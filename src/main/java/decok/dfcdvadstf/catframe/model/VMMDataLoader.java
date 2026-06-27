package decok.dfcdvadstf.catframe.model;

import com.google.gson.Gson;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.ModernItem;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import akka.dispatch.Futures;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 数据加载：namespace 发现、blockstate 加载、model_mappings 加载。
 * <p>
 * 从 {@link VanillaModelManager.DataLoading} 提取，职责不变。
 */
public class VMMDataLoader {

    static final Gson blockstateGson = BlockstateJson.createGson();

    /** Cache for redirect target blockstates loaded during init. */
    static final Map<String, BlockstateJson> cachedRedirectBlockstates = new HashMap<>();

    // ==================== 初始化 ====================

    /**
     * Initialize the model manager. Call during preInit.
     * Scans all registered namespaces for blockstates and model_mappings.
     */
    public static void init() {
        if (VanillaModelManager.initialized) return;
        VanillaModelManager.initialized = true;

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
                VanillaModelManager.loadedMappings.put(result.namespace, result.mappings);
            }
            VanillaModelManager.loadedBlockstates.put(result.namespace, result.blockstates);
            if (!result.metadataMaps.isEmpty()) {
                VanillaModelManager.loadedMetadataMaps.put(result.namespace, result.metadataMaps);
            }
            // 合并纹理收集结果
            VanillaModelManager.pendingTextures.addAll(result.blockTextures);
            VanillaModelManager.pendingItemTextures.addAll(result.itemTextures);
        }
        long t2 = System.nanoTime();
        CatFrame.logger.info("[VMM] namespace parallel load: {} namespaces in {:.1f}ms, merge in {:.1f}ms",
                results.size(), (t1 - t0) / 1e6, (t2 - t1) / 1e6);

        // Load blockstates for all registered IBlockStateProvider blocks
        for (Block block : VanillaModelManager.registeredStateBlocks) {
            loadStateProviderBlock(block);
        }

        // Scan Item.itemRegistry for IItemJsonModel implementations (Tier 2 discovery)
        for (Object obj : Item.itemRegistry) {
            if (obj instanceof IItemJsonModel) {
                IItemJsonModel ijm = (IItemJsonModel) obj;
                if (!ijm.shouldHandle()) continue;  // explicitly opt out
                Item item = (Item) obj;
                String modelPath = ijm.getModelPath();
                if (modelPath != null) {
                    // Auto-collect textures for this model
                    VanillaTextureTracker.collectTexturesFromModel(modelPath, true);
                    // For ModernItem dual-models, also collect hand model textures
                    if (obj instanceof ModernItem) {
                        ModernItem mi = (ModernItem) obj;
                        if (mi.hasDualModels()) {
                            VanillaTextureTracker.collectTexturesFromModel(mi.getHandModelPath(), true);
                        }
                    }
                    VanillaModelManager.interfaceItemModels.put(item, ijm);
                    CatFrame.logger.debug("[VMM] IItemJsonModel discovered: {} -> {}",
                            Item.itemRegistry.getNameForObject(item), modelPath);
                }
            }
        }
        if (!VanillaModelManager.interfaceItemModels.isEmpty()) {
            CatFrame.logger.info("VanillaModelManager: Discovered {} IItemJsonModel items",
                    VanillaModelManager.interfaceItemModels.size());
        }

        // Pre-load redirect target blockstates so their textures are collected into pendingTextures
        // BEFORE TextureStitchEvent.Pre (which registers them in the atlas)
        for (Map.Entry<Block, IMetadataBlockstateRedirect> entry : VanillaModelManager.blockstateRedirects.entrySet()) {
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
                VanillaModelManager.namespaces.size(), VanillaModelManager.registeredStateBlocks.size(),
                VanillaModelManager.pendingTextures.size(), VanillaModelManager.pendingItemTextures.size());
    }

    /**
     * Register a namespace for model loading.
     * Other mods should call this in preInit to participate.
     */
    public static void registerNamespace(String namespace) {
        if (!VanillaModelManager.namespaces.contains(namespace)) {
            VanillaModelManager.namespaces.add(namespace);
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
        if (!VanillaModelManager.registeredStateBlocks.contains(block)) {
            VanillaModelManager.registeredStateBlocks.add(block);
            String ns = ((IBlockStateProvider) block).getBlockstateNamespace();
            registerNamespace(ns);

            if (VanillaModelManager.initialized) {
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
        if (!VanillaModelManager.metadataMappers.containsKey(block)) {
            VanillaModelManager.metadataMappers.put(block, mapper);
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
        if (!VanillaModelManager.blockstateRedirects.containsKey(block)) {
            VanillaModelManager.blockstateRedirects.put(block, redirect);
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
        List<String> namespaces = new ArrayList<>(VanillaModelManager.namespaces);
        if (namespaces.isEmpty()) return new ArrayList<>();

        // 单 namespace 时直接同步执行，避免 Future 开销
        if (namespaces.size() == 1) {
            List<NamespaceLoadResult> results = new ArrayList<>();
            results.add(NamespaceLoadTask.execute(namespaces.get(0)));
            return results;
        }

        // 多 namespace 并行：使用 Akka ActorSystem 的 dispatcher 作为 ExecutionContext
        scala.concurrent.ExecutionContext ec =
                (scala.concurrent.ExecutionContext) akka.actor.ActorSystem.create("VMM-Loader")
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
            VanillaModelManager.stateBlockData.put(block, bs);
            CatFrame.logger.info("Loaded blockstate for state-block: {}:{}", namespace, name);
        } else {
            CatFrame.logger.warn("Failed to load blockstate for state-block: {}:{}", namespace, name);
        }
    }

    /**
     * Load a single blockstate JSON file.
     */
    static BlockstateJson loadSingleBlockstate(String namespace, String blockName) {
        String path = "/assets/" + namespace + "/blockstates/" + blockName + ".json";
        try (InputStream stream = VanillaModelManager.class.getResourceAsStream(path)) {
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
