package io.mark.hditemicons.hd;

import io.mark.hditemicons.CustomRotation;
import io.mark.hditemicons.CustomRotationStorage;
import io.mark.hditemicons.HdItemIconsConfig;
import io.mark.hditemicons.IconCacheStorage;
import io.mark.hditemicons.IconQuality;
import io.mark.hditemicons.ItemRenderSheet;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
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
	private static final int[] KEPT_CONTAINERS = {InventoryID.INV, InventoryID.WORN};
	private static final float DRAGGED_OPACITY = 128 / 256f;
	private static final float MIN_SHAPE_COVERAGE = .9f;

	// We paint a small margin around the icon too, since our render can spill slightly past
	// the game's own tighter bounding box.
	private static final int MARGIN = 2;
	private static final int PATCH_WIDTH = ICON_WIDTH + 2 * MARGIN;
	private static final int PATCH_HEIGHT = ICON_HEIGHT + 2 * MARGIN;
	// A pass spreads one pixel, so this is the most it can take to cross the patch
	private static final int INPAINT_PASSES = PATCH_WIDTH + PATCH_HEIGHT;
	private static final int PATCH_SIZE = PATCH_WIDTH * PATCH_HEIGHT;

	private static final byte FLAG_ITEM = 0x1;
	private static final byte FLAG_SHADOW = 0x2;
	private static final byte FLAG_STACK_TEXT = 0x4;
	private static final byte FLAG_OUTLINE = 0x8;

	private static final int[] NEIGHBOR_DX = {-1, 1, 0, 0};
	private static final int[] NEIGHBOR_DY = {0, 0, -1, 1};

	private static final int[] INVENTORY_LIKE_INTERFACES = {
		InterfaceID.INVENTORY, InterfaceID.WORNITEMS, InterfaceID.EQUIPMENT_SIDE,
		InterfaceID.BANKSIDE, InterfaceID.SHARED_BANK_SIDE, InterfaceID.BANK_DEPOSITBOX,
		InterfaceID.SHOPSIDE,
	};
	// The layers holding their items, drawn before the game draws the dragged item over them
	private static final int[] INVENTORY_LIKE_ITEM_LAYERS = {
		InterfaceID.Inventory.ITEMS, InterfaceID.EquipmentSide.ITEMS, InterfaceID.Bankside.ITEMS,
		InterfaceID.SharedBankSide.ITEMS, InterfaceID.BankDepositbox.INVENTORY, InterfaceID.Shopside.ITEMS,
	};

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
		int itemId = -1;
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

		ReferenceIcon(int itemId, int borderWidth, int supersample, int[] plainPixels, int[] pixelsWithCount) {
			this.plainPixels = plainPixels;
			this.fingerprint = fingerprintOf(itemId, borderWidth, supersample, plainPixels);

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

		// Keyed on the quality's supersampling rather than the setting itself, so the key of an
		// already-rendered icon doesn't move when a quality is added to the setting
		private static long fingerprintOf(int itemId, int borderWidth, int supersample, int[] pixels) {
			long h = 0xCBF29CE484222325L; // FNV-1a
			h = mix(h, itemId);
			h = mix(h, borderWidth);
			h = mix(h, supersample);
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
		@Nullable
		final CustomRotation placement;

		ModelLayer(ItemIconRasterizer.IconModel model, int pitchJau, int yawJau, int rollJau, int[] referencePixels, int borderWidth, @Nullable CustomRotation placement) {
			this.model = model;
			this.pitchJau = pitchJau;
			this.yawJau = yawJau;
			this.rollJau = rollJau;
			this.referencePixels = referencePixels;
			this.borderWidth = borderWidth;
			this.placement = placement;
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
		int fill, outline;
		// Whether everything other overlays drew over it is drawn with it, so the dragged item can go over it all
		boolean takeAll;
		// What's drawn under the interface
		int[] drawn;
		// The game's own icon for the dragged item, wherever it was found drawn
		@Nullable
		int[] drawnByGame;

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
		boolean takeAll;
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
			for (int groupId : INVENTORY_LIKE_INTERFACES)
				drawAfterInterface(groupId);
			drawAfterLayer(InterfaceID.Bankmain.ITEMS);
			drawAfterLayer(InterfaceID.SharedBank.ITEMS);
			// After the other item overlays
			setPriority(PRIORITY_HIGHEST + 1);
		}

		@Override
		public Dimension render(Graphics2D graphics) {
			BufferProvider frame = client.getBufferProvider();
			int[] framePixels = frame.getPixels();
			int frameWidth = frame.getWidth();
			PlacedIcon draggedHere = draggedThisHook;
			draggedThisHook = null;
			for (PlacedIcon placed : cutItems) {
				// The game draws the dragged item over what it's dragged over
				placed.takeAll = draggedHere != null && draggedHere.patchArea.intersects(placed.patchArea);
				captureOverlays(framePixels, frameWidth, placed);
				placed.drawn = drawWithOverlays(placed);
				drawnUnderInterface.add(placed);
			}
			cutItems.clear();
			if (draggedHere == null)
				return null;
			if (client.isGpu()) {
				draggedHere.takeAll = true;
				captureOverlays(framePixels, frameWidth, draggedHere);
			} else {
				// What the game draws the dragged item over, once the items under it are done
				draggedHere.background = nextPatchBuffer();
				readPatch(framePixels, frameWidth, draggedHere, draggedHere.background);
			}
			return null;
		}

		@Override
		public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
		}
	}

	/**
	 * Takes the game's dragged item back out once it's drawn over the rest of the interface, and puts ours in its place.
	 */
	private final class DraggedItemPainter extends Overlay {
		DraggedItemPainter() {
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ABOVE_WIDGETS);
			setPriority(PRIORITY_LOW - 1);
		}

		@Override
		public Dimension render(Graphics2D graphics) {
			drawDraggedItem();
			return null;
		}
	}

	/**
	 * Keeps what's under the dragged item right after its layer, before the game draws it there.
	 */
	private final class DraggedItemBackdrop extends WidgetItemOverlay {
		DraggedItemBackdrop() {
			for (int layerId : INVENTORY_LIKE_ITEM_LAYERS)
				drawAfterLayer(layerId);
			// After the other overlays of the layer, which the game draws the dragged item over
			setPriority(PRIORITY_HIGHEST + 1);
		}

		@Override
		public Dimension render(Graphics2D graphics) {
			Widget draggedWidget = client.getDraggedWidget();
			if (draggedWidget == null)
				return null;
			for (WidgetItem widgetItem : overlayManager.getWidgetItems()) {
				if (widgetItem.getWidget() == draggedWidget)
					keepBackdrop(widgetItem.getCanvasBounds());
			}
			return null;
		}

		@Override
		public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
		}
	}

	/**
	 * With GPU the interface has transparency, so our icons are drawn under it once it's all drawn. Whatever is
	 * drawn over an item, like menus, other interfaces and overlays, then stays over it, wherever it came from.
	 */
	private final class UnderInterfacePainter extends Overlay {
		UnderInterfacePainter() {
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ALWAYS_ON_TOP);
			setPriority(PRIORITY_HIGHEST + 1);
		}

		@Override
		public Dimension render(Graphics2D graphics) {
			drawUnderInterface();
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
	private final CustomRotationStorage rotationStorage;
	private final ItemRenderSheet itemRenderSheet;
	private final OverlayCapture overlayCapture = new OverlayCapture();
	private final DraggedItemPainter draggedItemPainter = new DraggedItemPainter();
	private final DraggedItemBackdrop draggedItemBackdrop = new DraggedItemBackdrop();
	private final UnderInterfacePainter underInterfacePainter = new UnderInterfacePainter();

	private ExecutorService renderExecutor;
	private boolean active;
	private double lastKnownBrightness = Double.NaN;
	@Nullable
	private IconQuality lastKnownQuality;
	private boolean lastKnownCustomRotationsEnabled = true;
	private boolean lastKnownStretched;
	@Nullable
	private Filepath iconCacheDirectory;
	@Nullable
	private ItemIconCache iconCache;
	@Nullable
	private volatile RotationEditorDialog openRotationDialog;

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
	// Containers whose items all have their icons ready (or found ineligible),
	// so the prefetch pass can skip re-scanning them every frame - this matters a lot for the
	// bank, which can hold 800+ slots.
	private final Set<Integer> settledContainers = new HashSet<>();

	private int rendersStartedThisFrame;
	private int frameCount;
	private final Set<Rectangle> paintedThisFrame = new HashSet<>();
	private final Map<Rectangle, Slot> slots = new HashMap<>();
	private final List<PlacedIcon> cutItems = new ArrayList<>();
	private final List<PlacedIcon> drawnUnderInterface = new ArrayList<>();
	@Nullable
	private PlacedIcon dragged;
	@Nullable
	private PlacedIcon draggedThisHook;
	private int backdropFrame = -1;
	private final Rectangle backdropBounds = new Rectangle();
	private final int[] backdrop = new int[PATCH_SIZE];
	private final List<int[]> patchBuffers = new ArrayList<>();
	private int patchBuffersUsed;
	private final int[] patchColor = new int[PATCH_SIZE];
	private final boolean[] patchResolved = new boolean[PATCH_SIZE];
	private final boolean[] patchResolvedScratch = new boolean[PATCH_SIZE];

	private int[] cachedPalette;
	private double cachedPaletteBrightness;

	@Inject
	public HdItemIcons(Client client, EventBus eventBus, OverlayManager overlayManager, ClientThread clientThread,
						HdItemIconsConfig config, CustomRotationStorage rotationStorage, ItemRenderSheet itemRenderSheet) {
		this.client = client;
		this.eventBus = eventBus;
		this.overlayManager = overlayManager;
		this.clientThread = clientThread;
		this.config = config;
		this.rotationStorage = rotationStorage;
		this.itemRenderSheet = itemRenderSheet;
		for (int groupId : INVENTORY_LIKE_INTERFACES)
			drawAfterInterface(groupId);
		drawAfterLayer(InterfaceID.Bankmain.ITEMS);
		drawAfterLayer(InterfaceID.SharedBank.ITEMS);
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
		try {
			if (!dataDirectory.isDirectory())
				dataDirectory.createDirectories();
			rotationStorage.load(dataDirectory);
			if (config.iconCacheStorage() == IconCacheStorage.DISK) {
				iconCacheDirectory = dataDirectory;
				renderExecutor.execute(() -> ItemIconCache.removeUnused(dataDirectory));
			}
		} catch (IOException e) {
			log.debug("Couldn't create the plugin data directory {}; icon cache and custom rotations will be memory only", dataDirectory, e);
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
			// Also when turned on after logging in, before any of them changes
			for (int containerId : new int[]{InventoryID.INV, InventoryID.WORN, InventoryID.BANK}) {
				ItemContainer container = client.getItemContainer(containerId);
				if (container != null)
					watchedContainers.put(containerId, container.getItems());
			}
			overlayManager.add(this);
			overlayManager.add(overlayCapture);
			overlayManager.add(draggedItemPainter);
			overlayManager.add(draggedItemBackdrop);
			overlayManager.add(underInterfacePainter);
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
		overlayManager.remove(draggedItemBackdrop);
		overlayManager.remove(underInterfacePainter);
		renderExecutor.shutdownNow();
		renderExecutor = null;
		rotationStorage.reset();

		RotationEditorDialog dialog = openRotationDialog;
		openRotationDialog = null;
		if (dialog != null)
			SwingUtilities.invokeLater(dialog::dispose);

		iconCacheDirectory = null;
		iconCache = null;
		renderedIcons.clear();
		referenceIcons.clear();
		resolvedStackModels.clear();
		stackModelSearchProgress.clear();
		watchedContainers.clear();
		settledContainers.clear();
		paintedThisFrame.clear();
		slots.clear();
		cutItems.clear();
		drawnUnderInterface.clear();
		dragged = null;
		draggedThisHook = null;
		backdropFrame = -1;
		patchBuffers.clear();
		patchBuffersUsed = 0;
		cachedPalette = null;
		cachedPaletteBrightness = 0;
		lastKnownBrightness = Double.NaN;
		lastKnownQuality = null;
		lastKnownCustomRotationsEnabled = true;
		lastKnownStretched = false;
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event) {
		frameCount++;
		slots.values().removeIf(slot -> slot.lastUsed < frameCount - 1);
		rendersStartedThisFrame = 0;
		paintedThisFrame.clear();
		cutItems.clear();
		drawnUnderInterface.clear();
		dragged = null;
		draggedThisHook = null;
		patchBuffersUsed = 0;

		double brightness = client.getTextureProvider().getBrightness();
		IconQuality quality = config.iconQuality();
		boolean customRotationsEnabled = config.customRotationsEnabled();
		// Icons are sharpened only when the interface is stretched, so they can't be shared
		// between the two - a sharpened icon drawn at 1:1 looks over-sharpened
		boolean stretched = client.isStretchedEnabled();
		if (brightness != lastKnownBrightness || quality != lastKnownQuality
			|| customRotationsEnabled != lastKnownCustomRotationsEnabled
			|| stretched != lastKnownStretched) {
			// Both the game's icons and ours depend on the brightness setting, and our own
			// renders depend on the configured supersampling quality
			lastKnownBrightness = brightness;
			lastKnownQuality = quality;
			lastKnownCustomRotationsEnabled = customRotationsEnabled;
			lastKnownStretched = stretched;
			referenceIcons.clear();
			renderedIcons.clear();
			settledContainers.clear();
			iconCache = iconCacheDirectory == null ? null : new ItemIconCache(iconCacheDirectory, quality, brightness, customRotationsEnabled, stretched, PATCH_SIZE);
			if (iconCache != null)
				renderExecutor.execute(iconCache::markUsed);
		}

		// Every frame, so icons are prepared as soon as the game sends the items, not once an interface shows them
		prefetchQueuedContainers();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		watchedContainers.put(event.getContainerId(), event.getItemContainer().getItems());
		settledContainers.remove(event.getContainerId());
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (!active)
			return null;
		boolean draggedHere = placeDraggedItem();
		super.render(graphics);
		if (draggedHere)
			cutOutDraggedItem();
		return null;
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
		// Placed before the rest
		if (widgetItem.getWidget() == client.getDraggedWidget())
			return;

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
		boolean isDragged = widget == client.getDraggedWidget();
		// Some interfaces report their items twice. The dragged item can be anywhere over the others.
		if (onScreen.isEmpty() || !isDragged && !paintedThisFrame.add(bounds))
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
		if (isDragged)
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

	/**
	 * The game draws the dragged item as soon as the rest of its interface is drawn, so after its layer but before the
	 * interface's overlays. Where it's already there, it's taken back out before the items under it are done.
	 */
	private boolean placeDraggedItem() {
		Widget draggedWidget = client.getDraggedWidget();
		// Once a frame, even where its interface reports it twice
		if (draggedWidget == null || dragged != null)
			return false;
		for (WidgetItem widgetItem : overlayManager.getWidgetItems()) {
			if (widgetItem.getWidget() != draggedWidget)
				continue;
			PlacedIcon placed = place(widgetItem);
			if (placed == null)
				return false;
			placed.drawnByGame = nextPatchBuffer();
			Arrays.fill(placed.drawnByGame, 0);
			if (backdropFrame == frameCount && placed.bounds.equals(backdropBounds)) {
				BufferProvider frame = client.getBufferProvider();
				takeOutGameDraggedItem(frame.getPixels(), frame.getWidth(), placed, backdrop);
			}
			dragged = placed;
			return true;
		}
		return false;
	}

	private void keepBackdrop(Rectangle bounds) {
		BufferProvider frame = client.getBufferProvider();
		int[] framePixels = frame.getPixels();
		if (framePixels == null)
			return;
		Rectangle area = new Rectangle(bounds.x - MARGIN, bounds.y - MARGIN, PATCH_WIDTH, PATCH_HEIGHT)
			.intersection(new Rectangle(frame.getWidth(), frame.getHeight()));
		for (int y = area.y; y < area.y + area.height; y++)
			for (int x = area.x; x < area.x + area.width; x++)
				backdrop[patchIndex(x - bounds.x, y - bounds.y)] = framePixels[y * frame.getWidth() + x];
		backdropBounds.setBounds(bounds);
		backdropFrame = frameCount;
	}

	/**
	 * Puts back what was under the game's dragged item, and works out what the game drew from how it's half see-through.
	 * Only where the game's icon covers, so anything drawn after it stays.
	 */
	private static void takeOutGameDraggedItem(int[] framePixels, int frameWidth, PlacedIcon dragged, int[] under) {
		ReferenceIcon reference = dragged.reference;
		for (int y = dragged.patchArea.y; y < dragged.patchArea.y + dragged.patchArea.height; y++) {
			for (int x = dragged.patchArea.x; x < dragged.patchArea.x + dragged.patchArea.width; x++) {
				int i = y * frameWidth + x;
				int patch = patchIndex(x - dragged.bounds.x, y - dragged.bounds.y);
				if (framePixels[i] == under[patch] || !reference.has(patch, FLAG_ITEM)
					&& !reference.has(patch, FLAG_SHADOW) && !reference.has(patch, FLAG_STACK_TEXT))
					continue;
				dragged.drawnByGame[patch] = unblendDragged(framePixels[i], under[patch]);
				framePixels[i] = under[patch];
			}
		}
	}

	// The game draws the dragged item half see-through, and every pixel of its icon is opaque
	private static int unblendDragged(int drawn, int under) {
		int result = 0xFF000000;
		for (int shift = 0; shift < 24; shift += 8) {
			int channel = Math.round(((drawn >>> shift & 0xFF) - (under >>> shift & 0xFF) * (1 - DRAGGED_OPACITY)) / DRAGGED_OPACITY);
			result |= Math.max(0, Math.min(255, channel)) << shift;
		}
		return result;
	}

	private void cutOutDraggedItem() {
		PlacedIcon placed = dragged;
		if (placed == null)
			return;
		if (client.isGpu()) {
			// Drawn under everything, since the game draws the dragged item over the rest of the interface
			BufferProvider frame = client.getBufferProvider();
			int[] framePixels = frame.getPixels();
			int frameWidth = frame.getWidth();
			placed.background = nextPatchBuffer();
			readPatch(framePixels, frameWidth, placed, placed.background);
			for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
				int row = y * frameWidth + placed.patchArea.x;
				Arrays.fill(framePixels, row, row + placed.patchArea.width, 0);
			}
		}
		draggedThisHook = placed;
	}

	private void drawDraggedItem() {
		PlacedIcon placed = dragged;
		if (placed == null || placed.background == null)
			return;

		BufferProvider frame = client.getBufferProvider();
		int[] framePixels = frame.getPixels();
		int frameWidth = frame.getWidth();
		boolean gpu = client.isGpu();
		boolean foundEarlier = false;
		for (int pixel : placed.drawnByGame)
			foundEarlier |= pixel != 0;
		if (!foundEarlier) {
			// With GPU what it's drawn over is cut out
			takeOutGameDraggedItem(framePixels, frameWidth, placed, gpu ? EMPTY_PATCH : placed.background);
		}

		int shadowColor = 0;
		for (int patch = 0; patch < PATCH_SIZE; patch++) {
			if (placed.drawnByGame[patch] != 0 && placed.reference.has(patch, FLAG_SHADOW) && !placed.reference.has(patch, FLAG_STACK_TEXT)) {
				shadowColor = scale(placed.drawnByGame[patch], DRAGGED_OPACITY);
				break;
			}
		}
		if (gpu)
			Arrays.fill(patchColor, 0);
		else
			System.arraycopy(placed.background, 0, patchColor, 0, PATCH_SIZE);
		composeIcon(placed, shadowColor, DRAGGED_OPACITY);
		int[] drawn = drawWithOverlays(placed);
		// Its count, as the game drew it
		for (int patch = 0; patch < PATCH_SIZE; patch++)
			if (placed.drawnByGame[patch] != 0 && placed.reference.has(patch, FLAG_STACK_TEXT))
				drawn[patch] = over(scale(placed.drawnByGame[patch], DRAGGED_OPACITY), drawn[patch]);

		if (gpu) {
			placed.drawn = drawn;
			return;
		}
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++)
			for (int x = placed.patchArea.x; x < placed.patchArea.x + placed.patchArea.width; x++)
				framePixels[y * frameWidth + x] = drawn[patchIndex(x - placed.bounds.x, y - placed.bounds.y)];
	}

	/**
	 * The dragged item goes over the items, newest first, and what the dragged item is drawn over goes under them all.
	 */
	private void drawUnderInterface() {
		BufferProvider frame = client.getBufferProvider();
		int[] framePixels = frame.getPixels();
		if (framePixels == null || !client.isGpu()) {
			drawnUnderInterface.clear();
			return;
		}
		int frameWidth = frame.getWidth();
		PlacedIcon placed = dragged;
		boolean draggedDrawn = placed != null && placed.drawn != null;
		if (draggedDrawn)
			drawUnder(framePixels, frameWidth, placed, placed.drawn);
		for (int i = drawnUnderInterface.size() - 1; i >= 0; i--) {
			PlacedIcon item = drawnUnderInterface.get(i);
			drawUnder(framePixels, frameWidth, item, item.drawn);
		}
		drawnUnderInterface.clear();
		if (draggedDrawn)
			drawUnder(framePixels, frameWidth, placed, placed.background);
	}

	private static void drawUnder(int[] framePixels, int frameWidth, PlacedIcon placed, int[] patchPixels) {
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
			for (int x = placed.patchArea.x; x < placed.patchArea.x + placed.patchArea.width; x++) {
				int i = y * frameWidth + x;
				int above = framePixels[i];
				if (above >>> 24 == 0xFF)
					continue;
				int ours = patchPixels[patchIndex(x - placed.bounds.x, y - placed.bounds.y)];
				framePixels[i] = above == 0 ? ours : over(above, ours);
			}
		}
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
			icon.itemId = itemId;
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
				return PENDING;
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
		// What's carried and worn stays ready, even while it isn't shown or the cache is full
		for (int containerId : KEPT_CONTAINERS) {
			Item[] items = watchedContainers.get(containerId);
			if (items == null)
				continue;
			for (Item item : items) {
				if (item.getId() != -1)
					resolveIcon(item.getId(), item.getQuantity(), ItemQuantityMode.NEVER, 1, false);
			}
		}

		for (Map.Entry<Integer, Item[]> entry : watchedContainers.entrySet()) {
			int containerId = entry.getKey();
			if (settledContainers.contains(containerId))
				continue;

			boolean settled = true;
			for (Item item : entry.getValue()) {
				if (renderedIcons.size() >= MAX_CACHED_ICONS)
					return;
				if (item.getId() == -1)
					continue;
				RenderedIcon icon = resolveIcon(item.getId(), item.getQuantity(), ItemQuantityMode.NEVER, 1, false);
				// A stack whose model is still being searched for doesn't hold up the rest
				if (icon == PENDING && rendersStartedThisFrame >= MAX_NEW_RENDERS_PER_FRAME)
					return;
				settled &= icon == null || icon.pixels != null;
			}
			if (settled)
				settledContainers.add(containerId);
		}
	}

	@Nullable
	private ReferenceIcon lookupReferenceIcon(int itemId, int quantity, int quantityMode, int borderWidth, boolean visibleNow) {
		long key = (long) quantity << 24 | (long) itemId << 4 | (long) quantityMode << 2 | borderWidth;
		ReferenceIcon cached = referenceIcons.get(key);
		if (cached != null || referenceIcons.containsKey(key))
			return cached;
		if (!consumeRenderBudget(visibleNow))
			return UNRESOLVED;

		ReferenceIcon reference = null;
		int[] plain = fetchGamePixels(itemId, quantity, borderWidth, ItemQuantityMode.NEVER, false);
		int[] withCount = plain == null || quantityMode == ItemQuantityMode.NEVER ? plain :
			fetchGamePixels(itemId, quantity, borderWidth, quantityMode, false);
		if (withCount != null)
			reference = new ReferenceIcon(itemId, borderWidth, config.iconQuality().getSupersample(), plain, withCount);
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
		boolean sharpen = lastKnownStretched;
		ItemIconCache cache = iconCache;
		renderExecutor.execute(() -> {
			try {
				int[] palette = paletteFor(brightness);
				int[] combined = null;
				for (ModelLayer layer : layers) {
					CustomRotation placement = layer.placement;
					ItemIconRasterizer rasterizer = placement == null
						? new ItemIconRasterizer(layer.model, layer.pitchJau, layer.yawJau, layer.rollJau, supersample)
						: new ItemIconRasterizer(layer.model, layer.pitchJau, layer.yawJau, layer.rollJau,
							placement.resizeX, placement.resizeY, placement.resizeZ, supersample);
					if (placement != null) {
						rasterizer.placeExplicitly(placement.zoom2d, placement.offsetX, placement.offsetY);
					} else if (!rasterizer.fitToReferenceSilhouette(layer.referencePixels, palette)) {
						icon.failed = true;
						if (cache != null)
							cache.save(fingerprint, null);
						return;
					}
					int[] rendered = rasterizer.render(MARGIN, layer.borderWidth > 0, sharpen, palette);
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
		ItemIconRasterizer.IconModel iconModel = captureModel(item);
		int[] referencePixels = fetchGamePixels(itemId, quantity, 0, ItemQuantityMode.NEVER, noted);
		if (iconModel == null || referencePixels == null)
			return null;

		CustomRotation override = config.customRotationsEnabled() ? rotationStorage.get(itemId) : null;
		int pitch = override != null ? override.xan2d : item.getXan2d();
		int yaw = override != null ? override.yan2d : item.getYan2d();
		int roll = override != null ? override.zan2d : item.getZan2d();
		return new ModelLayer(iconModel, pitch, yaw, roll, referencePixels, borderWidth, override);
	}

	@Nullable
	private ItemIconRasterizer.IconModel captureModel(ItemComposition item) {
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
		return ItemIconRasterizer.IconModel.capture(litModel, client.getTextureProvider());
	}

	/** Called from the menu entry's click handler, which already runs on the client thread. */
	public void openRotationEditor(int itemId) {
		if (!active || Double.isNaN(lastKnownBrightness))
			return;

		ItemComposition item = client.getItemDefinition(itemId);
		ItemIconRasterizer.IconModel iconModel = captureModel(item);
		if (iconModel == null)
			return;

		int[] palette = paletteFor(lastKnownBrightness);
		int supersample = config.iconQuality().getSupersample();

		ItemRenderSheet.Placement sheetDefault = itemRenderSheet.get(itemId);
		CustomRotation defaults = new CustomRotation(item.getXan2d(), item.getYan2d(), item.getZan2d(),
			sheetDefault.zoom2d, sheetDefault.offsetX, sheetDefault.offsetY,
			sheetDefault.resizeX, sheetDefault.resizeY, sheetDefault.resizeZ);
		CustomRotation existing = rotationStorage.get(itemId);
		CustomRotation initial = existing != null ? existing : defaults;
		String name = item.getName();

		SwingUtilities.invokeLater(() -> {
			if (!active)
				return;
			RotationEditorDialog dialog = new RotationEditorDialog(name, itemId, initial, defaults,
				iconModel, palette, supersample, rotationStorage, this::onRotationChanged);
			openRotationDialog = dialog;
			dialog.setVisible(true);
		});
	}

	private void onRotationChanged(int itemId) {
		clientThread.invoke(() -> clearRenderCache(itemId));
	}

	/** The cache is keyed by the game's own icon, which a saved rotation doesn't change. */
	public void clearRenderCache(int itemId) {
		clearRenderCache(Set.of(itemId));
	}

	public void clearRenderCache(Collection<Integer> itemIds) {
		if (itemIds.isEmpty())
			return;

		Set<Integer> targets = Set.copyOf(itemIds);
		List<Long> cleared = new ArrayList<>();
		renderedIcons.replaceAll((fingerprint, icon) -> {
			if (!targets.contains(icon.itemId))
				return icon;
			cleared.add(fingerprint);
			return renderAgain(icon);
		});
		deleteCachedImages(cleared);
	}

	public void clearRenderCache() {
		List<Long> cleared = new ArrayList<>(renderedIcons.keySet());
		renderedIcons.replaceAll((fingerprint, icon) -> renderAgain(icon));
		settledContainers.clear();
		deleteCachedImages(cleared);
	}

	// Not loaded from disk, where the old icon may not be deleted yet
	private static RenderedIcon renderAgain(RenderedIcon icon) {
		RenderedIcon fresh = new RenderedIcon();
		fresh.itemId = icon.itemId;
		fresh.uncached = true;
		return fresh;
	}

	private void deleteCachedImages(List<Long> fingerprints) {
		ItemIconCache cache = iconCache;
		ExecutorService executor = renderExecutor;
		if (cache == null || executor == null || fingerprints.isEmpty())
			return;

		executor.execute(() -> {
			for (long fingerprint : fingerprints)
				cache.delete(fingerprint);
		});
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

		// Detected first, since it samples the pixels the loop below marks for inpainting
		int shadowColor = detectUniformShadowColor(reference);
		// The game's shadow goes whether or not its colour could be identified: a colour we can't
		// match means we draw no shadow of our own, not that we keep theirs. Leaving it behind
		// only stays hidden while our icon covers it, so a custom rotation exposes it as a fringe.
		for (int patch = 0; patch < PATCH_SIZE; patch++)
			if (reference.has(patch, FLAG_SHADOW))
				patchResolved[patch] = false;

		// Each pass only reaches one pixel further in, and the pixels it hasn't reached still hold
		// the game's icon, so this runs until the whole silhouette is covered rather than to a
		// fixed budget. Stopping early left the middle of the icon in place, which only stayed
		// hidden while ours covered the same shape - a custom rotation exposes it.
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
					background = blend(shadowColor, shade, background, 1 - alpha(shadowColor) * shade);
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

	/**
	 * Takes what other overlays drew over the cut out item. Fills and outlines that follow the game's icon, like
	 * Inventory Tags', are redrawn to fit ours, with fills covering the shadow too. Everything else stays where it was
	 * drawn, unless it's all taken.
	 */
	private void captureOverlays(int[] framePixels, int frameWidth, PlacedIcon placed) {
		// Nothing was drawn over the item while its pixels are still as they were cut out
		if (!placed.takeAll && placed.slot != null && samePixels(framePixels, frameWidth, placed, placed.slot.cut)) {
			placed.overlays = null;
			return;
		}
		ReferenceIcon reference = placed.reference;
		placed.overlays = nextPatchBuffer();
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
			for (int x = placed.patchArea.x; x < placed.patchArea.x + placed.patchArea.width; x++) {
				int patch = patchIndex(x - placed.bounds.x, y - placed.bounds.y);
				placed.overlays[patch] = !placed.takeAll && reference.has(patch, FLAG_STACK_TEXT) ? 0 : framePixels[y * frameWidth + x];
			}
		}
		placed.fill = shapeColor(placed, FLAG_ITEM);
		placed.outline = shapeColor(placed, FLAG_OUTLINE);
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
			for (int x = placed.patchArea.x; x < placed.patchArea.x + placed.patchArea.width; x++) {
				int patch = patchIndex(x - placed.bounds.x, y - placed.bounds.y);
				if (placed.takeAll || isRedrawn(placed, patch))
					framePixels[y * frameWidth + x] = 0;
			}
		}
	}

	private static boolean isRedrawn(PlacedIcon placed, int patch) {
		ReferenceIcon reference = placed.reference;
		int overlay = placed.overlays[patch];
		return !reference.has(patch, FLAG_STACK_TEXT) && (
			placed.fill != 0 && (reference.has(patch, FLAG_ITEM) && overlay == placed.fill || reference.has(patch, FLAG_SHADOW)) ||
			placed.outline != 0 && reference.has(patch, FLAG_OUTLINE) && overlay == placed.outline);
	}

	private int[] drawWithOverlays(PlacedIcon placed) {
		Slot slot = placed.slot;
		if (placed.overlays == null)
			return placed.composed;
		if (slot != null && slot.overlaid && slot.takeAll == placed.takeAll && samePatch(placed, placed.overlays, slot.overlays))
			return slot.drawn;

		int fill = placed.fill;
		int outline = placed.outline;
		int[] drawn = slot != null ? slot.drawn : nextPatchBuffer();
		for (int y = placed.patchArea.y; y < placed.patchArea.y + placed.patchArea.height; y++) {
			for (int x = placed.patchArea.x; x < placed.patchArea.x + placed.patchArea.width; x++) {
				int localX = x - placed.bounds.x;
				int localY = y - placed.bounds.y;
				int patch = patchIndex(localX, localY);
				// What isn't taken stays in the interface, over ours
				int overlay = placed.takeAll && !isRedrawn(placed, patch) ? placed.overlays[patch] : 0;
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
			slot.takeAll = placed.takeAll;
		}
		return drawn;
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
