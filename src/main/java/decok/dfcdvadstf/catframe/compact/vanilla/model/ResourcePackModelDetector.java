package decok.dfcdvadstf.catframe.compact.vanilla.model;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import com.google.gson.JsonObject;
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

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监听资源管理器重载，检测顶层资源包提供的模型 JSON 和 BlockState JSON 内容。
 * <p>
 * 当玩家加载/切换资源包时，自动扫描已注册命名空间中已知的模型和 blockstate 路径，
 * 使用 {@link IResourceManager#getResource(ResourceLocation)} 获取顶层资源包中的版本，
 * 解析为 {@link ModelJson} / {@link BlockstateJson} 供其他子系统查询。
 * <p>
 * 注册方式完全复用 {@link decok.dfcdvadstf.catframe.compact.vanilla.LanguageReloadListener}
 * 的延迟注册模式：资源管理器不可用时通过一次性 ClientTick 延迟注册。
 * <p>
 * 资源包覆盖生效链路（扫描 → 合并 → 重注册）：
 * <ul>
 *   <li><b>items/ ItemState 决策树</b>：候选名来自 Item 注册表 ∪ 已加载决策树；
 *       通过 {@link IResourceManager#getAllResources} 区分“仅 mod jar 自带”与“被资源包覆盖/新增”，
 *       覆盖项合并进 {@link ModelManagerDataLoader#loadedItemStates} 后重建 item 模型 wrapper。</li>
 *   <li><b>blockstates/</b>：候选名来自 Block 注册表 ∪ 已加载 blockstate；同样区分 jar 自带与真覆盖，
 *       覆盖项合并进 {@link ModelManagerDataLoader#loadedBlockstates} 后重新注册方块模型。</li>
 *   <li><b>models/</b>：无需合并 —— {@link ModelResolver} 每次解析都走 IResourceManager，
 *       重载时清缓存即可自然感知资源包；这里的扫描结果仅供诊断。</li>
 * </ul>
 * <p>
 * 时序说明：1.7.10 中 TextureMap 的 reload listener 先于本监听器注册，因此重载时
 * 纹理缝合（及其触发的 {@code registerAllModels}）已完成，本监听器是重载链的末端 ——
 * 在此合并覆盖并重注册懒模型是安全落点。注意 {@code registerReloadListener} 注册时
 * 会立即回调一次（此时图集未就绪），故重注册步骤以图集就绪为前提。
 * <p>
 * 已知局限：资源包新引入的纹理（未在 preInit 纹理收集阶段登记过的）不在图集中，
 * 会回退到 missingno；覆盖已有模型的几何/属性/决策逻辑则完全生效。
 */
public class ResourcePackModelDetector implements IResourceManagerReloadListener {

    /** namespace:path → 顶层资源包提供的 ModelJson */
    public static final Map<String, ModelJson> PACK_MODELS = new ConcurrentHashMap<>();
    /** namespace:blockName → 顶层资源包提供的 BlockstateJson */
    public static final Map<String, BlockstateJson> PACK_BLOCKSTATES = new ConcurrentHashMap<>();
    /** namespace:itemName → 顶层资源包提供的 ItemState 决策树根（含根级字段） */
    public static final Map<String, ItemStateRoot> PACK_ITEM_STATES = new ConcurrentHashMap<>();
    /** 顶层资源包覆盖的模型路径集合 */
    public static final Set<String> PACK_MODEL_PATHS = ConcurrentHashMap.newKeySet();
    /** 顶层资源包覆盖的 blockstate 路径集合 */
    public static final Set<String> PACK_BLOCKSTATE_PATHS = ConcurrentHashMap.newKeySet();
    /** 顶层资源包覆盖的 ItemState 路径集合 */
    public static final Set<String> PACK_ITEM_STATE_PATHS = ConcurrentHashMap.newKeySet();

    // ==================== classpath 基线快照（资源包移除时还原用） ====================

    /** 首次合并前的 loadedBlockstates 快照（内层 map 浅拷贝） */
    private static Map<String, Map<String, BlockstateJson>> baselineBlockstates = null;
    /** 首次合并前的 loadedItemStates 快照（内层 map 浅拷贝） */
    private static Map<String, Map<String, ItemStateNode>> baselineItemStates = null;
    /** 首次合并前的 loadedOversizedItems 快照 */
    private static Map<String, Set<String>> baselineOversizedItems = null;

    /** 上一轮重载是否存在 blockstate 覆盖（覆盖被移除时也需重注册以还原） */
    private static boolean blockOverridesWereActive = false;
    /** 上一轮重载是否存在 ItemState 覆盖 */
    private static boolean itemOverridesWereActive = false;

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        CatFrame.logger.debug("ResourcePackModelDetector: resource manager reloaded, scanning...");
        clear();
        ModelResolver.clearCache();
        scanAllNamespaces(manager);
        // 合并覆盖并重注册懒模型（扫描 → 还原基线 → 叠加覆盖 → 重注册）
        // Merge overrides and re-register lazy models (scan → restore baseline → overlay → re-register)
        applyOverrides();
        CatFrame.logger.info("ResourcePackModelDetector: detected {} model overrides, {} blockstate overrides, {} item state overrides",
                PACK_MODEL_PATHS.size(), PACK_BLOCKSTATE_PATHS.size(), PACK_ITEM_STATE_PATHS.size());
    }

    private static void clear() {
        PACK_MODELS.clear();
        PACK_BLOCKSTATES.clear();
        PACK_ITEM_STATES.clear();
        PACK_MODEL_PATHS.clear();
        PACK_BLOCKSTATE_PATHS.clear();
        PACK_ITEM_STATE_PATHS.clear();
    }

    private static void scanAllNamespaces(IResourceManager manager) {
        for (String ns : ModelManagerDataLoader.namespaces) {
            scanNamespace(manager, ns);
        }
    }

    private static void scanNamespace(IResourceManager manager, String ns) {
        // 扫描 BlockStates：候选名 = 已加载 blockstate 名 ∪ Block 注册表中属于该 namespace 的方块名
        // （后者允许资源包为 jar 未提供 blockstate 的方块新增定义）
        Map<String, BlockstateJson> nsBlockstates = ModelManagerDataLoader.loadedBlockstates.get(ns);
        Set<String> blockCandidates = new LinkedHashSet<>();
        if (nsBlockstates != null) {
            blockCandidates.addAll(nsBlockstates.keySet());
        }
        for (Object obj : net.minecraft.block.Block.blockRegistry) {
            if (obj == null) continue;
            String registryName = net.minecraft.block.Block.blockRegistry.getNameForObject(obj);
            if (registryName == null) continue;
            String blockNs = registryName.contains(":") ? registryName.substring(0, registryName.indexOf(':')) : "minecraft";
            if (!blockNs.equals(ns)) continue;
            blockCandidates.add(registryName.contains(":") ? registryName.substring(registryName.indexOf(':') + 1) : registryName);
        }
        for (String blockName : blockCandidates) {
            ResourceLocation loc = new ResourceLocation(ns, "blockstates/" + blockName + ".json");
            try {
                List<?> all = manager.getAllResources(loc);
                if (all == null || all.isEmpty()) continue;
                // 仅 mod jar 自带（classpath 已加载且无更高优先级副本）→ 非资源包覆盖，跳过
                boolean classpathHasIt = nsBlockstates != null && nsBlockstates.containsKey(blockName);
                if (all.size() == 1 && classpathHasIt) continue;
                IResource topResource = (IResource) all.get(all.size() - 1);
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
                // 顶层资源包未覆盖此 blockstate
            }
        }

        // 扫描 items/ ItemState 决策树
        scanItemStates(manager, ns);

        // 扫描已知模型路径（仅诊断用途 —— 模型加载本身已经由 ModelResolver 走 IResourceManager）
        VanillaModelManager.ModelMappings mappings = ModelManagerDataLoader.loadedMappings.get(ns);
        if (mappings != null) {
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
     * 扫描该 namespace 下被资源包覆盖/新增的 {@code items/{name}.json} 决策树。
     * <p>
     * 候选名 = Item 注册表中属于该 namespace 的物品名 ∪ classpath 已加载的决策树名，
     * 与 {@code NamespaceLoadTask#loadItemStates} 的自动发现口径一致。
     * 通过 {@link IResourceManager#getAllResources} 的层数判断是否为真覆盖：
     * 仅当存在多个副本（资源包叠加在 jar 之上）或 classpath 未加载过（资源包纯新增）时才记录。
     */
    private static void scanItemStates(IResourceManager manager, String ns) {
        Map<String, ItemStateNode> nsItemStates = ModelManagerDataLoader.loadedItemStates.get(ns);
        Set<String> candidates = new LinkedHashSet<>();
        for (Object obj : Item.itemRegistry) {
            if (obj == null) continue;
            String registryName = Item.itemRegistry.getNameForObject(obj);
            if (registryName == null) continue;
            String itemNs = registryName.contains(":") ? registryName.substring(0, registryName.indexOf(':')) : "minecraft";
            if (!itemNs.equals(ns)) continue;
            candidates.add(registryName.contains(":") ? registryName.substring(registryName.indexOf(':') + 1) : registryName);
        }
        if (nsItemStates != null) {
            candidates.addAll(nsItemStates.keySet());
        }

        for (String itemName : candidates) {
            ResourceLocation loc = new ResourceLocation(ns, "items/" + itemName + ".json");
            try {
                List<?> all = manager.getAllResources(loc);
                if (all == null || all.isEmpty()) continue;
                // 仅 mod jar 自带 → 非资源包覆盖，跳过
                boolean classpathHasIt = nsItemStates != null && nsItemStates.containsKey(itemName);
                if (all.size() == 1 && classpathHasIt) continue;
                IResource topResource = (IResource) all.get(all.size() - 1);
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
                // 顶层资源包未覆盖此 ItemState
            }
        }
    }

    private static void scanModel(IResourceManager manager, String modelPath) {
        if (modelPath == null) return;
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
            IResource topResource = manager.getResource(loc);
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
            // 顶层资源包未覆盖此模型
        }
    }

    // ==================== 覆盖生效（合并 + 重注册） ====================

    /**
     * 将扫描到的资源包覆盖合并进模型系统并重注册懒模型。
     * <p>
     * Apply scanned resource-pack overrides into the model system and re-register lazy models.
     * <p>
     * 流程 / Flow：
     * <ol>
     *   <li>首次调用时快照 classpath 基线（内层容器浅拷贝）
     *       / snapshot classpath baseline on first call (shallow-copy inner containers)</li>
     *   <li>还原基线 —— 资源包被移除后覆盖自动消失
     *       / restore baseline so overrides vanish when the pack is removed</li>
     *   <li>叠加本轮扫描到的 blockstate / ItemState 覆盖
     *       / overlay this round's blockstate / ItemState overrides</li>
     *   <li>图集就绪时按需重注册（blockstate 变化走全量，仅 item 变化走增量）
     *       / re-register on demand when the atlas is ready (full for blockstates, incremental for items only)</li>
     * </ol>
     * 注意：{@code registerReloadListener} 注册时的立即回调场景下图集未就绪，
     * 此时仅合并数据、跳过重注册（首轮 {@code registerAllModels} 由纹理缝合事件触发）。
     */
    private static void applyOverrides() {
        // 模型系统尚未初始化（preInit 阶段的立即回调）→ 无基线可合并
        // Model system not initialized yet (immediate callback during preInit) → nothing to merge
        if (!ModelManagerDataLoader.initialized) return;

        // 1. 首次快照 classpath 基线 / snapshot classpath baseline on first call
        if (baselineBlockstates == null) {
            baselineBlockstates = new HashMap<>();
            for (Map.Entry<String, Map<String, BlockstateJson>> e : ModelManagerDataLoader.loadedBlockstates.entrySet()) {
                baselineBlockstates.put(e.getKey(), new HashMap<>(e.getValue()));
            }
            baselineItemStates = new HashMap<>();
            for (Map.Entry<String, Map<String, ItemStateNode>> e : ModelManagerDataLoader.loadedItemStates.entrySet()) {
                baselineItemStates.put(e.getKey(), new HashMap<>(e.getValue()));
            }
            baselineOversizedItems = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : ModelManagerDataLoader.loadedOversizedItems.entrySet()) {
                baselineOversizedItems.put(e.getKey(), new HashSet<>(e.getValue()));
            }
        }

        // 2. 还原基线 / restore baseline (so removed packs revert cleanly)
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

        // 3. 叠加覆盖 / overlay overrides
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
            // oversized_in_gui 根级字段随覆盖同步（false 时清除基线中的旧标记）
            // Sync root-level oversized_in_gui (false removes any stale baseline flag)
            Set<String> oversized = ModelManagerDataLoader.loadedOversizedItems
                    .computeIfAbsent(ns, k -> new HashSet<>());
            if (root.oversizedInGui) {
                oversized.add(name);
            } else {
                oversized.remove(name);
            }
        }

        // 4. 条件重注册 / conditional re-registration
        boolean anyBlockNow = !PACK_BLOCKSTATES.isEmpty();
        boolean anyItemNow = !PACK_ITEM_STATES.isEmpty();
        // 图集就绪判断：注册时的立即回调发生在缝合之前，此时跳过重注册
        // Atlas-ready check: the immediate callback on registration happens before stitching
        boolean atlasReady = !VanillaTextureTracker.textureIcons.isEmpty();
        if (atlasReady) {
            if (anyBlockNow || blockOverridesWereActive) {
                // blockstate 覆盖出现或消失 → 全量重注册（同时覆盖 item 侧）
                // Blockstate overrides appeared/vanished → full re-registration (covers items too)
                VanillaModelManager.Baking.registerAllModels();
            } else if (anyItemNow || itemOverridesWereActive) {
                // 仅 ItemState 覆盖变化 → 增量重建物品 wrapper
                // Only ItemState overrides changed → incremental item wrapper rebuild
                VanillaModelManager.Baking.registerItemModels();
            }
        }
        blockOverridesWereActive = anyBlockNow;
        itemOverridesWereActive = anyItemNow;
    }

    // ==================== 注册 ====================

    /**
     * 注册此监听器到 Minecraft 资源管理器。
     * 安全地在 mod init 调用 —— 若资源管理器未就绪，通过一次性 Tick 延迟注册。
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

    // ==================== 查询 API ====================

    /** 顶层资源包是否覆盖了指定模型？ */
    public static boolean hasModelOverride(String ns, String path) {
        return PACK_MODEL_PATHS.contains(ns + ":" + path);
    }

    /** 顶层资源包是否覆盖了指定 blockstate？ */
    public static boolean hasBlockstateOverride(String ns, String blockName) {
        return PACK_BLOCKSTATE_PATHS.contains(ns + ":" + blockName);
    }

    /** 获取顶层资源包的模型 JSON，或 null */
    public static ModelJson getTopModel(String ns, String path) {
        return PACK_MODELS.get(ns + ":" + path);
    }

    /** 获取顶层资源包的 Blockstate JSON，或 null */
    public static BlockstateJson getTopBlockstate(String ns, String blockName) {
        return PACK_BLOCKSTATES.get(ns + ":" + blockName);
    }

    /** 获取所有被覆盖的模型路径（不可变视图，用于调试） */
    public static Set<String> getOverriddenModelPaths() {
        return Collections.unmodifiableSet(PACK_MODEL_PATHS);
    }

    /** 获取所有被覆盖的 blockstate 路径（不可变视图，用于调试） */
    public static Set<String> getOverriddenBlockstatePaths() {
        return Collections.unmodifiableSet(PACK_BLOCKSTATE_PATHS);
    }
}
