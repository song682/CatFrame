package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import com.google.gson.JsonObject;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateRoot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for resource manager reloads and detects model JSON / BlockState JSON content provided by the top
 * resource pack.
 * <p>
 * When the player loads or switches a resource pack, the known model and blockstate paths of every registered
 * namespace are scanned automatically; {@link IResourceManager#getResource(ResourceLocation)} is used to fetch the
 * version in the top resource pack, which is parsed into {@link ModelJson} / {@link BlockstateJson} for other
 * subsystems to query.
 * <p>
 * Registration fully reuses the deferred-registration pattern of
 * {@link decok.dfcdvadstf.catframe.adapter.vanilla.LanguageReloadListener}: when the resource manager is not ready
 * it registers via a one-shot ClientTick.
 * <p>
 * Resource-pack override pipeline (scan → merge → re-register):
 * <ul>
 * <li><b>items/ ItemState decision trees</b>: candidate names come from the Item registry ∪ loaded decision trees;
 * {@link IResourceManager#getAllResources} distinguishes "jar-only" from "overridden/added by a pack",
 * and overrides are merged into {@link ModelManagerDataLoader#loadedItemStates} before rebuilding the item model
 * wrappers.</li>
 * <li><b>blockstates/</b>: candidate names come from the Block registry ∪ loaded blockstates; jar-provided and real
 * overrides are distinguished the same way, and overrides are merged into
 * {@link ModelManagerDataLoader#loadedBlockstates} before re-registering block models.</li>
 * <li><b>model_mappings.json</b>: candidate names are the registered namespaces
 * ({@link ModelManagerDataLoader#namespaces}); content comparison again decides a real override, merged with
 * <b>whole-file replacement</b> semantics into {@link ModelManagerDataLoader#loadedMappings}
 * before triggering a full re-registration (mappings may affect both block and item side registrations).</li>
 * <li><b>models/</b>: no merging needed - {@link ModelResolver} always goes through IResourceManager for every
 * resolution, so clearing the cache on reload naturally picks up resource packs; the scan results here are for
 * diagnostics only.</li>
 * </ul>
 * <p>
 * Timing note: in 1.7.10 the TextureMap reload listener registers before this listener, so on reload the texture
 * stitching (and the {@code registerAllModels} it triggers) has already finished; this listener is the tail of the
 * reload chain - merging overrides and re-registering lazy models here is a safe point. Note that
 * {@code registerReloadListener} fires an immediate callback once at registration time (when the atlas is not
 * ready), so the re-registration step assumes an atlas that is ready.
 * <p>
 * Known limitation: textures newly introduced by a resource pack (not registered during the preInit texture
 * collection phase) are not in the atlas and fall back to missingno; overriding the geometry/properties/decision
 * logic of existing models works fully.
 */
public class ResourcePackModelDetector implements IResourceManagerReloadListener {

    /** namespace:path → the ModelJson provided by the top resource pack */
    public static final Map<String, ModelJson> PACK_MODELS = new ConcurrentHashMap<>();
    /** namespace:blockName → the BlockstateJson provided by the top resource pack */
    public static final Map<String, BlockstateJson> PACK_BLOCKSTATES = new ConcurrentHashMap<>();
    /** namespace:itemName → the ItemState decision-tree root provided by the top resource pack (including root-level fields) */
    public static final Map<String, ItemStateRoot> PACK_ITEM_STATES = new ConcurrentHashMap<>();
    /** Set of model paths overridden by the top resource pack */
    public static final Set<String> PACK_MODEL_PATHS = ConcurrentHashMap.newKeySet();
    /** Set of blockstate paths overridden by the top resource pack */
    public static final Set<String> PACK_BLOCKSTATE_PATHS = ConcurrentHashMap.newKeySet();
    /** Set of ItemState paths overridden by the top resource pack */
    public static final Set<String> PACK_ITEM_STATE_PATHS = ConcurrentHashMap.newKeySet();
    /** namespace → the ModelMappings provided by the top resource pack (whole-file replacement semantics) */
    public static final Map<String, VanillaModelManager.ModelMappings> PACK_MAPPINGS = new ConcurrentHashMap<>();

    // ==================== Classpath baseline snapshots (used to restore when a pack is removed) ====================

    /** loadedBlockstates snapshot taken before the first merge (shallow copy of the inner map) */
    private static Map<String, Map<String, BlockstateJson>> baselineBlockstates = null;
    /** loadedItemStates snapshot taken before the first merge (shallow copy of the inner map) */
    private static Map<String, Map<String, ItemStateNode>> baselineItemStates = null;
    /** loadedOversizedItems snapshot taken before the first merge */
    private static Map<String, Set<String>> baselineOversizedItems = null;
    /** loadedMappings snapshot taken before the first merge (shallow copy of the inner blocks/items maps) */
    private static Map<String, VanillaModelManager.ModelMappings> baselineMappings = null;

    /** Whether blockstate overrides existed in the previous reload (re-registration is also needed when overrides disappear) */
    private static boolean blockOverridesWereActive = false;
    /** Whether ItemState overrides existed in the previous reload */
    private static boolean itemOverridesWereActive = false;
    /** Whether model_mappings overrides existed in the previous reload */
    private static boolean mappingsOverridesWereActive = false;

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        CatFrame.logger.debug("ResourcePackModelDetector: resource manager reloaded, scanning...");
        clear();
        ModelResolver.clearCache();
        scanAllNamespaces(manager);
        // Merge overrides and re-register lazy models (scan → restore baseline → overlay → re-register)
        applyOverrides();
        CatFrame.logger.info(
                "ResourcePackModelDetector: detected {} model overrides, {} blockstate overrides, {} item state overrides, {} mapping overrides",
                PACK_MODEL_PATHS.size(), PACK_BLOCKSTATE_PATHS.size(), PACK_ITEM_STATE_PATHS.size(),
                PACK_MAPPINGS.size());
    }

    private static void clear() {
        PACK_MODELS.clear();
        PACK_BLOCKSTATES.clear();
        PACK_ITEM_STATES.clear();
        PACK_MODEL_PATHS.clear();
        PACK_BLOCKSTATE_PATHS.clear();
        PACK_ITEM_STATE_PATHS.clear();
        PACK_MAPPINGS.clear();
    }

    private static void scanAllNamespaces(IResourceManager manager) {
        for (String ns : ModelManagerDataLoader.namespaces) {
            scanNamespace(manager, ns);
        }
    }

    private static void scanNamespace(IResourceManager manager, String ns) {
        // Scan BlockStates: candidate names = loaded blockstate names ∪ block names of this namespace in the Block registry
        // (the latter lets a resource pack add definitions for blocks whose blockstate the jar does not provide)
        Map<String, BlockstateJson> nsBlockstates = ModelManagerDataLoader.loadedBlockstates.get(ns);
        Set<String> blockCandidates = new LinkedHashSet<>();
        if (nsBlockstates != null) {
            blockCandidates.addAll(nsBlockstates.keySet());
        }
        for (Object obj : net.minecraft.block.Block.blockRegistry) {
            if (obj == null)
                continue;
            String registryName = net.minecraft.block.Block.blockRegistry.getNameForObject(obj);
            if (registryName == null)
                continue;
            String blockNs = registryName.contains(":") ? registryName.substring(0, registryName.indexOf(':'))
                    : "minecraft";
            if (!blockNs.equals(ns))
                continue;
            blockCandidates.add(
                    registryName.contains(":") ? registryName.substring(registryName.indexOf(':') + 1) : registryName);
        }
        for (String blockName : blockCandidates) {
            ResourceLocation loc = new ResourceLocation(ns, "blockstates/" + blockName + ".json");
            try {
                List<?> all = manager.getAllResources(loc);
                if (all == null || all.isEmpty())
                    continue;
                // In multi-mod environments Forge's ModResourcePack falls back to the global classpath when a
                // resource is missing inside the mod jar, so this jar's JSON is re-provided by every mod's pack layer
                // and layer counting necessarily false-positives (all.size() is always ≥2). Compare content instead: the
                // mod fallback serves the exact same bytes as the classpath, equal to the baseline → skipped; only a layer
                // whose content differs from the classpath baseline (real user pack override / pure addition) counts as an
                // override, and the last differing layer (highest priority) wins.
                IResource topResource = findTopOverride(all,
                        "/assets/" + ns + "/blockstates/" + blockName + ".json");
                if (topResource == null)
                    continue;
                BlockstateJson bs = ModelManagerDataLoader.blockstateGson.fromJson(
                        new InputStreamReader(topResource.getInputStream()),
                        BlockstateJson.class);
                if (bs != null) {
                    String key = ns + ":" + blockName;
                    PACK_BLOCKSTATES.put(key, bs);
                    PACK_BLOCKSTATE_PATHS.add(key);
                    CatFrame.logger.debug("ResourcePackModelDetector: detected top-pack blockstate '{}'", key);
                }
            } catch (Exception ignored) {
                // The top resource pack does not override this blockstate
            }
        }

        // Scan items/ ItemState decision trees
        scanItemStates(manager, ns);

        // Scan the resource pack for model_mappings.json overrides/additions (the actual merge happens in applyOverrides)
        // Scan pack overrides for model_mappings.json (the merge itself happens in applyOverrides)
        scanMappings(manager, ns);

        // Scan known model paths (diagnostics only - model loading itself already goes through ModelResolver → IResourceManager)
        // Use the effective mappings (pack overrides take priority over the classpath baseline) so models referenced by
        // newly added mappings also enter the diagnostic set
        // in state_mapping mode the mapping values are state names rather than model paths, so skip
        // Skip state_mapping mode: values are state names, not model paths
        VanillaModelManager.ModelMappings mappings = PACK_MAPPINGS.containsKey(ns)
                ? PACK_MAPPINGS.get(ns)
                : ModelManagerDataLoader.loadedMappings.get(ns);
        if (mappings != null && !mappings.state_mapping) {
            if (mappings.blocks != null) {
                for (String modelPath : mappings.blocks.values()) {
                    scanModel(manager, modelPath);
                }
            }
            if (mappings.items != null) {
                for (String modelPath : mappings.items.values()) {
                    scanModel(manager, modelPath);
                }
            }
        }
    }

    /**
     * Scans the {@code items/{name}.json} decision trees overridden/added by resource packs under this namespace.
     * <p>
     * Candidate names = item names of this namespace in the Item registry ∪ decision-tree names already loaded
     * from the classpath, matching the auto-discovery criteria of {@code NamespaceLoadTask#loadItemStates}.
     * **Content comparison** of {@link IResourceManager#getAllResources} decides whether it is a real override:
     * only a layer whose content differs from the classpath baseline (a real user pack override / pure addition) is
     * recorded; in multi-mod setups Forge's ModResourcePack falls back to the classpath and re-provides this jar's
     * JSON, whose content equals the baseline, so layer counting necessarily false-positives and identical-content
     * layers are always ignored.
     */
    private static void scanItemStates(IResourceManager manager, String ns) {
        Map<String, ItemStateNode> nsItemStates = ModelManagerDataLoader.loadedItemStates.get(ns);
        Set<String> candidates = new LinkedHashSet<>();
        for (Object obj : Item.itemRegistry) {
            if (obj == null)
                continue;
            String registryName = Item.itemRegistry.getNameForObject(obj);
            if (registryName == null)
                continue;
            String itemNs = registryName.contains(":") ? registryName.substring(0, registryName.indexOf(':'))
                    : "minecraft";
            if (!itemNs.equals(ns))
                continue;
            candidates.add(
                    registryName.contains(":") ? registryName.substring(registryName.indexOf(':') + 1) : registryName);
        }
        if (nsItemStates != null) {
            candidates.addAll(nsItemStates.keySet());
        }

        for (String itemName : candidates) {
            ResourceLocation loc = new ResourceLocation(ns, "items/" + itemName + ".json");
            try {
                List<?> all = manager.getAllResources(loc);
                if (all == null || all.isEmpty())
                    continue;
                // Same as scanNamespace: layer counting necessarily false-positives in multi-mod environments;
                // judge by content comparison instead.
                IResource topResource = findTopOverride(all,
                        "/assets/" + ns + "/items/" + itemName + ".json");
                if (topResource == null)
                    continue;
                JsonObject json = ModelResolver.GSON.fromJson(
                        new InputStreamReader(topResource.getInputStream()), JsonObject.class);
                ItemStateRoot root = ItemStateNode.parseRootFull(json);
                if (root != null) {
                    String key = ns + ":" + itemName;
                    PACK_ITEM_STATES.put(key, root);
                    PACK_ITEM_STATE_PATHS.add(key);
                    CatFrame.logger.debug("ResourcePackModelDetector: detected top-pack item state '{}'", key);
                }
            } catch (Exception ignored) {
                // The top resource pack does not override this ItemState
            }
        }
    }

    /**
     * Scans for {@code model_mappings.json} overridden/added by resource packs under this namespace.
     * <p>
     * Consistent with the blockstate/item scan: **content comparison** of
     * {@link IResourceManager#getAllResources} decides a real override (in multi-mod setups Forge's ModResourcePack
     * falls back to the classpath and re-provides this jar's JSON, whose content equals the baseline, so layer
     * counting necessarily false-positives).
     * <p>
     * Semantic note: a mappings override is a <b>whole-file replacement</b> rather than per-entry merging - the pack
     * must ship a complete model_mappings.json (all blocks/items entries); entries present in the jar version but
     * absent from the pack version vanish accordingly. This differs from the per-entry merge semantics of
     * blockstates/items.
     * <p>
     * Scan pack overrides for {@code model_mappings.json}. Unlike blockstates/items the
     * override is a <b>whole-file replacement</b>: the pack must ship the complete file
     * and entries missing from it vanish (no per-entry merging).
     */
    private static void scanMappings(IResourceManager manager, String ns) {
        ResourceLocation loc = new ResourceLocation(ns, "model_mappings.json");
        try {
            List<?> all = manager.getAllResources(loc);
            if (all == null || all.isEmpty())
                return;
            // Same as scanNamespace: layer counting necessarily false-positives in multi-mod environments;
            // judge by content comparison instead.
            IResource topResource = findTopOverride(all, "/assets/" + ns + "/model_mappings.json");
            if (topResource == null)
                return;
            VanillaModelManager.ModelMappings mappings = ModelResolver.GSON.fromJson(
                    new InputStreamReader(topResource.getInputStream()),
                    VanillaModelManager.ModelMappings.class);
            if (mappings != null) {
                PACK_MAPPINGS.put(ns, mappings);
                CatFrame.logger.debug("ResourcePackModelDetector: detected top-pack model mappings '{}'", ns);
            }
        } catch (Exception ignored) {
            // The top resource pack does not override this model_mappings
        }
    }

    private static void scanModel(IResourceManager manager, String modelPath) {
        if (modelPath == null)
            return;
        String ns, path;
        if (modelPath.contains(":")) {
            ns = modelPath.substring(0, modelPath.indexOf(':'));
            path = modelPath.substring(modelPath.indexOf(':') + 1);
        } else {
            ns = "minecraft";
            path = modelPath;
        }
        ResourceLocation loc = new ResourceLocation(ns, "models/" + path + ".json");
        try {
            // Same as blockstate/item: content comparison, ignoring fake mod-fallback layers
            // Diagnostic only — same content comparison to ignore mod-fallback layers
            List<?> all = manager.getAllResources(loc);
            IResource topResource = findTopOverride(all,
                    "/assets/" + ns + "/models/" + path + ".json");
            if (topResource == null)
                return;
            ModelJson model = ModelResolver.GSON.fromJson(
                    new InputStreamReader(topResource.getInputStream()),
                    ModelJson.class);
            if (model != null) {
                String key = ns + ":" + path;
                PACK_MODELS.put(key, model);
                PACK_MODEL_PATHS.add(key);
                CatFrame.logger.debug("ResourcePackModelDetector: detected top-pack model '{}'", key);
            }
        } catch (Exception ignored) {
            // The top resource pack does not override this model
        }
    }

    // ==================== Resource-pack override content detection ====================

    /**
     * Finds the last layer in the getAllResources layer list whose content differs from the classpath baseline.
     * <p>
     * In 1.7.10 Forge's ModResourcePack falls back to the global classpath when a resource is not found inside the mod
     * jar, so in multi-mod environments the JSON shipped by this jar is re-provided by every mod's pack layer, and it
     * serves exactly the same bytes as the classpath - layer counting necessarily false-positives, whereas content
     * comparison naturally excludes those fake layers: a layer counts as an override only when its content differs
     * from the classpath baseline (a real user pack override) or the baseline does not exist (a pure pack addition),
     * and the last differing layer is the highest-priority override.
     *
     * @param all           the complete layer list returned by getAllResources (may be empty)
     * @param classpathPath the classpath path of the same resource (e.g.
     *                      /assets/minecraft/blockstates/x.json)
     * @return the last layer whose content differs from the baseline; null when there is no real override
     */
    private static IResource findTopOverride(List<?> all, String classpathPath) {
        if (all == null || all.isEmpty())
            return null;
        byte[] baseline = readAll(ResourcePackModelDetector.class.getResourceAsStream(classpathPath));
        IResource override = null;
        for (Object obj : all) {
            if (!(obj instanceof IResource))
                continue;
            IResource res = (IResource) obj;
            byte[] content = readAll(res.getInputStream());
            if (!Arrays.equals(baseline, content)) {
                override = res;
            }
        }
        return override;
    }

    /** Reads all bytes from the input stream; returns null on failure or when the stream is empty */
    private static byte[] readAll(InputStream in) {
        if (in == null)
            return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1)
                out.write(buf, 0, n);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Shallow-copies a ModelMappings (inner blocks/items maps copied, entries shared).
     * <p>
     * No entry-level mutation happens between the baseline snapshot and the restore, so a shallow copy suffices to
     * isolate container replacement.
     * <p>
     * Shallow-copy a ModelMappings (inner blocks/items maps copied, entries shared).
     */
    private static VanillaModelManager.ModelMappings copyMappings(VanillaModelManager.ModelMappings src) {
        if (src == null)
            return null;
        VanillaModelManager.ModelMappings copy = new VanillaModelManager.ModelMappings();
        copy.state_mapping = src.state_mapping;
        copy.blocks = src.blocks != null ? new HashMap<>(src.blocks) : null;
        copy.items = src.items != null ? new HashMap<>(src.items) : null;
        return copy;
    }

    // ==================== Override application (merge + re-register) ====================

    /**
     * Merges the scanned resource-pack overrides into the model system and re-registers lazy models.
     * <p>
     * Apply scanned resource-pack overrides into the model system and re-register
     * lazy models.
     * <p>
     * Flow:
     * <ol>
     * <li>Incrementally snapshot the classpath baseline per namespace (shallow-copy the inner containers;
     * stitch-driven discovery can add namespaces at any pass)</li>
     * <li>Restore the baseline so overrides vanish when the pack is removed</li>
     * <li>Overlay this round's blockstate / ItemState overrides</li>
     * <li>Re-register on demand when the atlas is ready (full for blockstates, incremental for items only)</li>
     * </ol>
     * Note: during the immediate callback fired by {@code registerReloadListener} the atlas is not ready, so only
     * data is merged and re-registration is skipped (the first {@code registerAllModels} round is triggered by the
     * texture stitching event).
     */
    private static void applyOverrides() {
        // Model system not initialized yet (immediate callback during preInit) → nothing to merge
        if (!ModelManagerDataLoader.initialized)
            return;

        // 1. Snapshot the classpath baseline per namespace, incrementally
        //
        // Since discovery became stitch-driven, namespaces may appear at any stitch pass (late mod registrations).
        // The stitch (TextureMap listener) runs before this listener, so a newly discovered namespace's loaded* data
        // is still pure classpath here (overlays are applied only in step 3 below), making a snapshot safe at this
        // point; a one-shot full snapshot would instead let the "restore baseline" step wipe late namespaces entirely.
        if (baselineBlockstates == null) {
            baselineBlockstates = new HashMap<>();
            baselineItemStates = new HashMap<>();
            baselineOversizedItems = new HashMap<>();
            baselineMappings = new HashMap<>();
        }
        for (String ns : ModelManagerDataLoader.loadedNamespaces) {
            if (baselineBlockstates.containsKey(ns))
                continue;
            Map<String, BlockstateJson> bs = ModelManagerDataLoader.loadedBlockstates.get(ns);
            baselineBlockstates.put(ns, bs != null ? new HashMap<>(bs) : new HashMap<>());
            Map<String, ItemStateNode> is = ModelManagerDataLoader.loadedItemStates.get(ns);
            if (is != null) {
                baselineItemStates.put(ns, new HashMap<>(is));
            }
            Set<String> ov = ModelManagerDataLoader.loadedOversizedItems.get(ns);
            if (ov != null) {
                baselineOversizedItems.put(ns, new HashSet<>(ov));
            }
            baselineMappings.put(ns, copyMappings(ModelManagerDataLoader.loadedMappings.get(ns)));
        }

        // 2. Restore the baseline so removed packs revert cleanly
        ModelManagerDataLoader.loadedBlockstates.clear();
        for (Map.Entry<String, Map<String, BlockstateJson>> e : baselineBlockstates.entrySet()) {
            ModelManagerDataLoader.loadedBlockstates.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        ModelManagerDataLoader.loadedItemStates.clear();
        for (Map.Entry<String, Map<String, ItemStateNode>> e : baselineItemStates.entrySet()) {
            ModelManagerDataLoader.loadedItemStates.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        ModelManagerDataLoader.loadedOversizedItems.clear();
        for (Map.Entry<String, Set<String>> e : baselineOversizedItems.entrySet()) {
            ModelManagerDataLoader.loadedOversizedItems.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        // Restore mappings; skip null baselines (namespaces without a jar file must not inject a null entry into
        // the registration flow)
        ModelManagerDataLoader.loadedMappings.clear();
        for (Map.Entry<String, VanillaModelManager.ModelMappings> e : baselineMappings.entrySet()) {
            if (e.getValue() != null) {
                ModelManagerDataLoader.loadedMappings.put(e.getKey(), e.getValue());
            }
        }

        // 3. Overlay overrides
        for (Map.Entry<String, BlockstateJson> e : PACK_BLOCKSTATES.entrySet()) {
            String key = e.getKey();
            int sep = key.indexOf(':');
            String ns = key.substring(0, sep);
            String name = key.substring(sep + 1);
            ModelManagerDataLoader.loadedBlockstates
                    .computeIfAbsent(ns, k -> new HashMap<>())
                    .put(name, e.getValue());
        }
        for (Map.Entry<String, ItemStateRoot> e : PACK_ITEM_STATES.entrySet()) {
            String key = e.getKey();
            int sep = key.indexOf(':');
            String ns = key.substring(0, sep);
            String name = key.substring(sep + 1);
            ItemStateRoot root = e.getValue();
            ModelManagerDataLoader.loadedItemStates
                    .computeIfAbsent(ns, k -> new HashMap<>())
                    .put(name, root.model);
            // Sync the root-level oversized_in_gui (false removes any stale baseline flag)
            // Sync root-level oversized_in_gui (false removes any stale baseline flag)
            Set<String> oversized = ModelManagerDataLoader.loadedOversizedItems
                    .computeIfAbsent(ns, k -> new HashSet<>());
            if (root.oversizedInGui) {
                oversized.add(name);
            } else {
                oversized.remove(name);
            }
        }
        // model_mappings overrides: whole-file replacement (the pack file is authoritative)
        for (Map.Entry<String, VanillaModelManager.ModelMappings> e : PACK_MAPPINGS.entrySet()) {
            ModelManagerDataLoader.loadedMappings.put(e.getKey(), e.getValue());
        }

        // 4. Conditional re-registration
        boolean anyBlockNow = !PACK_BLOCKSTATES.isEmpty();
        boolean anyItemNow = !PACK_ITEM_STATES.isEmpty();
        boolean anyMappingNow = !PACK_MAPPINGS.isEmpty();
        // Atlas-ready check: the immediate callback on registration happens before stitching
        boolean atlasReady = !VanillaTextureTracker.textureIcons.isEmpty();
        if (atlasReady) {
            if (anyBlockNow || blockOverridesWereActive
                    || anyMappingNow || mappingsOverridesWereActive) {
                // Blockstate/mapping overrides appeared/vanished → full re-registration (covers items too;
                // mappings may change the block side)
                VanillaModelManager.Baking.registerAllModels();
            } else if (anyItemNow || itemOverridesWereActive) {
                // Only ItemState overrides changed → incremental item wrapper rebuild
                VanillaModelManager.Baking.registerItemModels();
            }
        }
        blockOverridesWereActive = anyBlockNow;
        itemOverridesWereActive = anyItemNow;
        mappingsOverridesWereActive = anyMappingNow;
    }

    // ==================== Registration ====================

    /**
     * Registers this listener with the Minecraft resource manager.
     * Safe to call from mod init - if the resource manager is not ready, registration is deferred via a one-shot Tick.
     */
    public static void register() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getResourceManager() instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) mc.getResourceManager())
                    .registerReloadListener(new ResourcePackModelDetector());
            CatFrame.logger.info("ResourcePackModelDetector: registered with resource manager");
        } else {
            CatFrame.logger.debug("ResourcePackModelDetector: resource manager not ready, deferring");
            MinecraftForge.EVENT_BUS.register(new Object() {
                @SubscribeEvent
                public void onClientTick(TickEvent.ClientTickEvent event) {
                    if (event.phase == TickEvent.Phase.END) {
                        Minecraft mc2 = Minecraft.getMinecraft();
                        if (mc2 != null && mc2.getResourceManager() instanceof IReloadableResourceManager) {
                            ((IReloadableResourceManager) mc2.getResourceManager())
                                    .registerReloadListener(new ResourcePackModelDetector());
                            CatFrame.logger.info("ResourcePackModelDetector: registered (deferred)");
                            MinecraftForge.EVENT_BUS.unregister(this);
                        }
                    }
                }
            });
        }
    }

    // ==================== Query API ====================

    /** Does the top resource pack override the given model? */
    public static boolean hasModelOverride(String ns, String path) {
        return PACK_MODEL_PATHS.contains(ns + ":" + path);
    }

    /** Does the top resource pack override the given blockstate? */
    public static boolean hasBlockstateOverride(String ns, String blockName) {
        return PACK_BLOCKSTATE_PATHS.contains(ns + ":" + blockName);
    }

    /** Gets the top resource pack's model JSON, or null */
    public static ModelJson getTopModel(String ns, String path) {
        return PACK_MODELS.get(ns + ":" + path);
    }

    /** Gets the top resource pack's Blockstate JSON, or null */
    public static BlockstateJson getTopBlockstate(String ns, String blockName) {
        return PACK_BLOCKSTATES.get(ns + ":" + blockName);
    }

    /** Gets all overridden model paths (immutable view, for debugging) */
    public static Set<String> getOverriddenModelPaths() {
        return Collections.unmodifiableSet(PACK_MODEL_PATHS);
    }

    /** Gets all overridden blockstate paths (immutable view, for debugging) */
    public static Set<String> getOverriddenBlockstatePaths() {
        return Collections.unmodifiableSet(PACK_BLOCKSTATE_PATHS);
    }

    /** Does the top resource pack override the model_mappings of the given namespace? */
    public static boolean hasMappingOverride(String ns) {
        return PACK_MAPPINGS.containsKey(ns);
    }

    /** Gets the top resource pack's model_mappings, or null */
    public static VanillaModelManager.ModelMappings getTopMappings(String ns) {
        return PACK_MAPPINGS.get(ns);
    }
}
