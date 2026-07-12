package decok.dfcdvadstf.catframe.model;

import akka.dispatch.Futures;
import com.google.gson.Gson;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.core.NamespaceLoadResult;
import decok.dfcdvadstf.catframe.model.core.NamespaceLoadTask;
import decok.dfcdvadstf.catframe.model.impl.ModernItem;
import decok.dfcdvadstf.catframe.model.render.RenderJsonBlockModel;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * 数据加载：namespace 发现、blockstate 加载、model_mappings 加载。
 * <p>
 * 从 {@link VanillaModelManager.DataLoading} 提取，职责不变。
 */
public class ModelManagerDataLoader {

    public static final Gson blockstateGson = BlockstateJson.createGson();

    /** Cache for redirect target blockstates loaded during init. */
    public static final Map<String, BlockstateJson> cachedRedirectBlockstates = new HashMap<>();

    // ==================== 共享注册表（从 VanillaModelManager 迁入） ====================

    public static boolean initialized = false;
    public static final List<String> namespaces = new ArrayList<>();
    public static final Map<String, Map<String, BlockstateJson>> loadedBlockstates = new HashMap<>();
    public static final Map<String, VanillaModelManager.ModelMappings> loadedMappings = new HashMap<>();
    static final Map<String, Map<String, Map<Integer, Map<String, String>>>> loadedMetadataMaps = new HashMap<>();
    static final List<Block> registeredStateBlocks = new ArrayList<>();
    public static final Map<Block, BlockstateJson> stateBlockData = new HashMap<>();
    static final Map<Block, IMetadataMapper> metadataMappers = new HashMap<>();
    static final Map<Block, IMetadataBlockstateRedirect> blockstateRedirects = new HashMap<>();
    public static final Map<Item, IItemStateProvider> interfaceItemStates = new LinkedHashMap<>();
    public static final Map<String, Map<String, ItemStateNode>> loadedItemStates = new HashMap<>();

    // ==================== 初始化 ====================

    /**
     * Initialize the model manager. Call during preInit.
     * Scans all registered namespaces for blockstates and model_mappings.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        // Always include minecraft namespace
        registerNamespace("minecraft");

        CatFrame.logger.info("VanillaModelManager: Initializing...");

        // ===== 并行加载所有 namespace（Akka Futures） =====
        long t0 = System.nanoTime();
        List<NamespaceLoadResult> results = loadNamespacesParallel();
        long t1 = System.nanoTime();

        // ===== 主线程合并结果到共享字段 =====
        for (NamespaceLoadResult result : results) {
            if (result.mappings != null) {
                loadedMappings.put(result.namespace, result.mappings);
            }
            loadedBlockstates.put(result.namespace, result.blockstates);
            if (!result.metadataMaps.isEmpty()) {
                loadedMetadataMaps.put(result.namespace, result.metadataMaps);
            }
            if (!result.itemStates.isEmpty()) {
                loadedItemStates.put(result.namespace, result.itemStates);
            }
            // 合并纹理收集结果
            VanillaTextureTracker.pendingTextures.addAll(result.blockTextures);
            VanillaTextureTracker.pendingItemTextures.addAll(result.itemTextures);
        }
        long t2 = System.nanoTime();
        CatFrame.logger.info("[VMM] namespace parallel load: {} namespaces in {:.1f}ms, merge in {:.1f}ms",
                results.size(), (t1 - t0) / 1e6, (t2 - t1) / 1e6);

        // Load blockstates for all registered IBlockStateProvider blocks
        for (Block block : registeredStateBlocks) {
            loadStateProviderBlock(block);
        }

        // Scan Item.itemRegistry for IItemState implementations (Tier 3 discovery)
        for (Object obj : Item.itemRegistry) {
            if (obj instanceof IItemStateProvider) {
                IItemStateProvider is = (IItemStateProvider) obj;
                if (!is.shouldHandle()) continue;  // explicitly opt out
                Item item = (Item) obj;
                // 纹理收集：ModernItem 提供 getModelPath() / getHandModelPath()
                if (obj instanceof ModernItem) {
                    ModernItem mi = (ModernItem) obj;
                    String modelPath = mi.getModelPath();
                    if (modelPath != null) {
                        VanillaTextureTracker.collectTexturesFromModel(modelPath, true);
                        if (mi.hasDualModels()) {
                            VanillaTextureTracker.collectTexturesFromModel(mi.getHandModelPath(), true);
                        }
                    }
                }
                interfaceItemStates.put(item, is);
                CatFrame.logger.debug("[VMM] IItemState discovered: {}",
                        Item.itemRegistry.getNameForObject(item));
            }
        }
        if (!interfaceItemStates.isEmpty()) {
            CatFrame.logger.info("VanillaModelManager: Discovered {} IItemState items",
                    interfaceItemStates.size());
        }

        // Pre-load redirect target blockstates so their textures are collected into pendingTextures
        // BEFORE TextureStitchEvent.Pre (which registers them in the atlas)
        for (Map.Entry<Block, IMetadataBlockstateRedirect> entry : blockstateRedirects.entrySet()) {
            Block block = entry.getKey();
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

        CatFrame.logger.info("VanillaModelManager: Loaded {} namespaces, {} state-blocks, {} block-textures pending, {} item-textures pending",
                namespaces.size(), registeredStateBlocks.size(),
                VanillaTextureTracker.pendingTextures.size(), VanillaTextureTracker.pendingItemTextures.size());
    }

    /**
     * Register a namespace for model loading.
     * Other mods should call this in preInit to participate.
     */
    public static void registerNamespace(String namespace) {
        if (!namespaces.contains(namespace)) {
            namespaces.add(namespace);
            ModelResolver.registerNamespace(namespace);
        }
    }

    /**
     * Register a block that implements IBlockStateProvider for blockstate-driven rendering.
     * Call this during preInit (before init() completes).
     */
    public static void registerBlock(Block block) {
        if (!(block instanceof IBlockStateProvider)) {
            throw new IllegalArgumentException("Block must implement IBlockStateProvider: " + block.getClass().getName());
        }
        if (!registeredStateBlocks.contains(block)) {
            registeredStateBlocks.add(block);
            String ns = ((IBlockStateProvider) block).getBlockstateNamespace();
            registerNamespace(ns);

            if (initialized) {
                loadStateProviderBlock(block);
            }
        }
    }

    /**
     * Register a metadata-to-properties mapper for a vanilla block.
     */
    public static void registerMetadataMapping(Block block, IMetadataMapper mapper) {
        if (mapper == null) {
            CatFrame.logger.warn("VanillaModelManager: registerMetadataMapping called with null mapper for {}", block);
            return;
        }
        if (!metadataMappers.containsKey(block)) {
            metadataMappers.put(block, mapper);
            CatFrame.logger.debug("VanillaModelManager: registered metadata mapper for {}", block);
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

    // ==================== 并行加载 ====================

    /**
     * 使用 Akka Futures 并行加载所有已注册的 namespace。
     * <p>
     * 每个 namespace 的加载由 {@link NamespaceLoadTask#execute(String)} 在独立线程中执行，
     * 所有结果收集到本地集合中，不触碰共享静态字段。
     *
     * @return 所有 namespace 的加载结果列表
     */
    private static List<NamespaceLoadResult> loadNamespacesParallel() {
        List<String> nsList = new ArrayList<>(ModelManagerDataLoader.namespaces);
        if (nsList.isEmpty()) return new ArrayList<>();

        // 单 namespace 时直接同步执行，避免 Future 开销
        if (nsList.size() == 1) {
            List<NamespaceLoadResult> results = new ArrayList<>();
            results.add(NamespaceLoadTask.execute(nsList.get(0)));
            return results;
        }

        // 多 namespace 并行：使用 Akka ActorSystem 的 dispatcher 作为 ExecutionContext
        scala.concurrent.ExecutionContext ec =
                akka.actor.ActorSystem.create("VMM-Loader")
                        .dispatcher();

        List<Future<NamespaceLoadResult>> futures = new ArrayList<>();
        for (final String namespace : namespaces) {
            Future<NamespaceLoadResult> f = Futures.future(
                    new Callable<NamespaceLoadResult>() {
                        @Override
                        public NamespaceLoadResult call() {
                            return NamespaceLoadTask.execute(namespace);
                        }
                    }, ec);
            futures.add(f);
        }

        // 等待所有 Future 完成（最多 30 秒）
        List<NamespaceLoadResult> results = new ArrayList<>();
        for (Future<NamespaceLoadResult> f : futures) {
            try {
                results.add(Await.result(f, Duration.create(30, "seconds")));
            } catch (Exception e) {
                CatFrame.logger.error("[VMM] namespace load failed: {}", e.getMessage());
            }
        }
        return results;
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

    // ==================== 内部加载方法 ====================

    /**
     * Load blockstate data for a registered IBlockStateProvider block.
     */
    private static void loadStateProviderBlock(Block block) {
        IBlockStateProvider provider = (IBlockStateProvider) block;
        String namespace = provider.getBlockstateNamespace();
        String name = provider.getBlockstateName();

        BlockstateJson bs = loadSingleBlockstate(namespace, name);
        if (bs != null) {
            stateBlockData.put(block, bs);
            CatFrame.logger.info("Loaded blockstate for state-block: {}:{}", namespace, name);
        } else {
            CatFrame.logger.warn("Failed to load blockstate for state-block: {}:{}", namespace, name);
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
