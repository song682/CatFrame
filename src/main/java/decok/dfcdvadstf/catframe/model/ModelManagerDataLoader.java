package decok.dfcdvadstf.catframe.model;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.core.NamespaceLoadResult;
import decok.dfcdvadstf.catframe.model.core.NamespaceLoadTask;
import decok.dfcdvadstf.catframe.model.core.async.RenderExecutors;
import decok.dfcdvadstf.catframe.model.render.RenderJsonBlockModel;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.BlockstateKeyValidator;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateRoot;
import decok.dfcdvadstf.catframe.model.state.property.ItemPropertyRegistry;
import decok.dfcdvadstf.catframe.model.state.property.ItemPropertyProvider;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Data loading: namespace discovery, blockstate loading, model_mappings loading.
 * <p>
 * Extracted from {@link VanillaModelManager.DataLoading}, responsibilities unchanged.
 */
public class ModelManagerDataLoader {

    public static final Gson blockstateGson = BlockstateJson.createGson();

    /** Plain Gson for items/ ItemState JSON (root fields parsed via {@link ItemStateNode#parseRootFull}).
     *  Plain Gson for items/ ItemState JSON (root fields parsed via {@link ItemStateNode#parseRootFull}). */
    private static final Gson itemStateGson = new Gson();

    /** Cache for redirect target blockstates loaded during init. */
    public static final Map<String, BlockstateJson> cachedRedirectBlockstates = new HashMap<>();

    // ==================== Shared registry (migrated from VanillaModelManager) ====================

    public static boolean initialized = false;
    public static final List<String> namespaces = new ArrayList<>();
    /** Namespaces whose classpath scan already completed (dedup basis for incremental discovery).
     *  Namespaces whose classpath scan already completed (dedup basis for incremental discovery). */
    public static final Set<String> loadedNamespaces = new LinkedHashSet<>();
    /** State-provider blocks already attempted, so stitch passes never retry or re-warn.
     *  State-provider blocks already attempted, so stitch passes never retry or re-warn. */
    private static final Set<Block> attemptedStateBlocks = new HashSet<>();
    /** State-provider items whose ItemState JSON was already attempted — never retried.
     *  State-provider items whose ItemState JSON was already attempted — never retried. */
    private static final Set<Item> attemptedStateItems = new HashSet<>();
    /** item → declared property keys already attempted (attempted-once dedup, so stitch re-scans neither re-register nor repeat warnings).
     *  item → declared property keys already attempted (attempted-once dedup, so stitch
     *  re-scans neither re-register nor repeat warnings). */
    private static final Map<Item, Set<String>> registeredDeclaredProps = new HashMap<>();
    /** Blocks whose redirect targets were already preloaded. */
    private static final Set<Block> preloadedRedirectBlocks = new HashSet<>();
    public static final Map<String, Map<String, BlockstateJson>> loadedBlockstates = new HashMap<>();
    public static final Map<String, VanillaModelManager.ModelMappings> loadedMappings = new HashMap<>();
    static final List<Block> registeredStateBlocks = new ArrayList<>();
    public static final Map<Block, BlockstateJson> stateBlockData = new ConcurrentHashMap<>();
    static final Map<Block, IMetadataBlockstateRedirect> blockstateRedirects = new HashMap<>();
    public static final Map<Item, IItemStateProvider> interfaceItemStates = new LinkedHashMap<>();
    public static final Map<String, Map<String, ItemStateNode>> loadedItemStates = new ConcurrentHashMap<>();
    /** namespace → set of item names declared with {@code oversized_in_gui=true}. */
    public static final Map<String, Set<String>> loadedOversizedItems = new HashMap<>();

    // ==================== Initialization ====================

    /**
     * Incremental discovery entry point, invoked from {@code TexturesStitch} on every
     * block-atlas {@code TextureStitchEvent.Pre}. The first stitch fires after ALL mods'
     * preInit (the sync point hard-wired in Minecraft.startGame), the second one comes from
     * refreshResources after init/postInit — late registrations get picked up there.
     * <p>
     * Incremental discovery entry point, invoked from {@code TexturesStitch} on every
     * block-atlas {@code TextureStitchEvent.Pre}. The first stitch fires after ALL mods'
     * preInit (the sync point hard-wired in Minecraft.startGame), the second one comes from
     * refreshResources after init/postInit — late registrations get picked up there.
     * <p>
     * Opt-in is derived from the registries: a registered block/item implementing
     * {@link IBlockStateProvider} / {@link IItemStateProvider} IS the declaration
     * (resource packs cannot forge an {@code instanceof}); {@code minecraft} is the
     * only blanket namespace.
     * Opt-in is derived from the registries: a registered block/item implementing
     * {@link IBlockStateProvider} / {@link IItemStateProvider} IS the declaration
     * (resource packs cannot forge an {@code instanceof}); {@code minecraft} is the
     * only blanket namespace.
     */
    public static void init() {
        boolean firstPass = !initialized;
        if (firstPass) {
            // Always include minecraft namespace — vanilla takeover is CatFrame's own job
            // minecraft always included — vanilla takeover is CatFrame's own job
            registerNamespace("minecraft");
            CatFrame.logger.info("VanillaModelManager: Initializing at first texture stitch...");
        }

        // ===== Registry-derived opt-in =====
        deriveNamespacesFromRegistries();

        // ===== Parallel load newly added namespaces this pass (incremental) =====
        // ===== Parallel-load namespaces new to this pass (incremental) =====
        List<String> pendingNs = new ArrayList<>();
        for (String ns : namespaces) {
            if (!loadedNamespaces.contains(ns)) pendingNs.add(ns);
        }
        if (!pendingNs.isEmpty()) {
            long t0 = System.nanoTime();
            List<NamespaceLoadResult> results = loadNamespacesParallel(pendingNs);
            long t1 = System.nanoTime();

            // ===== Main thread merges results into shared fields =====
            for (NamespaceLoadResult result : results) {
                if (result.mappings != null) {
                    loadedMappings.put(result.namespace, result.mappings);
                }
                loadedBlockstates.put(result.namespace, result.blockstates);
                if (!result.itemStates.isEmpty()) {
                    loadedItemStates.put(result.namespace, result.itemStates);
                }
                if (result.oversizedItems != null && !result.oversizedItems.isEmpty()) {
                    loadedOversizedItems.put(result.namespace, result.oversizedItems);
                }
                // Merge texture collection results
                VanillaTextureTracker.pendingTextures.addAll(result.blockTextures);
                VanillaTextureTracker.pendingItemTextures.addAll(result.itemTextures);
                // Record in loaded set; namespaces lost during fallback retry automatically next stitch
                // Mark as loaded; namespaces dropped by the fallback path retry next stitch
                loadedNamespaces.add(result.namespace);
            }
            long t2 = System.nanoTime();
            CatFrame.logger.info("[VMM] namespace parallel load: {} namespaces in {:.1f}ms, merge in {:.1f}ms",
                    results.size(), (t1 - t0) / 1e6, (t2 - t1) / 1e6);
        }

        // Load blockstates for registered IBlockStateProvider blocks
        // (loadStateProviderBlock skips blocks already attempted, so this is incremental)
        // Load blockstate for registered IBlockStateProvider blocks (skips attempted blocks, naturally incremental)
        for (Block block : new ArrayList<>(registeredStateBlocks)) {
            loadStateProviderBlock(block);
        }

        // Scan Item.itemRegistry for IItemState implementations (Tier 3 discovery, incremental)
        // Interface implementation is opt-in: registry rescanned every stitch — late registrations auto-pickup next pass
        // Implementation as declaration: the registry is re-scanned on every stitch —
        // late registrations are picked up on the next pass
        for (Object obj : Item.itemRegistry) {
            if (obj instanceof IItemStateProvider) {
                IItemStateProvider is = (IItemStateProvider) obj;
                if (!is.shouldHandle()) continue;  // explicitly opt out
                Item item = (Item) obj;
                // Interface-driven texture collection: reruns unconditionally every pass (idempotent sets), late declarations auto-pickup
                // Interface-driven texture collection: rerun every pass (idempotent sets),
                // so late declarations are picked up on the next stitch
                for (String modelPath : is.getDeclaredModelPaths()) {
                    VanillaTextureTracker.collectTexturesFromModel(modelPath, true);
                }
                if (interfaceItemStates.put(item, is) == null) {
                    CatFrame.logger.debug("[VMM] IItemState discovered: {}",
                            Item.itemRegistry.getNameForObject(item));
                }
                // Interface-based property declaration: mirrors block-side getStateDefinition() optional hook,
                // Custom properties declared by implementors auto-registered at discovery (attempted-once dedup)
                // Interface-based property declaration: mirrors the block-side
                // getStateDefinition() optional hook — declared custom properties
                // are auto-registered at discovery time (attempted-once dedup)
                registerDeclaredProperties(item, is);
                // Implementation as opt-in: ItemState JSON — explicit declaration wins, fallback derives from registry name,
                // Resolved decision tree bound by registry name (Baking 4a looks up by registry name)
                // Implementation as declaration: ItemState JSON — explicit declarations
                // win, missing parts fall back to the registry name; the parsed tree
                // binds under the registry name (Baking 4a looks items up by registry name)
                loadStateProviderItem(item, is);
            }
        }
        if (!interfaceItemStates.isEmpty()) {
            CatFrame.logger.info("VanillaModelManager: Discovered {} IItemState items",
                    interfaceItemStates.size());
        }

        // Pre-load redirect target blockstates so their textures land in pendingTextures
        // before this stitch registers them in the atlas (incremental: once per block)
        // Preload redirect target blockstate; textures enter pendingTextures before this pass's stitch registration (once per block)
        for (Map.Entry<Block, IMetadataBlockstateRedirect> entry : blockstateRedirects.entrySet()) {
            Block block = entry.getKey();
            if (!preloadedRedirectBlocks.add(block)) continue;
            IMetadataBlockstateRedirect redirect = entry.getValue();
            String blockId = Block.blockRegistry.getNameForObject(block);
            String ns = blockId.contains(":") ? blockId.substring(0, blockId.indexOf(':')) : "minecraft";
            for (int meta = 0; meta < 16; meta++) {
                String targetName = redirect.redirect(meta);
                if (targetName != null) {
                    // loadSingleBlockstate collects textures via collectTexturesFromBlockstate()
                    BlockstateJson targetBs = loadSingleBlockstate(ns, targetName);
                    if (targetBs != null) {
                        String cacheKey = ns + ":" + targetName;
                        cachedRedirectBlockstates.put(cacheKey, targetBs);
                    }
                }
            }
        }

        if (firstPass) {
            initialized = true;
        }
        if (firstPass || !pendingNs.isEmpty()) {
            CatFrame.logger.info("VanillaModelManager: Loaded {} namespaces, {} state-blocks, {} block-textures pending, {} item-textures pending",
                    loadedNamespaces.size(), registeredStateBlocks.size(),
                    VanillaTextureTracker.pendingTextures.size(), VanillaTextureTracker.pendingItemTextures.size());
        }
    }

/**
     * Derive participating namespaces from the game registries — the high-version model:
     * registering a block/item that implements the provider interface is itself the
     * "I use CatFrame" declaration, so no manual namespace call is needed.
     */
    private static void deriveNamespacesFromRegistries() {
        for (Object obj : Block.blockRegistry) {
            if (obj instanceof IBlockStateProvider && obj instanceof Block) {
                Block block = (Block) obj;
                if (!registeredStateBlocks.contains(block)) {
                    registeredStateBlocks.add(block);
// Inheritance as declaration: fall back to block's registry name when namespace not explicit
                    // Inheritance as declaration: fall back to the block's registry
                    // name when the namespace is not declared explicitly
                    String[] path = resolveBlockstatePath(block);
                    String ns = path != null
                            ? path[0] : ((IBlockStateProvider) block).getBlockstateNamespace();
                    if (ns != null && !ns.isEmpty()) {
                        registerNamespace(ns);
                    }
                }
            }
        }
        for (Object obj : Item.itemRegistry) {
            if (obj instanceof IItemStateProvider && ((IItemStateProvider) obj).shouldHandle()) {
                IItemStateProvider provider = (IItemStateProvider) obj;
                // Implementation as declaration: fall back to item's registry name when namespace not explicit
                // Implementation as declaration: fall back to the item's registry
                // name when the namespace is not declared explicitly
                String[] path = resolveItemStatePath((Item) obj, provider);
                String ns = path != null ? path[0] : provider.getItemStateNamespace();
                if (ns != null && !ns.isEmpty()) {
                    registerNamespace(ns);
                }
            }
        }
    }

/**
     * Registers the custom properties declared via
     * {@link IItemStateProvider#getPropertyDefinitions()}. Defensive: a single bad
     * declaration (bare name / empty key / null provider) is skipped with a warning
     * instead of aborting discovery; valid entries go through
     * {@link ItemPropertyRegistry#register} (namespace enforcement + defaults-first).
     * <p>
     * Attempted-once: each key is processed at most once (an invalid key warns a
     * single time), so stitch re-scans neither re-register nor repeat warnings.
     */
    private static void registerDeclaredProperties(Item item, IItemStateProvider provider) {
        Map<String, ItemPropertyProvider> declarations = provider.getPropertyDefinitions();
        if (declarations == null || declarations.isEmpty()) return;

        Set<String> attempted = registeredDeclaredProps.computeIfAbsent(item, k -> new HashSet<>());
        String itemId = Item.itemRegistry.getNameForObject(item);
        for (Map.Entry<String, ItemPropertyProvider> entry : declarations.entrySet()) {
            String key = entry.getKey();
            if (!attempted.add(key)) continue;
            int colon = key != null ? key.indexOf(':') : -1;
            // Require full modid:name form, both parts non-empty
            // Require the full modid:name form with both parts non-empty
            if (colon <= 0 || colon >= key.length() - 1) {
                CatFrame.logger.warn("[VMM] Item {} declared property '{}' without a valid 'modid:name' key, skipped",
                        itemId, key);
                continue;
            }
            try {
                ItemPropertyRegistry.register(key.substring(0, colon), key.substring(colon + 1), entry.getValue());
                CatFrame.logger.debug("[VMM] Item {} declared property '{}' registered", itemId, key);
            } catch (IllegalArgumentException e) {
                CatFrame.logger.warn("[VMM] Item {} declared invalid property '{}': {}", itemId, key, e.getMessage());
            }
        }
    }

/**
     * Register a namespace for model loading.
     * <p>
     * Since discovery moved to the texture-stitch sync point, mods whose blocks/items
     * implement the provider interfaces no longer need this call (namespaces are derived
     * from the registries). It remains useful for reference-only namespaces — assets
     * referenced cross-namespace without any registered object behind them.
     */
    public static void registerNamespace(String namespace) {
        if (!namespaces.contains(namespace)) {
            namespaces.add(namespace);
            ModelResolver.registerNamespace(namespace);
        }
    }

    /**
     * Register a block that implements IBlockStateProvider for blockstate-driven rendering.
     * <p>
     * Timing is no longer a constraint: blocks present in the registry are auto-discovered
     * at each texture stitch; calling this merely front-loads the bookkeeping (and loads
     * immediately once the first discovery pass has completed).
     * Registration timing relaxed: blocks in registry auto-discovered at every texture stitch;
     * manual call is just early registration (immediate load if called after first discovery pass).
     */
    public static void registerBlock(Block block) {
        if (!(block instanceof IBlockStateProvider)) {
            throw new IllegalArgumentException("Block must implement IBlockStateProvider: " + block.getClass().getName());
        }
        if (!registeredStateBlocks.contains(block)) {
            registeredStateBlocks.add(block);
            // Inheritance as declaration: fall back to block's registry name when namespace not explicit
            // Inheritance as declaration: fall back to the block's registry name
            String[] path = resolveBlockstatePath(block);
            String ns = path != null
                    ? path[0] : ((IBlockStateProvider) block).getBlockstateNamespace();
            if (ns != null && !ns.isEmpty()) {
                registerNamespace(ns);
            }

            if (initialized) {
                loadStateProviderBlock(block);
            }
        }
    }

    /**
     * Register a blockstate redirect for a block.
     * When registered, baking will delegate per-metadata to separate blockstate files.
     */
    public static void registerBlockstateRedirect(Block block, IMetadataBlockstateRedirect redirect) {
        if (redirect == null) {
            CatFrame.logger.warn("VanillaModelManager: registerBlockstateRedirect called with null redirect for {}", block);
            return;
        }
        if (!blockstateRedirects.containsKey(block)) {
            blockstateRedirects.put(block, redirect);
            CatFrame.logger.debug("VanillaModelManager: registered blockstate redirect for {}", block);
        }
    }

    // ==================== Parallel loading ====================

    /**
     * Load specified namespace set in parallel using Guava {@link ListenableFuture}.
     * <p>
     * Each namespace's load runs via {@link NamespaceLoadTask#execute(String)} in shared
     * thread pool; all results collected into local set, never touch shared static fields.
     * <p>
     * Reuses {@link RenderExecutors} shared pool.
     *
     * @param nsList namespaces to load in this pass
     * @return list of load results for all namespaces
     */
    private static List<NamespaceLoadResult> loadNamespacesParallel(List<String> nsList) {
        if (nsList.isEmpty()) return new ArrayList<>();

        // Single namespace: execute synchronously, avoid Future overhead
        if (nsList.size() == 1) {
            List<NamespaceLoadResult> results = new ArrayList<>();
            results.add(NamespaceLoadTask.execute(nsList.get(0)));
            return results;
        }

        // Multiple namespaces parallel: use Guava shared thread pool
        List<ListenableFuture<NamespaceLoadResult>> futures = new ArrayList<>();
        for (final String namespace : nsList) {
            ListenableFuture<NamespaceLoadResult> f = RenderExecutors.get().submit(
                    new Callable<NamespaceLoadResult>() {
                        @Override
                        public NamespaceLoadResult call() {
                            return NamespaceLoadTask.execute(namespace);
                        }
                    });
            futures.add(f);
        }

        // Wait for all Futures (max 30 seconds), preserve original timeout semantics
        try {
            return new ArrayList<>(Futures.allAsList(futures).get(30, TimeUnit.SECONDS));
        } catch (Exception e) {
            CatFrame.logger.error("[VMM] namespace parallel load failed: {}", e.getMessage());
            // Fallback: execute one by one synchronously, collect successful results
            List<NamespaceLoadResult> results = new ArrayList<>();
            for (String namespace : nsList) {
                try {
                    results.add(NamespaceLoadTask.execute(namespace));
                } catch (Exception ex) {
                    CatFrame.logger.error("[VMM] namespace load failed: {}", ex.getMessage());
                }
            }
            return results;
        }
    }

    /**
     * Get the loaded blockstate data for a block.
     * <p>
     * Public accessor for cross-package access (e.g. by
     * {@link RenderJsonBlockModel}).
     *
     * @param block the block instance
     * @return the loaded BlockstateJson, or null if not found
     */
    public static BlockstateJson getBlockstateData(Block block) {
        return stateBlockData.get(block);
    }

    // ==================== Internal loading methods ====================

    /**
     * Resolve provider block's blockstate resource path: explicit declaration wins, missing parts from block's registry name
     * ({@code namespace:name}, no colon → {@code minecraft}).
     * <p>
     * Key part of inheritance-as-declaration: subclass needs no setBlockstate / register, as long as blockstate JSON
     * sits at the standard path matching registry name, it's auto-discovered, loaded, and registered.
     * <p>
     * Resolves the blockstate resource path of a provider block — explicit declarations
     * win, missing parts fall back to the block's registry name ({@code namespace:name};
     * no colon → {@code minecraft}). This is what lets inheritance alone suffice: no
     * setBlockstate / register call as long as the JSON sits at the standard path.
     *
     * @return {@code [namespace, name]}; null when registry name missing and explicit declaration incomplete
     */
    @Nullable
    private static String[] resolveBlockstatePath(Block block) {
        IBlockStateProvider provider = (IBlockStateProvider) block;
        String namespace = provider.getBlockstateNamespace();
        String name = provider.getBlockstateName();
        if (namespace != null && !namespace.isEmpty() && name != null && !name.isEmpty()) {
            return new String[]{namespace, name};
        }
        String blockId = Block.blockRegistry.getNameForObject(block);
        if (blockId == null || blockId.isEmpty()) return null;
        int colon = blockId.indexOf(':');
        String derivedNs = colon > 0 ? blockId.substring(0, colon) : "minecraft";
        String derivedName = colon > 0 ? blockId.substring(colon + 1) : blockId;
        return new String[]{
                namespace == null || namespace.isEmpty() ? derivedNs : namespace,
                name == null || name.isEmpty() ? derivedName : name
        };
    }

    /**
     * Load blockstate data for a registered IBlockStateProvider block.
     * Attempted at most once per block — stitch passes never retry or re-warn.
     * <p>
     * The resource path falls back to the registry name when the provider leaves
     * parts empty (inheritance as declaration).
     */
    private static void loadStateProviderBlock(Block block) {
        if (!attemptedStateBlocks.add(block)) return;
        IBlockStateProvider provider = (IBlockStateProvider) block;
        String[] path = resolveBlockstatePath(block);
        if (path == null) {
            CatFrame.logger.warn("Cannot resolve blockstate path for state-block {}: " +
                    "no registry name and incomplete explicit declaration", block.getClass().getName());
            return;
        }
        String namespace = path[0];
        String name = path[1];

        BlockstateJson bs = loadSingleBlockstate(namespace, name);
        if (bs != null) {
            stateBlockData.put(block, bs);
            // Providers exposing a typed state definition get their variant keys
            // validated right at load time; invalid keys → builtin/missing.
            // Providers exposing typed state definition get variant keys validated at load; invalid keys → builtin/missing.
            BlockstateKeyValidator.validate(bs, provider.getStateDefinition(),
                    "blockstate " + namespace + ":" + name);
            CatFrame.logger.info("Loaded blockstate for state-block: {}:{}", namespace, name);
        } else {
            CatFrame.logger.warn("Failed to load blockstate for state-block: {}:{}", namespace, name);
        }
    }

    /**
     * Resolves the ItemState resource path of a provider item — explicit declarations
     * win, missing parts fall back to the item's registry name ({@code namespace:name};
     * no colon → {@code minecraft}). This is what lets implementation alone suffice:
     * no register call as long as the JSON sits at the standardized path.
     *
     * @return {@code [namespace, name]}; null when registry name missing and explicit declaration incomplete
     */
    @Nullable
    private static String[] resolveItemStatePath(Item item, IItemStateProvider provider) {
        String namespace = provider.getItemStateNamespace();
        String name = provider.getItemStateName();
        if (namespace != null && !namespace.isEmpty() && name != null && !name.isEmpty()) {
            return new String[]{namespace, name};
        }
        String itemId = Item.itemRegistry.getNameForObject(item);
        if (itemId == null || itemId.isEmpty()) return null;
        int colon = itemId.indexOf(':');
        String derivedNs = colon > 0 ? itemId.substring(0, colon) : "minecraft";
        String derivedName = colon > 0 ? itemId.substring(colon + 1) : itemId;
        return new String[]{
                namespace == null || namespace.isEmpty() ? derivedNs : namespace,
                name == null || name.isEmpty() ? derivedName : name
        };
    }

    /**
     * Load the ItemState JSON for a registered IItemStateProvider item
     * ("implementation as declaration"). Attempted at most once per item.
     * <p>
     * The resource path falls back to the registry name when the provider leaves parts
     * empty; the parsed tree binds under the item's <b>registry name</b>, so Baking
     * step 4a ({@code Utilities.findItem}) finds it regardless of where the file was
     * declared. A declared path that differs from the registry name wins for reading
     * the file (explicit over convention), while the binding key stays the registry
     * name. Failure stays silent for derived paths (an interface item without JSON is
     * the normal case — the provider itself renders) and warns only when the
     * declaration was fully explicit.
     * <p>
     * Path defaults derived from registry name; resolved tree bound by "registry name" —
     * Baking 4a looks up by registry name, so explicitly declared cross-namespace files
     * still land on the correct item (explicit declaration only affects read path,
     * not binding key). Derived path load failure is silent (interface items without JSON
     * are normal — provider self-renders), warns only when both path segments are explicit.
     */
    private static void loadStateProviderItem(Item item, IItemStateProvider provider) {
        if (!attemptedStateItems.add(item)) return;
        String[] path = resolveItemStatePath(item, provider);
        if (path == null) return;  // No registry name and explicit declaration incomplete — cannot locate resource
        String declNs = path[0];
        String declName = path[1];
        String declaredNs = provider.getItemStateNamespace();
        String declaredName = provider.getItemStateName();
        boolean explicit = declaredNs != null && !declaredNs.isEmpty()
                && declaredName != null && !declaredName.isEmpty();

        // Binding key = item registry name (Baking 4a looks up by registry name); falls back to declared path when no registry name
        // Binding key = registry name (Baking 4a looks items up by registry name);
        // falls back to the declared path when no registry name exists
        String itemId = Item.itemRegistry.getNameForObject(item);
        String bindNs = declNs;
        String bindName = declName;
        if (itemId != null && !itemId.isEmpty()) {
            int colon = itemId.indexOf(':');
            bindNs = colon > 0 ? itemId.substring(0, colon) : "minecraft";
            bindName = colon > 0 ? itemId.substring(colon + 1) : itemId;
        }

        // Skip when namespace scan already loaded same resource (declared path == binding key)
        // Skip when the namespace scan already loaded this same resource
        if (declNs.equals(bindNs) && declName.equals(bindName)) {
            Map<String, ItemStateNode> nsStates = loadedItemStates.get(bindNs);
            if (nsStates != null && nsStates.containsKey(bindName)) return;
        }

        String resource = "/assets/" + declNs + "/items/" + declName + ".json";
        try (InputStream stream = ModelManagerDataLoader.class.getResourceAsStream(resource)) {
            if (stream == null) {
                if (explicit) {
                    CatFrame.logger.warn("[VMM] Item {} declared ItemState '{}:{}' but {} not found",
                            itemId, declNs, declName, resource);
                }
                return;
            }
            JsonObject json = itemStateGson.fromJson(new InputStreamReader(stream), JsonObject.class);
            ItemStateRoot rootFull = json != null ? ItemStateNode.parseRootFull(json) : null;
            if (rootFull == null || rootFull.model == null) {
                if (explicit) {
                    CatFrame.logger.warn("[VMM] Item {} declared ItemState '{}:{}' but it has no valid model tree",
                            itemId, declNs, declName);
                }
                return;
            }

            loadedItemStates.computeIfAbsent(bindNs, k -> new ConcurrentHashMap<>()).put(bindName, rootFull.model);
            if (rootFull.oversizedInGui) {
                loadedOversizedItems.computeIfAbsent(bindNs, k -> new HashSet<>()).add(bindName);
            } else {
                Set<String> oversized = loadedOversizedItems.get(bindNs);
                if (oversized != null) oversized.remove(bindName);
            }
            // Collect model textures referenced by decision tree (prefix routing, idempotent)
            // Collect the textures referenced by the tree (prefix routing, idempotent)
            Set<String> modelPaths = new LinkedHashSet<>();
            rootFull.model.collectModelPaths(modelPaths);
            for (String modelPath : modelPaths) {
                VanillaTextureTracker.collectTexturesFromModel(modelPath, true);
            }
            CatFrame.logger.info("Loaded ItemState for state-item: {}:{}", bindNs, bindName);
        } catch (Exception e) {
            if (explicit) {
                CatFrame.logger.warn("[VMM] Error loading declared ItemState {}/items/{} for {}: {}",
                        declNs, declName, itemId, e.getMessage());
            } else {
                CatFrame.logger.debug("[VMM] Derived ItemState path {}/items/{} unusable for {}: {}",
                        declNs, declName, itemId, e.getMessage());
            }
        }
    }

    /**
     * Load a single blockstate JSON file.
     */
    public static BlockstateJson loadSingleBlockstate(String namespace, String blockName) {
        String path = "/assets/" + namespace + "/blockstates/" + blockName + ".json";
        try (InputStream stream = ModelManagerDataLoader.class.getResourceAsStream(path)) {
            if (stream == null) return null;
            InputStreamReader reader = new InputStreamReader(stream);
            BlockstateJson bs = blockstateGson.fromJson(reader, BlockstateJson.class);
            if (bs != null) {
                VanillaTextureTracker.collectTexturesFromBlockstate(bs);
                CatFrame.logger.debug("Loaded blockstate: {}/{}", namespace, blockName);
            }
            return bs;
        } catch (Exception e) {
            CatFrame.logger.error("Error loading blockstate {}/{}: {}", namespace, blockName, e.getMessage());
            return null;
        }
    }
}
