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
 * 数据加载：namespace 发现、blockstate 加载、model_mappings 加载。
 * <p>
 * 从 {@link VanillaModelManager.DataLoading} 提取，职责不变。
 */
public class ModelManagerDataLoader {

    public static final Gson blockstateGson = BlockstateJson.createGson();

    /** items/ ItemState JSON 的纯 Gson（根级字段经 {@link ItemStateNode#parseRootFull} 解析）。
     *  Plain Gson for items/ ItemState JSON (root fields parsed via {@link ItemStateNode#parseRootFull}). */
    private static final Gson itemStateGson = new Gson();

    /** Cache for redirect target blockstates loaded during init. */
    public static final Map<String, BlockstateJson> cachedRedirectBlockstates = new HashMap<>();

    // ==================== 共享注册表（从 VanillaModelManager 迁入） ====================

    public static boolean initialized = false;
    public static final List<String> namespaces = new ArrayList<>();
    /** 已完成 classpath 扫描的 namespace（增量发现的去重依据）。
     *  Namespaces whose classpath scan already completed (dedup basis for incremental discovery). */
    public static final Set<String> loadedNamespaces = new LinkedHashSet<>();
    /** 已尝试加载 blockstate 的 state-provider 方块，避免每轮缝合重复尝试/告警。
     *  State-provider blocks already attempted, so stitch passes never retry or re-warn. */
    private static final Set<Block> attemptedStateBlocks = new HashSet<>();
    /** 已尝试加载 ItemState JSON 的 state-provider 物品，避免每轮缝合重复尝试。
     *  State-provider items whose ItemState JSON was already attempted — never retried. */
    private static final Set<Item> attemptedStateItems = new HashSet<>();
    /** item → 已尝试注册的声明属性 key 集合（attempted-once 去重，缝合重扫不重复注册/告警）。
     *  item → declared property keys already attempted (attempted-once dedup, so stitch
     *  re-scans neither re-register nor repeat warnings). */
    private static final Map<Item, Set<String>> registeredDeclaredProps = new HashMap<>();
    /** 已完成 redirect 目标预载的方块。 Blocks whose redirect targets were already preloaded. */
    private static final Set<Block> preloadedRedirectBlocks = new HashSet<>();
    public static final Map<String, Map<String, BlockstateJson>> loadedBlockstates = new HashMap<>();
    public static final Map<String, VanillaModelManager.ModelMappings> loadedMappings = new HashMap<>();
    static final List<Block> registeredStateBlocks = new ArrayList<>();
    public static final Map<Block, BlockstateJson> stateBlockData = new ConcurrentHashMap<>();
    static final Map<Block, IMetadataBlockstateRedirect> blockstateRedirects = new HashMap<>();
    public static final Map<Item, IItemStateProvider> interfaceItemStates = new LinkedHashMap<>();
    public static final Map<String, Map<String, ItemStateNode>> loadedItemStates = new ConcurrentHashMap<>();
    /** namespace → 声明 {@code oversized_in_gui=true} 的物品名集合。 */
    public static final Map<String, Set<String>> loadedOversizedItems = new HashMap<>();

    // ==================== 初始化 ====================

    /**
     * Incremental discovery entry point, invoked from {@code TexturesStitch} on every
     * block-atlas {@code TextureStitchEvent.Pre}. The first stitch fires after ALL mods'
     * preInit (the sync point hard-wired in Minecraft.startGame), the second one comes from
     * refreshResources after init/postInit — late registrations get picked up there.
     * <p>
     * 增量发现入口：由 {@code TexturesStitch} 在每次方块图集 {@code TextureStitchEvent.Pre} 调用。
     * 第一次缝合位于全体 mod preInit 之后（Minecraft.startGame 焊死的同步点），
     * 第二次来自 init/postInit 之后的 refreshResources —— 迟到的注册在那里补票。
     * <p>
     * Opt-in is derived from the registries: a registered block/item implementing
     * {@link IBlockStateProvider} / {@link IItemStateProvider} IS the declaration
     * (resource packs cannot forge an {@code instanceof}); {@code minecraft} is the
     * only blanket namespace.
     * 接入信号从注册表推导：注册对象实现接口即声明（资源包伪造不了 instanceof）；
     * {@code minecraft} 是唯一全量特例。
     */
    public static void init() {
        boolean firstPass = !initialized;
        if (firstPass) {
            // Always include minecraft namespace — vanilla takeover is CatFrame's own job
            // minecraft 永远在列 —— 接管原版渲染是 CatFrame 自己的职责
            registerNamespace("minecraft");
            CatFrame.logger.info("VanillaModelManager: Initializing at first texture stitch...");
        }

        // ===== 注册表推导 opt-in / registry-derived opt-in =====
        deriveNamespacesFromRegistries();

        // ===== 并行加载本轮新增的 namespace（增量） =====
        // ===== Parallel-load namespaces new to this pass (incremental) =====
        List<String> pendingNs = new ArrayList<>();
        for (String ns : namespaces) {
            if (!loadedNamespaces.contains(ns)) pendingNs.add(ns);
        }
        if (!pendingNs.isEmpty()) {
            long t0 = System.nanoTime();
            List<NamespaceLoadResult> results = loadNamespacesParallel(pendingNs);
            long t1 = System.nanoTime();

            // ===== 主线程合并结果到共享字段 =====
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
                // 合并纹理收集结果
                VanillaTextureTracker.pendingTextures.addAll(result.blockTextures);
                VanillaTextureTracker.pendingItemTextures.addAll(result.itemTextures);
                // 记入已加载集合；失败回退中丢失的 namespace 下轮缝合自动重试
                // Mark as loaded; namespaces dropped by the fallback path retry next stitch
                loadedNamespaces.add(result.namespace);
            }
            long t2 = System.nanoTime();
            CatFrame.logger.info("[VMM] namespace parallel load: {} namespaces in {:.1f}ms, merge in {:.1f}ms",
                    results.size(), (t1 - t0) / 1e6, (t2 - t1) / 1e6);
        }

        // Load blockstates for registered IBlockStateProvider blocks
        // (loadStateProviderBlock skips blocks already attempted, so this is incremental)
        // 为已登记的 IBlockStateProvider 方块加载 blockstate（内部跳过已尝试的方块，天然增量）
        for (Block block : new ArrayList<>(registeredStateBlocks)) {
            loadStateProviderBlock(block);
        }

        // Scan Item.itemRegistry for IItemState implementations (Tier 3 discovery, incremental)
        // 接口实现即接入：每轮缝合重新扫描注册表 —— 迟到的注册在下一轮自动补票
        // Implementation as declaration: the registry is re-scanned on every stitch —
        // late registrations are picked up on the next pass
        for (Object obj : Item.itemRegistry) {
            if (obj instanceof IItemStateProvider) {
                IItemStateProvider is = (IItemStateProvider) obj;
                if (!is.shouldHandle()) continue;  // explicitly opt out
                Item item = (Item) obj;
                // 接口驱动纹理收集：每轮无条件重跑（集合幂等），迟到的声明自动补票
                // Interface-driven texture collection: rerun every pass (idempotent sets),
                // so late declarations are picked up on the next stitch
                for (String modelPath : is.getDeclaredModelPaths()) {
                    VanillaTextureTracker.collectTexturesFromModel(modelPath, true);
                }
                if (interfaceItemStates.put(item, is) == null) {
                    CatFrame.logger.debug("[VMM] IItemState discovered: {}",
                            Item.itemRegistry.getNameForObject(item));
                }
                // 接口化属性声明：对标方块侧 getStateDefinition() 的可选钩子，
                // 实现类声明的自定义属性在发现时自动注册（attempted-once 去重）
                // Interface-based property declaration: mirrors the block-side
                // getStateDefinition() optional hook — declared custom properties
                // are auto-registered at discovery time (attempted-once dedup)
                registerDeclaredProperties(item, is);
                // 实现即接入：ItemState JSON —— 显式声明优先、缺省从注册名推导，
                // 解析出的决策树按注册名绑定（Baking 4a 按注册名回查）
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
        // 预载 redirect 目标 blockstate，纹理在本轮缝合注册前进入 pendingTextures（每方块一次）
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
     * <p>
     * 从游戏注册表推导参与的命名空间 —— 对标高版本：注册一个实现 provider 接口的
     * 方块/物品，这个动作本身就是「我接入了 CatFrame」，无需再手动登记命名空间。
     */
    private static void deriveNamespacesFromRegistries() {
        for (Object obj : Block.blockRegistry) {
            if (obj instanceof IBlockStateProvider && obj instanceof Block) {
                Block block = (Block) obj;
                if (!registeredStateBlocks.contains(block)) {
                    registeredStateBlocks.add(block);
                    // 继承即接入：命名空间未显式声明时从方块注册名推导
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
                // 实现即接入：命名空间未显式声明时从物品注册名推导
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
     * 注册 {@link IItemStateProvider#getPropertyDefinitions()} 声明的自定义属性。
     * <p>
     * 防御式处理：单个非法声明（裸名 / 空 key / null provider）只跳过并记录警告，
     * 不中断整个发现流程。合法条目经 {@link ItemPropertyRegistry#register} 注册
     * （命名空间强制 + 默认表先行物化）。
     * <p>
     * Registers the custom properties declared via
     * {@link IItemStateProvider#getPropertyDefinitions()}. Defensive: a single bad
     * declaration (bare name / empty key / null provider) is skipped with a warning
     * instead of aborting discovery; valid entries go through
     * {@link ItemPropertyRegistry#register} (namespace enforcement + defaults-first).
     * <p>
     * attempted-once 语义：每个 key 至多处理一次（含非法 key 只告警一次），
     * 缝合轮次重扫不会重复注册或重复告警。
     * <br>Attempted-once: each key is processed at most once (an invalid key warns a
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
            // 要求完整 modid:name 形式，两段均非空
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
     * <p>
     * 发现流程移至纹理缝合同步点后，方块/物品实现了 provider 接口的 mod 不再需要
     * 手动调用（命名空间从注册表推导）；保留给「纯引用型命名空间」——
     * 没有注册对象、但有被跨空间引用的资源。
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
     * 注册时机已放开：注册表中的方块会在每次纹理缝合时被自动发现，手动调用只是提前登记
     * （首轮发现完成后调用则立即加载）。
     */
    public static void registerBlock(Block block) {
        if (!(block instanceof IBlockStateProvider)) {
            throw new IllegalArgumentException("Block must implement IBlockStateProvider: " + block.getClass().getName());
        }
        if (!registeredStateBlocks.contains(block)) {
            registeredStateBlocks.add(block);
            // 继承即接入：命名空间未显式声明时从方块注册名推导
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

    // ==================== 并行加载 ====================

    /**
     * 使用 Guava {@link ListenableFuture} 并行加载指定的 namespace 集合。
     * <p>
     * 每个 namespace 的加载由 {@link NamespaceLoadTask#execute(String)} 在共享线程池中执行，
     * 所有结果收集到本地集合中，不触碰共享静态字段。
     * <p>
     * 复用 {@link RenderExecutors} 共享池。
     *
     * @param nsList 本轮需要加载的 namespace 列表 / namespaces to load in this pass
     * @return 所有 namespace 的加载结果列表
     */
    private static List<NamespaceLoadResult> loadNamespacesParallel(List<String> nsList) {
        if (nsList.isEmpty()) return new ArrayList<>();

        // 单 namespace 时直接同步执行，避免 Future 开销
        if (nsList.size() == 1) {
            List<NamespaceLoadResult> results = new ArrayList<>();
            results.add(NamespaceLoadTask.execute(nsList.get(0)));
            return results;
        }

        // 多 namespace 并行：使用 Guava 共享线程池
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

        // 等待所有 Future 完成（最多 30 秒），保持原超时语义
        try {
            return new ArrayList<>(Futures.allAsList(futures).get(30, TimeUnit.SECONDS));
        } catch (Exception e) {
            CatFrame.logger.error("[VMM] namespace parallel load failed: {}", e.getMessage());
            // 回退：逐个同步执行，尽量收集成功结果
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

    // ==================== 内部加载方法 ====================

    /**
     * 解析 provider 方块的 blockstate 资源路径：显式声明优先，缺失部分从方块注册名
     * （{@code namespace:name}，无冒号 → {@code minecraft}）推导。
     * <p>
     * 继承即接入的关键一环：子类无需 setBlockstate / register，只要 blockstate JSON
     * 位于注册名对应的标准路径，即可被自动发现、加载与注册。
     * <p>
     * Resolves the blockstate resource path of a provider block — explicit declarations
     * win, missing parts fall back to the block's registry name ({@code namespace:name};
     * no colon → {@code minecraft}). This is what lets inheritance alone suffice: no
     * setBlockstate / register call as long as the JSON sits at the standard path.
     *
     * @return {@code [namespace, name]}；注册名缺失且显式声明不完整时返回 {@code null}
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
     * 每个方块至多尝试一次 —— 缝合轮次不会重试或重复告警。
     * <p>
     * The resource path falls back to the registry name when the provider leaves
     * parts empty (inheritance as declaration).
     * 路径缺省部分从注册名推导（继承即接入）。
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
            // 提供 typed 状态定义的 provider 在加载时即校验 variant 键；无效键 → builtin/missing。
            BlockstateKeyValidator.validate(bs, provider.getStateDefinition(),
                    "blockstate " + namespace + ":" + name);
            CatFrame.logger.info("Loaded blockstate for state-block: {}:{}", namespace, name);
        } else {
            CatFrame.logger.warn("Failed to load blockstate for state-block: {}:{}", namespace, name);
        }
    }

    /**
     * 解析 provider 物品的 ItemState 资源路径：显式声明优先，缺失部分从物品注册名
     * （{@code namespace:name}，无冒号 → {@code minecraft}）推导。
     * <p>
     * 实现即接入的关键一环：物品无需 register，只要 ItemState JSON 位于注册名对应的
     * 标准路径（或显式声明的路径），即可被自动发现、加载并绑定到该物品。
     * <p>
     * Resolves the ItemState resource path of a provider item — explicit declarations
     * win, missing parts fall back to the item's registry name ({@code namespace:name};
     * no colon → {@code minecraft}). This is what lets implementation alone suffice:
     * no register call as long as the JSON sits at the standardized path.
     *
     * @return {@code [namespace, name]}；注册名缺失且显式声明不完整时返回 {@code null}
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
     * 为 provider 物品加载 ItemState JSON（实现即接入），每个物品至多尝试一次。
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
     * 路径缺省部分从注册名推导；解析出的树按「注册名」绑定 —— Baking 4a 以注册名回查，
     * 显式声明的跨命名空间文件也能落到正确的物品上（显式声明只影响读取路径，
     * 不影响绑定键）。派生路径加载失败静默（接口物品本就允许没有 JSON，由 provider
     * 自渲染），仅在两个路径段都显式声明时才告警。
     */
    private static void loadStateProviderItem(Item item, IItemStateProvider provider) {
        if (!attemptedStateItems.add(item)) return;
        String[] path = resolveItemStatePath(item, provider);
        if (path == null) return;  // 无注册名且显式声明不完整 — 无法定位资源
        String declNs = path[0];
        String declName = path[1];
        String declaredNs = provider.getItemStateNamespace();
        String declaredName = provider.getItemStateName();
        boolean explicit = declaredNs != null && !declaredNs.isEmpty()
                && declaredName != null && !declaredName.isEmpty();

        // 绑定键 = 物品注册名（Baking 4a 按注册名回查）；注册名缺失时退回声明路径
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

        // 命名空间扫描已加载同一资源（声明路径 == 绑定键）时跳过
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
            // 收集决策树引用的模型纹理（前缀分流，幂等）
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
