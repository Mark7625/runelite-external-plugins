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
import java.io.DataInputStream;
import java.io.DataOutputStream;
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
import net.runelite.client.ui.overlay.OverlayManager;
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

	// We paint a small margin around the icon too, since our render can spill slightly past
	// the game's own tighter bounding box.
	private static final int MARGIN = 2;
	private static final int PATCH_WIDTH = ICON_WIDTH + 2 * MARGIN;
	private static final int PATCH_HEIGHT = ICON_HEIGHT + 2 * MARGIN;
	private static final int PATCH_SIZE = PATCH_WIDTH * PATCH_HEIGHT;

	private static final byte FLAG_ITEM = 0x1;
	private static final byte FLAG_SHADOW = 0x2;
	private static final byte FLAG_STACK_TEXT = 0x4;

	private static final int[] NEIGHBOR_DX = {-1, 1, 0, 0};
	private static final int[] NEIGHBOR_DY = {0, 0, -1, 1};

	/**
	 * A queued or finished render. A shared {@link #PENDING} instance stands in for "still
	 * being worked on" so callers don't need a separate lookup to check.
	 */
	private static final class RenderedIcon {
		volatile boolean failed;
		volatile int[] pixels;
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

	private ExecutorService renderExecutor;
	private boolean active;
	private double lastKnownBrightness = Double.NaN;
	@Nullable
	private IconQuality lastKnownQuality;
	private boolean lastKnownCustomRotationsEnabled = true;
	@Nullable
	private Filepath iconCacheDirectory;
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
	// Containers whose items have all already been queued for a render (or found ineligible),
	// so the prefetch pass can skip re-scanning them every frame - this matters a lot for the
	// bank, which can hold 800+ slots.
	private final Set<Integer> settledContainers = new HashSet<>();
	private final Set<Integer> hookedInterfaces = new HashSet<>();

	private int rendersStartedThisFrame;
	private final Set<Rectangle> paintedThisFrame = new HashSet<>();
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
		try {
			if (!dataDirectory.isDirectory())
				dataDirectory.createDirectories();
			rotationStorage.load(dataDirectory);
			if (config.iconCacheStorage() == IconCacheStorage.DISK)
				iconCacheDirectory = dataDirectory;
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
			hookInterface(client.getTopLevelInterfaceId());
			for (WidgetNode node : client.getComponentTable())
				hookInterface(node.getId());
			overlayManager.add(this);
		});
	}

	public void shutDown() {
		if (!active)
			return;
		active = false;
		eventBus.unregister(this);
		overlayManager.remove(this);
		renderExecutor.shutdownNow();
		renderExecutor = null;
		rotationStorage.reset();

		RotationEditorDialog dialog = openRotationDialog;
		openRotationDialog = null;
		if (dialog != null)
			SwingUtilities.invokeLater(dialog::dispose);

		iconCacheDirectory = null;
		renderedIcons.clear();
		referenceIcons.clear();
		resolvedStackModels.clear();
		stackModelSearchProgress.clear();
		watchedContainers.clear();
		settledContainers.clear();
		hookedInterfaces.clear();
		paintedThisFrame.clear();
		cachedPalette = null;
		cachedPaletteBrightness = 0;
		lastKnownBrightness = Double.NaN;
		lastKnownQuality = null;
		lastKnownCustomRotationsEnabled = true;
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event) {
		rendersStartedThisFrame = 0;
		paintedThisFrame.clear();

		double brightness = client.getTextureProvider().getBrightness();
		IconQuality quality = config.iconQuality();
		boolean customRotationsEnabled = config.customRotationsEnabled();
		if (brightness != lastKnownBrightness || quality != lastKnownQuality
			|| customRotationsEnabled != lastKnownCustomRotationsEnabled) {
			// Both the game's icons and ours depend on the brightness setting, and our own
			// renders depend on the configured supersampling quality
			lastKnownBrightness = brightness;
			lastKnownQuality = quality;
			lastKnownCustomRotationsEnabled = customRotationsEnabled;
			referenceIcons.clear();
			renderedIcons.clear();
			settledContainers.clear();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (hookInterface(event.getGroupId())) {
			// Re-add so the overlay manager notices the new draw hook
			overlayManager.remove(this);
			overlayManager.add(this);
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
		return true;
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (!active)
			return null;
		super.render(graphics);
		prefetchQueuedContainers();
		return null;
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
		Widget widget = widgetItem.getWidget();
		// The game draws the dragged item after the interface, half transparent; leave that alone
		if (widget == client.getDraggedWidget() || widgetItem.getDraggingCanvasBounds() != null)
			return;

		Rectangle bounds = widgetItem.getCanvasBounds();
		if (bounds.width != ICON_WIDTH || bounds.height != ICON_HEIGHT)
			return;

		int quantity = widgetItem.getQuantity();
		int quantityMode = widget.getItemQuantityMode();
		int borderWidth = widget.getBorderType();

		// Looked up once and threaded through, rather than resolving the reference icon twice
		// (once implicitly inside icon resolution, once again for compositing).
		ReferenceIcon reference = lookupReferenceIcon(itemId, quantity, quantityMode, borderWidth, true);
		if (reference == null || reference == UNRESOLVED)
			return;
		RenderedIcon icon = iconFor(reference, itemId, quantity, borderWidth, true);
		if (icon == null || icon == PENDING || icon.pixels == null)
			return;

		Rectangle onScreen = bounds.intersection(widget.getParent().getBounds());
		// Some interfaces report their items twice
		if (onScreen.isEmpty() || !paintedThisFrame.add(bounds))
			return;

		BufferProvider frame = client.getBufferProvider();
		int[] framePixels = frame.getPixels();
		if (framePixels == null)
			return;

		// Partly visible items stay clipped like the interface clips them
		Rectangle patchArea = !onScreen.equals(bounds) ? onScreen :
			new Rectangle(bounds.x - MARGIN, bounds.y - MARGIN, PATCH_WIDTH, PATCH_HEIGHT)
				.intersection(new Rectangle(frame.getWidth(), frame.getHeight()));
		if (patchArea.isEmpty())
			return;

		float opacity = (256 - widget.getOpacity()) / 256f;
		int shadowColor = inpaintBackground(framePixels, frame.getWidth(), bounds, patchArea, reference);
		paintIconOverBackground(framePixels, frame.getWidth(), bounds, patchArea, reference, icon.pixels, shadowColor, opacity);
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
			int modelItemId = resolveModelItemId(itemId, quantity, borderWidth, reference);
			if (modelItemId == -1)
				return null;
			if (modelItemId == -2)
				icon.failed = true;
			else
				beginRenderingIcon(icon, modelItemId, borderWidth, reference.fingerprint);
			renderedIcons.put(reference.fingerprint, icon);
		}
		return icon.failed ? null : icon;
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
		Filepath cacheFile = iconCacheDirectory == null ? null : cacheFileFor(fingerprint);
		renderExecutor.execute(() -> {
			try {
				if (cacheFile != null) {
					int[] cached = readCachedPixels(cacheFile);
					if (cached != null) {
						icon.pixels = cached;
						return;
					}
				}

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
						return;
					}
					int[] rendered = rasterizer.render(MARGIN, layer.borderWidth, palette);
					combined = combined == null ? rendered : ItemIconRasterizer.compositeOver(rendered, combined);
				}
				icon.pixels = combined;

				if (cacheFile != null)
					writeCachedPixels(cacheFile, combined);
			} catch (Throwable ex) {
				log.debug("Unable to render an HD icon for item {}:", itemId, ex);
				icon.failed = true;
			}
		});
	}

	private Filepath cacheFileFor(long fingerprint) {
		return iconCacheDirectory.join(Long.toHexString(fingerprint) + ".bin");
	}

	/**
	 * Client thread never calls this - only from within the render executor. Raw ints, not
	 * Java object serialization; a size mismatch (e.g. after a plugin update changes the icon
	 * dimensions) is treated as a cache miss rather than trusted.
	 */
	@Nullable
	private int[] readCachedPixels(Filepath file) {
		if (!file.isFile())
			return null;
		try (DataInputStream in = new DataInputStream(file.openInputStream())) {
			int[] pixels = new int[PATCH_SIZE];
			for (int i = 0; i < pixels.length; i++)
				pixels[i] = in.readInt();
			return pixels;
		} catch (IOException e) {
			log.debug("Couldn't read cached icon {}, will re-render", file, e);
			return null;
		}
	}

	private void writeCachedPixels(Filepath file, int[] pixels) {
		try (DataOutputStream out = new DataOutputStream(file.openOutputStream())) {
			for (int pixel : pixels)
				out.writeInt(pixel);
		} catch (IOException e) {
			log.debug("Couldn't write icon cache file {}", file, e);
		}
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
		renderedIcons.entrySet().removeIf(entry -> {
			if (!targets.contains(entry.getValue().itemId))
				return false;
			cleared.add(entry.getKey());
			return true;
		});
		deleteCachedImages(cleared);
	}

	public void clearRenderCache() {
		List<Long> cleared = new ArrayList<>(renderedIcons.keySet());
		renderedIcons.clear();
		settledContainers.clear();
		deleteCachedImages(cleared);
	}

	private void deleteCachedImages(List<Long> fingerprints) {
		Filepath directory = iconCacheDirectory;
		ExecutorService executor = renderExecutor;
		if (directory == null || executor == null || fingerprints.isEmpty())
			return;

		executor.execute(() -> {
			for (long fingerprint : fingerprints) {
				Filepath file = directory.join(Long.toHexString(fingerprint) + ".bin");
				try {
					file.deleteIfExists();
				} catch (IOException e) {
					log.debug("Couldn't delete cached icon {}", file, e);
				}
			}
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
				patchColor[patch] = framePixels[y * frameWidth + x] & 0xFFFFFF;
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

				int count = 0, r = 0, g = 0, b = 0;
				for (int n = 0; n < NEIGHBOR_DX.length; n++) {
					int nx = x + NEIGHBOR_DX[n];
					int ny = y + NEIGHBOR_DY[n];
					if (nx < 0 || nx >= PATCH_WIDTH || ny < 0 || ny >= PATCH_HEIGHT)
						continue;
					int neighborPatch = ny * PATCH_WIDTH + nx;
					if (!patchResolved[neighborPatch])
						continue;
					int neighborColor = patchColor[neighborPatch];
					r += neighborColor >> 16 & 0xFF;
					g += neighborColor >> 8 & 0xFF;
					b += neighborColor & 0xFF;
					count++;
				}
				if (count > 0) {
					patchColor[patch] = (r / count) << 16 | (g / count) << 8 | (b / count);
					patchResolvedScratch[patch] = true;
					resolvedAny = true;
				}
			}
		}
		System.arraycopy(patchResolvedScratch, 0, patchResolved, 0, PATCH_SIZE);
		return resolvedAny;
	}

	private void paintIconOverBackground(int[] framePixels, int frameWidth, Rectangle iconBounds, Rectangle patchArea,
										ReferenceIcon reference, int[] iconPixels, int shadowColor, float opacity) {
		for (int y = patchArea.y; y < patchArea.y + patchArea.height; y++) {
			for (int x = patchArea.x; x < patchArea.x + patchArea.width; x++) {
				int localX = x - iconBounds.x;
				int localY = y - iconBounds.y;
				int patch = patchIndex(localX, localY);
				if (reference.has(patch, FLAG_STACK_TEXT))
					continue;

				int iconArgb = iconPixels[patch];
				float shade = 0;
				if (shadowColor != 0 && localX - 1 >= -MARGIN && localY - 1 >= -MARGIN)
					shade = (iconPixels[patchIndex(localX - 1, localY - 1)] >>> 24) / 255f;

				// Pixels the old icon (or its shadow) never touched, and that our own icon
				// doesn't draw into either, are untouched slot background - leave them and their
				// real alpha alone. Transparent side panels rely on that alpha channel (the GPU
				// compositor blends the interface over the 3D scene with it); stamping every
				// patch pixel fully opaque, as this used to unconditionally do, punches a solid
				// opaque square into an otherwise translucent panel.
				boolean erasingOldIcon = reference.has(patch, FLAG_ITEM) || reference.has(patch, FLAG_SHADOW);
				if (!erasingOldIcon && (iconArgb >>> 24) == 0 && shade == 0)
					continue;

				float iconAlpha = (iconArgb >>> 24) / 255f * opacity;
				int r = patchColor[patch] >> 16 & 0xFF;
				int g = patchColor[patch] >> 8 & 0xFF;
				int b = patchColor[patch] & 0xFF;

				if (shade > 0) {
					r = Math.round((shadowColor >> 16 & 0xFF) * shade + r * (1 - shade));
					g = Math.round((shadowColor >> 8 & 0xFF) * shade + g * (1 - shade));
					b = Math.round((shadowColor & 0xFF) * shade + b * (1 - shade));
				}

				r = clamp8(Math.round((iconArgb >> 16 & 0xFF) * opacity + r * (1 - iconAlpha)));
				g = clamp8(Math.round((iconArgb >> 8 & 0xFF) * opacity + g * (1 - iconAlpha)));
				b = clamp8(Math.round((iconArgb & 0xFF) * opacity + b * (1 - iconAlpha)));
				framePixels[y * frameWidth + x] = 0xFF000000 | r << 16 | g << 8 | b;
			}
		}
	}

	private static int clamp8(int v) {
		return v < 0 ? 0 : Math.min(255, v);
	}
}
