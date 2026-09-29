package io.mark.hditemicons.hd;

import io.mark.hditemicons.HdItemIconsConfig;
import io.mark.hditemicons.IconCacheStorage;
import io.mark.hditemicons.IconQuality;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.WidgetNode;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.util.Filepath;

import static io.mark.hditemicons.hd.ItemIconRasterizer.ICON_HEIGHT;
import static io.mark.hditemicons.hd.ItemIconRasterizer.ICON_WIDTH;

/**
 * Replaces the game's 36x32 item icons with antialiased software renders of the item models,
 * painted directly into the frame buffer over each item widget. The item widget itself is
 * never touched, so clicks, drags and menu actions all keep working exactly as before; only
 * the pixels the game already drew for that slot are repainted.
 * <p>
 * The game doesn't expose an item's icon camera framing through the public API, so for every
 * distinct (item, quantity, border) combination we first ask the client to draw its own icon,
 * and {@link ItemIconRasterizer} numerically fits our own camera to match that silhouette
 * before trusting the render. Rendering itself happens on a background thread pool; only the
 * final compositing into the live frame buffer runs synchronously during overlay drawing.
 */
@Slf4j
@Singleton
public class HdItemIcons extends WidgetItemOverlay {
	private static final int MAX_CACHED_ICONS = 2048;
	private static final int MAX_CACHED_REFERENCES = 2048;
	private static final int MAX_CACHED_STACK_MODELS = 512;
	private static final int MAX_NEW_RENDERS_PER_FRAME = 16;
	private static final int INPAINT_PASSES = 3;
	private static final float DRAGGED_OPACITY = 128 / 256f;
	private static final float MIN_SHAPE_COVERAGE = .9f;

	// We paint a small margin around the icon too, since our render can spill slightly past
	// the game's own tighter bounding box.
	private static final int MARGIN = 2;
	private static final int PATCH_WIDTH = ICON_WIDTH + 2 * MARGIN;
	private static final int PATCH_HEIGHT = ICON_HEIGHT + 2 * MARGIN;
	private static final int PATCH_SIZE = PATCH_WIDTH * PATCH_HEIGHT;

	private static final byte FLAG_ITEM = 0x1;
	private static final byte FLAG_SHADOW = 0x2;
	private static final byte FLAG_STACK_TEXT = 0x4;
	private static final byte FLAG_OUTLINE = 0x8;

	private static final int[] NEIGHBOR_DX = {-1, 1, 0, 0};
	private static final int[] NEIGHBOR_DY = {0, 0, -1, 1};

	/**
	 * A queued or finished render. A shared {@link #PENDING} instance stands in for "still
	 * being worked on" so callers don't need a separate lookup to check.
	 */
	private static final class RenderedIcon {
		volatile boolean failed;
		volatile boolean uncached;
		volatile boolean[] outlineRing;
		volatile int[] pixels;
		int[] selectedPixels;
		boolean[] selectedOutlineRing;
	}

	/**
	 * A fingerprint of what the game itself drew for one (item, quantity, border) combination.
	 * Used both as the cache key for our rendered replacement and to classify every pixel of
	 * the patch around the icon as part of the item's silhouette, its drop shadow, or
	 * stack-count text, so compositing knows what it's allowed to touch.
	 */
	private static final class ReferenceIcon {
		final byte[] patchFlags = new byte[PATCH_SIZE];
		final int[] plainPixels;
		final long fingerprint;
		int resolvedModelItemId = -1;

		ReferenceIcon(int itemId, int borderWidth, int quality, int[] plainPixels, int[] pixelsWithCount) {
			this.plainPixels = plainPixels;
			this.fingerprint = fingerprintOf(itemId, borderWidth, quality, plainPixels);

			for (int y = 0; y < ICON_HEIGHT; y++) {
				for (int x = 0; x < ICON_WIDTH; x++) {
					int flat = y * ICON_WIDTH + x;
					int patch = patchIndex(x, y);
					if (plainPixels[flat] != 0)
						patchFlags[patch] |= FLAG_ITEM;
					if (pixelsWithCount[flat] != plainPixels[flat])
						patchFlags[patch] |= FLAG_STACK_TEXT;
				}
			}
			for (int y = 0; y < ICON_HEIGHT; y++) {
				for (int x = 0; x < ICON_WIDTH; x++) {
					int patch = patchIndex(x, y);
					boolean isItem = (patchFlags[patch] & FLAG_ITEM) != 0;
					boolean litFromUpperLeft = (patchFlags[patchIndex(x - 1, y - 1)] & FLAG_ITEM) != 0;
					if (!isItem && litFromUpperLeft)
						patchFlags[patch] |= FLAG_SHADOW;
					for (int n = 0; n < NEIGHBOR_DX.length; n++)
						if (!isItem && (patchFlags[patchIndex(x + NEIGHBOR_DX[n], y + NEIGHBOR_DY[n])] & FLAG_ITEM) != 0)
							patchFlags[patch] |= FLAG_OUTLINE;
				}
			}
		}

		boolean has(int patch, byte flag) {
			return (patchFlags[patch] & flag) != 0;
		}

		private static long fingerprintOf(int itemId, int borderWidth, int quality, int[] pixels) {
			long h = 0xCBF29CE484222325L; // FNV-1a
			h = mix(h, itemId);
			h = mix(h, borderWidth);
			h = mix(h, quality);
			for (int p : pixels)
				h = mix(h, p);
			return h;
		}

		private static long mix(long hash, int value) {
			for (int shift = 0; shift < 32; shift += 8)
				hash = (hash ^ (value >>> shift & 0xFF)) * 0x100000001B3L;
			return hash;
		}
	}

	private static int patchIndex(int x, int y) {
		return (y + MARGIN) * PATCH_WIDTH + (x + MARGIN);
	}

	/**
	 * Everything needed to rasterize one drawn layer of an icon (a plain item, or one half of
	 * a noted item's [paper, small item] pair).
	 */
	private static final class ModelLayer {
		final ItemIconRasterizer.IconModel model;
		final int pitchJau, yawJau, rollJau;
		final int[] referencePixels;
		final int borderWidth;

		ModelLayer(ItemIconRasterizer.IconModel model, int pitchJau, int yawJau, int rollJau, int[] referencePixels, int borderWidth) {
			this.model = model;
			this.pitchJau = pitchJau;
			this.yawJau = yawJau;
			this.rollJau = rollJau;
			this.referencePixels = referencePixels;
			this.borderWidth = borderWidth;
		}
	}

	/**
	 * An item's icon put on screen this frame, and what it's composited from.
	 */
	private static final class PlacedIcon {
		final Rectangle bounds, patchArea;
		final ReferenceIcon reference;
		final int[] pixels;
		final boolean[] outlineRing;
		@Nullable
		final Slot slot;
		final int[] composed;
		int[] background;
		int[] overlays;

		PlacedIcon(Rectangle bounds, Rectangle patchArea, ReferenceIcon reference, int[] pixels, boolean[] outlineRing,
			@Nullable Slot slot, int[] composed) {
			this.bounds = bounds;
			this.patchArea = patchArea;
			this.reference = reference;
			this.pixels = pixels;
			this.outlineRing = outlineRing;
			this.slot = slot;
			this.composed = composed;
		}
	}

	/**
	 * What's behind an item and what other overlays draw over it rarely change, so they're only worked
	 * out again when they do.
	 */
	private static final class Slot {
		final int[] behind = new int[PATCH_SIZE];
		final int[] cut = new int[PATCH_SIZE];
		final int[] composed = new int[PATCH_SIZE];
		final int[] overlays = new int[PATCH_SIZE];
		final int[] drawn = new int[PATCH_SIZE];
		ReferenceIcon reference;
		int[] pixels;
		Rectangle patchArea;
		float opacity;
		boolean overlaid;
		int lastUsed;

		boolean holds(PlacedIcon placed, float opacity) {
			return reference == placed.reference && pixels == placed.pixels && placed.patchArea.equals(patchArea) && this.opacity == opacity;
		}

		void keep(PlacedIcon placed, float opacity) {
			reference = placed.reference;
			pixels = placed.pixels;
			patchArea = placed.patchArea;
			this.opacity = opacity;
			overlaid = false;
			for (int y = patchArea.y; y < patchArea.y + patchArea.height; y++) {
				for (int x = patchArea.x; x < patchArea.x + patchArea.width; x++) {
					int patch = patchIndex(x - placed.bounds.x, y - placed.bounds.y);
					cut[patch] = reference.has(patch, FLAG_STACK_TEXT) ? behind[patch] : 0;
				}
			}
		}
	}

	/**
	 * Takes what other overlays drew over the cut out items. Fills and outlines that follow the game's
	 * icon, like Inventory Tags', are redrawn to fit ours, with fills covering the shadow too. Everything
	 * else stays where it was drawn.
	 */
	private final class OverlayCapture extends WidgetItemOverlay {
		OverlayCapture() {
			showOnInventory();
			showOnBank();
			showOnEquipment();
			// After the other item overlays
			setPriority(PRIORITY_HIGHEST + 1);
		}

		void showOnInterface(int groupId) {
			drawAfterInterface(groupId);
		}

		@Override
		public Dimension render(Graphics2D graphics) {
			BufferProvider frame = client.getBufferProvider();
			int[] framePixels = frame.getPixels();
			int frameWidth = frame.getWidth();
			for (PlacedIcon placed : cutItems)
				captureOverlays(framePixels, frameWidth, placed);
			for (PlacedIcon dragged : draggedCuts) {
				captureOverlays(framePixels, frameWidth, dragged);
				writePatch(framePixels, frameWidth, dragged, dragged.background);
			}
			for (PlacedIcon placed : cutItems)
				drawWithOverlays(framePixels, frameWidth, placed);
			cutItems.clear();
			draggedCuts.clear();
			// What the game draws the dragged item over, now that the items under it are done
			for (PlacedIcon dragged : draggedIcons)
				readPatch(framePixels, frameWidth, dragged, dragged.background);
			return null;
		}

		@Override
		public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
		}
	}

	/**
	 * Paints the dragged item over the one the game draws after the rest of the interface.
	 */
	private final class DraggedItemPainter extends Overlay {
		DraggedItemPainter() {
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ABOVE_WIDGETS);
			setPriority(PRIORITY_LOW - 1);
		}

		@Override
		public Dimension render(Graphics2D graphics) {
			paintDraggedItems();
			return null;
		}
	}

	private static final int[] EMPTY_PATCH = new int[PATCH_SIZE];
	private static final RenderedIcon PENDING = new RenderedIcon();
	private static final ReferenceIcon UNRESOLVED =
		new ReferenceIcon(-1, 0, 0, new int[ICON_WIDTH * ICON_HEIGHT], new int[ICON_WIDTH * ICON_HEIGHT]);

	private final Client client;
	private final EventBus eventBus;
	private final OverlayManager overlayManager;
	private final ClientThread clientThread;
	private final HdItemIconsConfig config;
	private final OverlayCapture overlayCapture = new OverlayCapture();
	private final DraggedItemPainter draggedItemPainter = new DraggedItemPainter();

	private ExecutorService renderExecutor;
	private boolean active;
	private double lastKnownBrightness = Double.NaN;
	@Nullable
	private IconQuality lastKnownQuality;
	@Nullable
	private Filepath iconCacheDirectory;
	@Nullable
	private ItemIconCache iconCache;

	private final Map<Long, RenderedIcon> renderedIcons = new LinkedHashMap<>(MAX_CACHED_ICONS, .75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, RenderedIcon> eldest) {
			return size() > MAX_CACHED_ICONS;
		}
	};
	private final Map<Long, ReferenceIcon> referenceIcons = new LinkedHashMap<>(MAX_CACHED_REFERENCES, .75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, ReferenceIcon> eldest) {
			return size() > MAX_CACHED_REFERENCES;
		}
	};
	private final Map<Long, Integer> resolvedStackModels = new LinkedHashMap<>(MAX_CACHED_STACK_MODELS, .75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
			return size() > MAX_CACHED_STACK_MODELS;
		}
	};
	// Not access-ordered: an in-progress search must keep its resume point until it either
	// finishes or is evicted for staying idle the longest, not for being actively read.
	private final Map<Long, Integer> stackModelSearchProgress = new LinkedHashMap<>(MAX_CACHED_STACK_MODELS, .75f, false) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
			return size() > MAX_CACHED_STACK_MODELS;
		}
	};
	private final Map<Integer, Item[]> watchedContainers = new HashMap<>();
	// Containers whose items have all already been queued for a render (or found ineligible),
	// so the prefetch pass can skip re-scanning them every frame - this matters a lot for the
	// bank, which can hold 800+ slots.
	private final Set<Integer> settledContainers = new HashSet<>();
	private final Set<Integer> hookedInterfaces = new HashSet<>();

	private int rendersStartedThisFrame;
	private int frameCount;
	private final Set<Rectangle> paintedThisFrame = new HashSet<>();
	private final Map<Rectangle, Slot> slots = new HashMap<>();
	private final List<WidgetItem> draggedItems = new ArrayList<>();
	private final List<PlacedIcon> draggedIcons = new ArrayList<>();
	private final List<PlacedIcon> cutItems = new ArrayList<>();
	private final List<PlacedIcon> draggedCuts = new ArrayList<>();
	private final List<int[]> patchBuffers = new ArrayList<>();
	private int patchBuffersUsed;
	private final int[] patchColor = new int[PATCH_SIZE];
	private final boolean[] patchResolved = new boolean[PATCH_SIZE];
	private final boolean[] patchResolvedScratch = new boolean[PATCH_SIZE];

	private int[] cachedPalette;
	private double cachedPaletteBrightness;

	@Inject
	public HdItemIcons(Client client, EventBus eventBus, OverlayManager overlayManager, ClientThread clientThread, HdItemIconsConfig config) {
		this.client = client;
		this.eventBus = eventBus;
		this.overlayManager = overlayManager;
		this.clientThread = clientThread;
		this.config = config;
		showOnInventory();
		showOnBank();
		showOnEquipment();
		// Ahead of other item overlays, so they draw on top of our replacement icon
		setPriority(PRIORITY_LOW - 1);
	}

	/**
	 * @param dataDirectory the plugin's data directory. Only used if the disk icon cache is
	 *                      enabled in config.
	 */
	public void startUp(Filepath dataDirectory) {
		renderExecutor = Executors.newFixedThreadPool(config.renderThreadCount(), runnable -> {
			Thread thread = new Thread(runnable, "hd-item-icon-renderer");
			thread.setDaemon(true);
			return thread;
		});

		iconCacheDirectory = null;
		if (config.iconCacheStorage() == IconCacheStorage.DISK) {
			try {
				if (!dataDirectory.isDirectory())
					dataDirectory.createDirectories();
				iconCacheDirectory = dataDirectory;
				renderExecutor.execute(() -> ItemIconCache.removeUnused(dataDirectory));
			} catch (IOException e) {
				log.debug("Couldn't create the disk icon cache directory {}; falling back to memory only", dataDirectory, e);
			}
		}

		active = true;
		eventBus.register(this);
		clientThread.invoke(() -> {
			// clientThread.invoke() just queues this for the next client tick if we're not
			// already on it - it isn't tied to our lifecycle. If the plugin is toggled off
			// again before this runs, shutDown() can finish first; without this guard we'd
			// re-add the overlay after shutdown already tore everything down, leaking it
			// with no way left to remove it.
			if (!active)
				return;
			hookInterface(client.getTopLevelInterfaceId());
			for (WidgetNode node : client.getComponentTable())
				hookInterface(node.getId());
			overlayManager.add(this);
			overlayManager.add(overlayCapture);
			overlayManager.add(draggedItemPainter);
		});
	}

	public void shutDown() {
		if (!active)
			return;
		active = false;
		eventBus.unregister(this);
		overlayManager.remove(this);
		overlayManager.remove(overlayCapture);
		overlayManager.remove(draggedItemPainter);
		renderExecutor.shutdownNow();
		renderExecutor = null;

		iconCacheDirectory = null;
		iconCache = null;
		renderedIcons.clear();
		referenceIcons.clear();
		resolvedStackModels.clear();
		stackModelSearchProgress.clear();
		watchedContainers.clear();
		settledContainers.clear();
		hookedInterfaces.clear();
		paintedThisFrame.clear();
		slots.clear();
		draggedItems.clear();
		draggedIcons.clear();
		cutItems.clear();
		draggedCuts.clear();
		patchBuffers.clear();
		patchBuffersUsed = 0;
		cachedPalette = null;
		cachedPaletteBrightness = 0;
		lastKnownBrightness = Double.NaN;
		lastKnownQuality = null;
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event) {
		frameCount++;
		slots.values().removeIf(slot -> slot.lastUsed < frameCount - 1);
		rendersStartedThisFrame = 0;
		paintedThisFrame.clear();
		draggedItems.clear();
		draggedIcons.clear();
		cutItems.clear();
		draggedCuts.clear();
		patchBuffersUsed = 0;

		double brightness = client.getTextureProvider().getBrightness();
		IconQuality quality = config.iconQuality();
		if (brightness != lastKnownBrightness || quality != lastKnownQuality) {
			// Both the game's icons and ours depend on the brightness setting, and our own
			// renders depend on the configured supersampling quality
			lastKnownBrightness = brightness;
			lastKnownQuality = quality;
			referenceIcons.clear();
			renderedIcons.clear();
			settledContainers.clear();
			iconCache = iconCacheDirectory == null ? null : new ItemIconCache(iconCacheDirectory, quality, brightness, PATCH_SIZE);
			if (iconCache != null)
				renderExecutor.execute(iconCache::markUsed);
		}

		// Every frame, so icons are prepared as soon as the game sends the items, not once an interface shows them
		prefetchQueuedContainers();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (hookInterface(event.getGroupId())) {
			// Re-add so the overlay manager notices the new draw hook
			overlayManager.remove(this);
			overlayManager.remove(overlayCapture);
			overlayManager.add(this);
			overlayManager.add(overlayCapture);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		watchedContainers.put(event.getContainerId(), event.getItemContainer().getItems());
		settledContainers.remove(event.getContainerId());
	}

	private boolean hookInterface(int groupId) {
		if (groupId == -1 || !hookedInterfaces.add(groupId))
			return false;
		drawAfterInterface(groupId);
		overlayCapture.showOnInterface(groupId);
		return true;
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (!active)
			return null;
		super.render(graphics);
		cutOutDraggedItems();
		return null;
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
		// The game draws the dragged item after the interface, so it's replaced once the interface is done
		if (widgetItem.getWidget() == client.getDraggedWidget()) {
			draggedItems.add(widgetItem);
			return;
		}

		PlacedIcon placed = place(widgetItem);
		if (placed == null)
			return;

		BufferProvider frame = client.getBufferProvider();
		int[] framePixels = frame.getPixels();
		float opacity = (256 - widgetItem.getWidget().getOpacity()) / 256f;
		Slot slot = placed.slot;
		if (!slot.holds(placed, opacity) || !samePixels(framePixels, frame.getWidth(), placed, slot.behind)) {
			readPatch(framePixels, frame.getWidth(), placed, slot.behind);
			int shadowColor = inpaintBackground(framePixels, frame.getWidth(), placed.bounds, placed.patchArea, placed.reference);
			composeIcon(placed, shadowColor, opacity);
			slot.keep(placed, opacity);
		}
		if (client.isGpu()) {
			// Other overlays draw onto a transparent patch, so the ones that follow the game's icon can be fitted to ours
			writePatch(framePixels, frame.getWidth(), placed, EMPTY_PATCH);
			cutItems.add(placed);
		} else {
			writePatch(framePixels, frame.getWidth(), placed, placed.composed);
		}
	}

	@Nullable
	private PlacedIcon place(WidgetItem widgetItem) {
		Widget widget = widgetItem.getWidget();
		Rectangle bounds = widgetItem.getCanvasBounds();
		if (bounds.width != ICON_WIDTH || bounds.height != ICON_HEIGHT)
			return null;

		int itemId = widgetItem.getId();
		int quantity = widgetItem.getQuantity();
		int quantityMode = widget.getItemQuantityMode();
		int borderWidth = widget.getBorderType();

		// Looked up once and threaded through, rather than resolving the reference icon twice
		// (once implicitly inside icon resolution, once again for compositing).
		ReferenceIcon reference = lookupReferenceIcon(itemId, quantity, quantityMode, borderWidth, true);
		if (reference == null || reference == UNRESOLVED)
			return null;
		// Selected items use the normal icon, with the game's white border around it
		boolean selected = borderWidth == 2;
		ReferenceIcon normal = selected ? lookupReferenceIcon(itemId, quantity, quantityMode, 1, true) : reference;
		if (normal == null || normal == UNRESOLVED)
			return null;
		RenderedIcon icon = iconFor(normal, itemId, quantity, Math.min(borderWidth, 1), true);
		if (icon == null || icon == PENDING || icon.pixels == null)
			return null;

		Rectangle onScreen = bounds.intersection(widget.getParent().getBounds());
		// Some interfaces report their items twice
		if (onScreen.isEmpty() || !paintedThisFrame.add(bounds))
			return null;

		BufferProvider frame = client.getBufferProvider();
		if (frame.getPixels() == null)
			return null;

		// Partly visible items stay clipped like the interface clips them
		Rectangle patchArea = !onScreen.equals(bounds) ? onScreen :
			new Rectangle(bounds.x - MARGIN, bounds.y - MARGIN, PATCH_WIDTH, PATCH_HEIGHT)
				.intersection(new Rectangle(frame.getWidth(), frame.getHeight()));
		if (patchArea.isEmpty())
			return null;

		int[] pixels = icon.pixels;
		boolean[] outlineRing = icon.outlineRing;
		if (selected) {
			if (icon.selectedPixels == null) {
				icon.selectedPixels = ItemIconRasterizer.withSelectionBorder(icon.pixels, PATCH_WIDTH, PATCH_HEIGHT);
				icon.selectedOutlineRing = ItemIconRasterizer.outlineRing(icon.selectedPixels, PATCH_WIDTH, PATCH_HEIGHT);
			}
			pixels = icon.selectedPixels;
			outlineRing = icon.selectedOutlineRing;
		}

		// Dragged items move, so they're worked out every frame
		if (widget == client.getDraggedWidget())
			return new PlacedIcon(bounds, patchArea, reference, pixels, outlineRing, null, nextPatchBuffer());
		Slot slot = slots.computeIfAbsent(bounds, b -> new Slot());
		slot.lastUsed = frameCount;
		return new PlacedIcon(bounds, patchArea, reference, pixels, outlineRing, slot, slot.composed);
	}

	private int[] nextPatchBuffer() {
		if (patchBuffersUsed == patchBuffers.size())
			patchBuffers.add(new int[PATCH_SIZE]);
		return patchBuffers.get(patchBuffersUsed++);
	}

	private void cutOutDraggedItems() {
		BufferProvider frame = client.getBufferProvider();
		for (WidgetItem widgetItem : draggedItems) {
			PlacedIcon placed = place(widgetItem);
			if (placed == null)
				continue;
			placed.background = nextPatchBuffer();
			if (client.isGpu()) {
				readPatch(frame.getPixels(), frame.getWidth(), placed, placed.background);
				writePatch(frame.getPixels(), frame.getWidth(), placed, EMPTY_PATCH);
				draggedCuts.add(placed);
			}
			draggedIcons.add(placed);
		}
		draggedItems.clear();
	}

	private void paintDraggedItems() {
		if (draggedIcons.isEmpty())
			return;

		BufferProvider frame = client.getBufferProvider();
		int[] framePixels = frame.getPixels();
		for (PlacedIcon dragged : draggedIcons) {
			int shadowColor = draggedShadowColor(framePixels, frame.getWidth(), dragged);
			System.arraycopy(dragged.background, 0, patchColor, 0, PATCH_SIZE);
			composeIcon(dragged, shadowColor, DRAGGED_OPACITY);
			drawWithOverlays(framePixels, frame.getWidth(), dragged);
		}
		draggedIcons.clear();
	}

	private static int draggedShadowColor(int[] framePixels, int frameWidth, PlacedIcon dragged) {
		for (int y = dragged.patchArea.y; y < dragged.patchArea.y + dragged.patchArea.height; y++) {
			for (int x = dragged.patchArea.x; x < dragged.patchArea.x + dragged.patchArea.width; x++) {
				int patch = patchIndex(x - dragged.bounds.x, y - dragged.bounds.y);
				if (dragged.reference.has(patch, FLAG_SHADOW) && !dragged.reference.has(patch, FLAG_STACK_TEXT))
					return framePixels[y * frameWidth + x];
			}
		}
		return 0;
	}

	@Nullable
	private RenderedIcon resolveIcon(int itemId, int quantity, int quantityMode, int borderWidth, boolean visibleNow) {
		ReferenceIcon reference = lookupReferenceIcon(itemId, quantity, quantityMode, borderWidth, visibleNow);
		if (reference == UNRESOLVED)
			return PENDING;
		if (reference == null)
			return null;
		return iconFor(reference, itemId, quantity, borderWidth, visibleNow);
	}

	@Nullable
	private RenderedIcon iconFor(ReferenceIcon reference, int itemId, int quantity, int borderWidth, boolean visibleNow) {
		RenderedIcon icon = renderedIcons.get(reference.fingerprint);
		if (icon == null) {
			if (!consumeRenderBudget(visibleNow))
				return PENDING;

			icon = new RenderedIcon();
			renderedIcons.put(reference.fingerprint, icon);
			if (iconCache != null) {
				loadIcon(icon, iconCache, reference.fingerprint);
				return icon;
			}
			icon.uncached = true;
		}
		if (icon.uncached) {
			if (!consumeRenderBudget(visibleNow))
				return PENDING;

			int modelItemId = resolveModelItemId(itemId, quantity, borderWidth, reference);
			if (modelItemId == -1)
				return null;
			icon.uncached = false;
			if (modelItemId == -2) {
				icon.failed = true;
				ItemIconCache cache = iconCache;
				if (cache != null)
					renderExecutor.execute(() -> cache.save(reference.fingerprint, null));
			} else {
				beginRenderingIcon(icon, modelItemId, borderWidth, reference.fingerprint);
			}
		}
		return icon.failed ? null : icon;
	}

	private void loadIcon(RenderedIcon icon, ItemIconCache cache, long fingerprint) {
		renderExecutor.execute(() -> {
			int[] kept = cache.load(fingerprint);
			if (kept == null) {
				icon.uncached = true;
			} else if (kept.length == 0) {
				icon.failed = true;
			} else {
				icon.outlineRing = ItemIconRasterizer.outlineRing(kept, PATCH_WIDTH, PATCH_HEIGHT);
				icon.pixels = kept;
			}
		});
	}

	private boolean consumeRenderBudget(boolean visibleNow) {
		if (!visibleNow && rendersStartedThisFrame >= MAX_NEW_RENDERS_PER_FRAME)
			return false;
		rendersStartedThisFrame++;
		return true;
	}

	/**
	 * Starts a render for every not-yet-settled container's items ahead of time, so icons are
	 * usually already rendered by the time the player actually looks at them (e.g. scrolling
	 * the bank). Bounded by the same per-frame budget as on-screen items.
	 */
	private void prefetchQueuedContainers() {
		for (Map.Entry<Integer, Item[]> entry : watchedContainers.entrySet()) {
			int containerId = entry.getKey();
			if (settledContainers.contains(containerId))
				continue;

			boolean settled = true;
			for (Item item : entry.getValue()) {
				if (renderedIcons.size() >= MAX_CACHED_ICONS)
					return;
				if (item.getId() != -1 && resolveIcon(item.getId(), item.getQuantity(), ItemQuantityMode.NEVER, 1, false) == PENDING) {
					settled = false;
					break;
				}
			}
			if (!settled)
				return;
			settledContainers.add(containerId);
		}
	}

	@Nullable
	private ReferenceIcon lookupReferenceIcon(int itemId, int quantity, int quantityMode, int borderWidth, boolean visibleNow) {
		long key = (long) quantity << 24 | (long) itemId << 4 | (long) quantityMode << 2 | borderWidth;
		if (referenceIcons.containsKey(key))
			return referenceIcons.get(key);
		if (!consumeRenderBudget(visibleNow))
			return UNRESOLVED;

		ReferenceIcon reference = null;
		int[] plain = fetchGamePixels(itemId, quantity, borderWidth, ItemQuantityMode.NEVER, false);
		int[] withCount = plain == null || quantityMode == ItemQuantityMode.NEVER ? plain :
			fetchGamePixels(itemId, quantity, borderWidth, quantityMode, false);
		if (withCount != null)
			reference = new ReferenceIcon(itemId, borderWidth, config.iconQuality().ordinal(), plain, withCount);
		referenceIcons.put(key, reference);
		return reference;
	}

	// Stacks like coins and arrows show the model of an unnamed item, which the API doesn't expose
	private int resolveModelItemId(int itemId, int quantity, int borderWidth, ReferenceIcon reference) {
		if (reference.resolvedModelItemId != -1)
			return reference.resolvedModelItemId;

		ItemComposition definition = client.getItemDefinition(itemId);
		if (definition.getPlaceholderTemplateId() != -1) {
			reference.resolvedModelItemId = definition.getPlaceholderId();
		} else if (quantity == 1 || Arrays.equals(reference.plainPixels, fetchGamePixels(itemId, 1, borderWidth, ItemQuantityMode.NEVER, false))) {
			reference.resolvedModelItemId = itemId;
		} else {
			return searchForStackModelItem(itemId, borderWidth, reference.plainPixels);
		}
		return reference.resolvedModelItemId;
	}

	/**
	 * Some stackable items (coins, arrows, ...) share a model belonging to a different,
	 * unnamed item id that isn't otherwise discoverable. We search outward from the item's own
	 * id for one whose plain icon pixels match, a few candidates per frame, remembering how
	 * far we've searched so a large search resumes instead of restarting.
	 */
	private int searchForStackModelItem(int itemId, int borderWidth, int[] targetPixels) {
		long key = (long) Arrays.hashCode(targetPixels) << 32 | (long) itemId << 2 | borderWidth;
		Integer resolved = resolvedStackModels.get(key);
		if (resolved != null)
			return resolved == -1 ? -2 : resolved;

		int totalItems = client.getItemCount();
		int distance = stackModelSearchProgress.getOrDefault(key, 0);
		for (int checked = 0; checked < 256 && rendersStartedThisFrame < MAX_NEW_RENDERS_PER_FRAME; checked++) {
			distance++;
			if (itemId + distance / 2 >= totalItems && itemId - distance / 2 < 0) {
				resolvedStackModels.put(key, -1);
				stackModelSearchProgress.remove(key);
				return -2;
			}
			int candidate = itemId + (distance % 2 == 1 ? distance / 2 + 1 : -distance / 2);
			if (candidate < 0 || candidate >= totalItems || !"null".equals(client.getItemDefinition(candidate).getName()))
				continue;
			rendersStartedThisFrame++;
			if (Arrays.equals(targetPixels, fetchGamePixels(candidate, 1, borderWidth, ItemQuantityMode.NEVER, false))) {
				resolvedStackModels.put(key, candidate);
				stackModelSearchProgress.remove(key);
				return candidate;
			}
		}
		stackModelSearchProgress.put(key, distance);
		return -1;
	}

	@Nullable
	private int[] fetchGamePixels(int itemId, int quantity, int borderWidth, int quantityMode, boolean noted) {
		var sprite = client.createItemSprite(itemId, quantity, borderWidth, 0, quantityMode, noted, Constants.CLIENT_DEFAULT_ZOOM);
		return sprite == null ? null : sprite.getPixels();
	}

	private void beginRenderingIcon(RenderedIcon icon, int itemId, int borderWidth, long fingerprint) {
		ItemComposition item = client.getItemDefinition(itemId);
		// Notes are drawn as the note's paper with a small copy of the real item on top
		ModelLayer[] layers = item.getNote() == -1
			? new ModelLayer[]{buildModelLayer(itemId, 1, false, borderWidth)}
			: new ModelLayer[]{buildModelLayer(item.getNote(), 1, false, borderWidth), buildModelLayer(item.getLinkedNoteId(), 10, true, 1)};
		for (ModelLayer layer : layers) {
			if (layer == null) {
				icon.failed = true;
				return;
			}
		}

		double brightness = lastKnownBrightness;
		int supersample = config.iconQuality().getSupersample();
		ItemIconCache cache = iconCache;
		renderExecutor.execute(() -> {
			try {
				int[] palette = paletteFor(brightness);
				int[] combined = null;
				for (ModelLayer layer : layers) {
					ItemIconRasterizer rasterizer = new ItemIconRasterizer(layer.model, layer.pitchJau, layer.yawJau, layer.rollJau, supersample);
					if (!rasterizer.fitToReferenceSilhouette(layer.referencePixels, palette)) {
						icon.failed = true;
						if (cache != null)
							cache.save(fingerprint, null);
						return;
					}
					int[] rendered = rasterizer.render(MARGIN, layer.borderWidth > 0, palette);
					combined = combined == null ? rendered : ItemIconRasterizer.compositeOver(rendered, combined);
				}
				icon.outlineRing = ItemIconRasterizer.outlineRing(combined, PATCH_WIDTH, PATCH_HEIGHT);
				icon.pixels = combined;

				if (cache != null)
					cache.save(fingerprint, combined);
			} catch (Throwable ex) {
				log.debug("Unable to render an HD icon for item {}:", itemId, ex);
				icon.failed = true;
			}
		});
	}

	@Nullable
	private ModelLayer buildModelLayer(int itemId, int quantity, boolean noted, int borderWidth) {
		ItemComposition item = client.getItemDefinition(itemId);
		ModelData data = client.loadModelData(item.getInventoryModel());
		if (data == null)
			return null;

		short[] colorFind = item.getColorToReplace();
		short[] colorReplace = item.getColorToReplaceWith();
		if (colorFind != null) {
			data = data.cloneColors();
			for (int i = 0; i < colorFind.length; i++)
				data.recolor(colorFind[i], colorReplace[i]);
		}

		short[] textureFind = item.getTextureToReplace();
		short[] textureReplace = item.getTextureToReplaceWith();
		if (textureFind != null) {
			data = data.cloneTextures();
			for (int i = 0; i < textureFind.length; i++)
				data.retexture(textureFind[i], textureReplace[i]);
		}

		// Lit the same way the game lights item models for its own icons
		Model litModel = data.light(item.getAmbient() + 64, item.getContrast() + 768, -50, -10, -50);
		ItemIconRasterizer.IconModel iconModel = ItemIconRasterizer.IconModel.capture(litModel, client.getTextureProvider());
		int[] referencePixels = fetchGamePixels(itemId, quantity, 0, ItemQuantityMode.NEVER, noted);
		if (iconModel == null || referencePixels == null)
			return null;
		return new ModelLayer(iconModel, item.getXan2d(), item.getYan2d(), item.getZan2d(), referencePixels, borderWidth);
	}

	private synchronized int[] paletteFor(double brightness) {
		if (cachedPalette == null || cachedPaletteBrightness != brightness) {
			cachedPalette = ItemIconRasterizer.buildPalette(brightness);
			cachedPaletteBrightness = brightness;
		}
		return cachedPalette;
	}

	/**
	 * Reads the frame pixels under {@code patchArea} into {@link #patchColor}, then replaces
	 * every pixel the game's icon covers with a plausible background inpainted from what
	 * surrounds it, so our own antialiased edges blend onto something sensible instead of the
	 * old icon. Returns the colour the game shaded its own drop shadow with, or 0 if the
	 * shadow pixels don't all share one colour (a busy background), in which case we skip
	 * drawing a shadow of our own.
	 */
	private int inpaintBackground(int[] framePixels, int frameWidth, Rectangle iconBounds, Rectangle patchArea, ReferenceIcon reference) {
		Arrays.fill(patchColor, 0);
		Arrays.fill(patchResolved, false);
		for (int y = patchArea.y; y < patchArea.y + patchArea.height; y++) {
			for (int x = patchArea.x; x < patchArea.x + patchArea.width; x++) {
				int patch = patchIndex(x - iconBounds.x, y - iconBounds.y);
				patchColor[patch] = framePixels[y * frameWidth + x];
				patchResolved[patch] = !reference.has(patch, FLAG_ITEM) && !reference.has(patch, FLAG_STACK_TEXT);
			}
		}

		int shadowColor = detectUniformShadowColor(reference);
		if (shadowColor != 0) {
			for (int patch = 0; patch < PATCH_SIZE; patch++)
				if (reference.has(patch, FLAG_SHADOW))
					patchResolved[patch] = false;
		}

		// Most of the patch (everything outside the item's own silhouette) is already resolved
		// before diffusion starts, so a pass commonly finishes the job in one go - stop as soon
		// as a pass makes no further progress instead of always running the full budget.
		for (int pass = 0; pass < INPAINT_PASSES; pass++)
			if (!spreadKnownColorsOnce())
				break;

		return shadowColor;
	}

	/** Every pixel the game drop-shadowed should be the same colour; if not, treat as unknown. */
	private int detectUniformShadowColor(ReferenceIcon reference) {
		int shadowColor = 0;
		boolean seenAny = false;
		for (int patch = 0; patch < PATCH_SIZE; patch++) {
			if (!patchResolved[patch] || !reference.has(patch, FLAG_SHADOW))
				continue;
			if (!seenAny) {
				shadowColor = patchColor[patch];
				seenAny = true;
			} else if (patchColor[patch] != shadowColor) {
				return 0;
			}
		}
		return shadowColor;
	}

	/**
	 * One diffusion pass: every unresolved pixel adopts the average of its resolved
	 * neighbours. Returns whether anything was actually resolved this pass, so the caller can
	 * stop early once the patch has settled instead of always running the full pass budget.
	 */
	private boolean spreadKnownColorsOnce() {
		System.arraycopy(patchResolved, 0, patchResolvedScratch, 0, PATCH_SIZE);
		boolean resolvedAny = false;
		int patch = 0;
		for (int y = 0; y < PATCH_HEIGHT; y++) {
			for (int x = 0; x < PATCH_WIDTH; x++, patch++) {
				if (patchResolved[patch])
					continue;

				int count = 0, a = 0, r = 0, g = 0, b = 0;
				for (int n = 0; n < NEIGHBOR_DX.length; n++) {
					int nx = x + NEIGHBOR_DX[n];
					int ny = y + NEIGHBOR_DY[n];
					if (nx < 0 || nx >= PATCH_WIDTH || ny < 0 || ny >= PATCH_HEIGHT)
						continue;
					int neighborPatch = ny * PATCH_WIDTH + nx;
					if (!patchResolved[neighborPatch])
						continue;
					int neighborColor = patchColor[neighborPatch];
					a += neighborColor >>> 24;
					r += neighborColor >> 16 & 0xFF;
					g += neighborColor >> 8 & 0xFF;
					b += neighborColor & 0xFF;
					count++;
				}
				if (count > 0) {
					patchColor[patch] = (a / count) << 24 | (r / count) << 16 | (g / count) << 8 | (b / count);
					patchResolvedScratch[patch] = true;
					resolvedAny = true;
				}
			}
		}
		System.arraycopy(patchResolvedScratch, 0, patchResolved, 0, PATCH_SIZE);
		return resolvedAny;
	}

	private void composeIcon(PlacedIcon placed, int shadowColor, float opacity) {
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
			for (int x = placed.patchArea.x; x < placed.patchArea.x + placed.patchArea.width; x++) {
				int localX = x - placed.bounds.x;
				int localY = y - placed.bounds.y;
				int patch = patchIndex(localX, localY);
				int background = patchColor[patch];
				if (shadowColor != 0 && localX - 1 >= -MARGIN && localY - 1 >= -MARGIN) {
					float shade = alpha(placed.pixels[patchIndex(localX - 1, localY - 1)]);
					background = blend(shadowColor, shade, background, 1 - shade);
				}

				// With GPU the interface is premultiplied ARGB, so alpha is blended like the colours
				int iconArgb = placed.pixels[patch];
				placed.composed[patch] = blend(iconArgb, opacity, background, 1 - alpha(iconArgb) * opacity);
			}
		}
	}

	private static void writePatch(int[] framePixels, int frameWidth, PlacedIcon placed, int[] patchPixels) {
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
			for (int x = placed.patchArea.x; x < placed.patchArea.x + placed.patchArea.width; x++) {
				int patch = patchIndex(x - placed.bounds.x, y - placed.bounds.y);
				if (!placed.reference.has(patch, FLAG_STACK_TEXT))
					framePixels[y * frameWidth + x] = patchPixels[patch];
			}
		}
	}

	private static boolean samePixels(int[] framePixels, int frameWidth, PlacedIcon placed, int[] patchPixels) {
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
			int i = y * frameWidth + placed.patchArea.x;
			int patch = patchIndex(placed.patchArea.x - placed.bounds.x, y - placed.bounds.y);
			if (!Arrays.equals(framePixels, i, i + placed.patchArea.width, patchPixels, patch, patch + placed.patchArea.width))
				return false;
		}
		return true;
	}

	private static boolean samePatch(PlacedIcon placed, int[] a, int[] b) {
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
			int patch = patchIndex(placed.patchArea.x - placed.bounds.x, y - placed.bounds.y);
			if (!Arrays.equals(a, patch, patch + placed.patchArea.width, b, patch, patch + placed.patchArea.width))
				return false;
		}
		return true;
	}

	private static void readPatch(int[] framePixels, int frameWidth, PlacedIcon placed, int[] patchPixels) {
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++)
			for (int x = placed.patchArea.x; x < placed.patchArea.x + placed.patchArea.width; x++)
				patchPixels[patchIndex(x - placed.bounds.x, y - placed.bounds.y)] = framePixels[y * frameWidth + x];
	}

	private void captureOverlays(int[] framePixels, int frameWidth, PlacedIcon placed) {
		// Nothing was drawn over the item while its pixels are still as they were cut out
		if (placed.slot != null && samePixels(framePixels, frameWidth, placed, placed.slot.cut)) {
			placed.overlays = null;
			return;
		}
		placed.overlays = nextPatchBuffer();
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
			for (int x = placed.patchArea.x; x < placed.patchArea.x + placed.patchArea.width; x++) {
				int patch = patchIndex(x - placed.bounds.x, y - placed.bounds.y);
				if (placed.reference.has(patch, FLAG_STACK_TEXT)) {
					placed.overlays[patch] = 0;
				} else {
					placed.overlays[patch] = framePixels[y * frameWidth + x];
					framePixels[y * frameWidth + x] = 0;
				}
			}
		}
	}

	private void drawWithOverlays(int[] framePixels, int frameWidth, PlacedIcon placed) {
		Slot slot = placed.slot;
		if (placed.overlays == null) {
			writePatch(framePixels, frameWidth, placed, placed.composed);
			return;
		}
		if (slot != null && slot.overlaid && samePatch(placed, placed.overlays, slot.overlays)) {
			writePatch(framePixels, frameWidth, placed, slot.drawn);
			return;
		}

		ReferenceIcon reference = placed.reference;
		int fill = shapeColor(placed, FLAG_ITEM);
		int outline = shapeColor(placed, FLAG_OUTLINE);
		int[] drawn = slot != null ? slot.drawn : nextPatchBuffer();
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
			for (int x = placed.patchArea.x; x < placed.patchArea.x + placed.patchArea.width; x++) {
				int localX = x - placed.bounds.x;
				int localY = y - placed.bounds.y;
				int patch = patchIndex(localX, localY);
				if (reference.has(patch, FLAG_STACK_TEXT))
					continue;

				int overlay = placed.overlays[patch];
				boolean redrawn =
					fill != 0 && (reference.has(patch, FLAG_ITEM) && overlay == fill || reference.has(patch, FLAG_SHADOW)) ||
					outline != 0 && reference.has(patch, FLAG_OUTLINE) && overlay == outline;
				if (redrawn)
					overlay = 0;

				float item = alpha(placed.pixels[patch]);
				float shadow = localX - 1 >= -MARGIN && localY - 1 >= -MARGIN ? alpha(placed.pixels[patchIndex(localX - 1, localY - 1)]) : 0;
				float outlined = placed.outlineRing[patch] ? 1 - item : 0;
				int fitted = over(scale(fill, Math.max(item, shadow)), scale(outline, outlined));
				drawn[patch] = over(over(overlay, fitted), placed.composed[patch]);
			}
		}
		if (slot != null) {
			System.arraycopy(placed.overlays, 0, slot.overlays, 0, PATCH_SIZE);
			slot.overlaid = true;
		}
		writePatch(framePixels, frameWidth, placed, drawn);
	}

	private static int shapeColor(PlacedIcon placed, byte shape) {
		ReferenceIcon reference = placed.reference;
		// Boyer-Moore majority vote
		int color = 0, lead = 0, pixels = 0;
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
			for (int x = placed.patchArea.x; x < placed.patchArea.x + placed.patchArea.width; x++) {
				int patch = patchIndex(x - placed.bounds.x, y - placed.bounds.y);
				if (reference.has(patch, FLAG_STACK_TEXT) || !reference.has(patch, shape) || reference.has(patch, FLAG_SHADOW))
					continue;
				pixels++;
				if (lead == 0)
					color = placed.overlays[patch];
				lead += placed.overlays[patch] == color ? 1 : -1;
			}
		}
		if (color == 0)
			return 0;

		int covered = 0;
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
			for (int x = placed.patchArea.x; x < placed.patchArea.x + placed.patchArea.width; x++) {
				int patch = patchIndex(x - placed.bounds.x, y - placed.bounds.y);
				if (reference.has(patch, FLAG_STACK_TEXT) || placed.overlays[patch] != color || reference.has(patch, FLAG_SHADOW))
					continue;
				if (!reference.has(patch, shape))
					return 0;
				covered++;
			}
		}
		return covered >= MIN_SHAPE_COVERAGE * pixels ? color : 0;
	}

	private static float alpha(int argb) {
		return (argb >>> 24) / 255f;
	}

	private static int scale(int argb, float factor) {
		return blend(argb, factor, 0, 0);
	}

	private static int over(int top, int bottom) {
		return blend(top, 1, bottom, 1 - alpha(top));
	}

	private static int blend(int top, float topWeight, int bottom, float bottomWeight) {
		int result = 0;
		for (int shift = 0; shift < 32; shift += 8) {
			int channel = Math.round((top >>> shift & 0xFF) * topWeight + (bottom >>> shift & 0xFF) * bottomWeight);
			result |= Math.min(255, channel) << shift;
		}
		return result;
	}
}
