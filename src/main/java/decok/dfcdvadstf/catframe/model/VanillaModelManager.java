package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.adapter.vanilla.model.VanillaBlockResolvers;
import decok.dfcdvadstf.catframe.model.core.async.AsyncBakePipeline;
import decok.dfcdvadstf.catframe.model.lazy.LazySingleBlockModel;
import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.render.extension.tint.TintRegistry;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import decok.dfcdvadstf.catframe.model.state.item.tint.ItemStateTintBridge;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStairs;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages vanilla block/item model overrides using JSON model files.
 * <p>
 * Since the module split (E3), this class is a <b>pure facade</b> — it holds no fields.
 * All state has been moved to the respective owning classes:
 * <ul>
 *   <li>{@link ModelManagerDataLoader} — namespace discovery, blockstate/mappings loading, IItemState discovery</li>
 *   <li>{@link VanillaTextureTracker} — texture collection, stitch callbacks</li>
 *   <li>{@link ModelRegistry} — model/blockstate registration API</li>
 *   <li>{@link Baking} — baking pipeline, caches</li>
 *   <li>{@link RenderDispatcher} — rendering dispatch</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class VanillaModelManager {

    // ==================== Model Mappings Data Class ====================

    public static class ModelMappings {
        /**
         * State-mapping mode switch (new-era format). Defaults to {@code false},
         * preserving the legacy key → model-path semantics for backward compatibility.
         * <p>
         * 新时代 state mapping 开关，默认 {@code false}（向后兼容：保留旧版
         * key → JSON 模型路径 的映射语义）。为 {@code true} 时，blocks/items 的值
         * 不再是模型路径，而是 (Block/Item)State 名称：
         * <ul>
         *   <li>blocks 值 → {@code blockstates/{value}.json}（可带 "ns:" 前缀）</li>
         *   <li>items 值 → {@code items/{value}.json} 决策树（可带 "ns:" 前缀）</li>
         * </ul>
         * 之后走常规 state → 模型 的数据驱动管线。面向未实现 JSON 加载的旧模组：
         * 把旧 ID 直接接入现有 state 定义，无需为每个旧 ID 单独铺模型文件。
         */
        public boolean state_mapping = false;
        public Map<String, String> blocks;
        public Map<String, String> items;
    }

    // ==================== Utilities ====================

    public static class Utilities {

        /**
         * 原版图集查询键：剥 namespace 与 block/blocks/item/items 前缀，生成 1.7.10
         * {@code TextureMap} 的 basePath 注册/查询键（如 {@code minecraft:block/ladder}
         * → {@code ladder}；原版加载时拼 {@code textures/blocks/ladder.png}）。
         * <p>
         * 仅用于原版图集（vanilla atlas）的键变换 —— CatFrame 数据驱动图集键是完整
         * 纹理路径（定义 JSON 产出的 sprite id），由 {@code CatAtlasManager} 独立管理，
         * 与本方法无关。
         *
         * <p>Vanilla-atlas base-path key: strips namespace and the block/blocks/
         * item/items prefixes. The data-driven CatAtlas identity is managed
         * separately by {@code CatAtlasManager}.
         *
         * <p>[Hot Update 撤回方案] 键策略收敛：namespace 剥离仅对 minecraft 生效
         * （扁平键与 {@code block.registerBlockIcons()} 幂等重合，OptiFine CTM 按名字
         * 匹配分毫不差）；其他命名空间保留为 {@code modid:name}（{@code ResourceLocation}
         * 解析 domain，落到 {@code modid:textures/blocks/<name>.png}），跨 mod 永不撞键。
         * Key strategy: the namespace is stripped only for minecraft (flat keys stay
         * idempotent with vanilla registration and OptiFine CTM name matching);
         * other namespaces keep {@code modid:name} to avoid cross-mod collisions.
         */
        public static String toVanillaBaseKey(String texturePath) {
            if (texturePath == null) return null;
            int colon = texturePath.indexOf(':');
            String namespace = colon >= 0 ? texturePath.substring(0, colon) : "minecraft";
            String pathPart = colon >= 0 ? texturePath.substring(colon + 1) : texturePath;
            if (pathPart.startsWith("blocks/")) {
                pathPart = pathPart.substring("blocks/".length());
            } else if (pathPart.startsWith("items/")) {
                pathPart = pathPart.substring("items/".length());
            } else if (pathPart.startsWith("block/")) {
                pathPart = pathPart.substring("block/".length());
            } else if (pathPart.startsWith("item/")) {
                pathPart = pathPart.substring("item/".length());
            }
            // minecraft → 扁平键；其余命名空间 → "modid:name"（跨 mod 不撞键）
            // minecraft → flat key; other namespaces → "modid:name" (collision-free)
            return "minecraft".equals(namespace) ? pathPart : namespace + ":" + pathPart;
        }

        @Nullable
        public static Block findBlock(String namespace, String name) {
            Block block = Block.getBlockFromName(name);
            if (block != null) return block;
            block = Block.getBlockFromName(namespace + ":" + name);
            return block;
        }

        @Nullable
        public static Item findItem(String namespace, String name) {
            Item item = (Item) Item.itemRegistry.getObject(namespace + ":" + name);
            if (item != null) return item;
            item = (Item) Item.itemRegistry.getObject(name);
            return item;
        }
    }

    // ==================== 向后兼容内层类 (模块拆分 E3 后作为委托) ====================

    /**
     * Backward-compatible delegate to {@link ModelManagerDataLoader}.
     */
    public static class DataLoading {
        public static void init() {
            ModelManagerDataLoader.init();
        }

        public static void registerNamespace(String namespace) {
            ModelManagerDataLoader.registerNamespace(namespace);
        }

        public static void registerBlockstateRedirect(Block block, IMetadataBlockstateRedirect redirect) {
            ModelManagerDataLoader.registerBlockstateRedirect(block, redirect);
        }
    }

    /**
     * 模型注册：异步烘焙架构下的模型注册入口。
     * <p>
     * 不再执行同步烘焙。所有烘焙由 {@link BakedModelCache}（懒烘焙）和
     * {@link AsyncBakePipeline}（异步预烘焙）承担。
     * <p>
     * 本类职责：
     * <ul>
     *   <li>物化 CatModels 声明式 spec（typed {@link decok.dfcdvadstf.catframe.model.state.CatStateDefinition} 常驻表）</li>
     *   <li>为 blockstate 创建常驻模型（{@link ResidentStateModel}）</li>
     *   <li>为 model_mappings 创建懒模型（{@link LazySingleBlockModel}、{@link ItemStateModel}）</li>
     *   <li>处理 BlockPane 特殊注册</li>
     *   <li>注册 Forge IItemRenderer</li>
     * </ul>
     */
    public static class Baking {

        // ==================== 模型注册入口 ====================

        /**
         * 注册所有模型（不执行烘焙）。
         * <p>
         * 在 {@link VanillaTextureTracker#onTextureStitchPost} 中调用，
         * 此时纹理已缝合、iconMap 已通过参数传入缓存和烘焙管线，懒烘焙可正确解析纹理。
         * <p>
         * 替代旧系统的 {@code bakeAllModels()}。
         */
        public static void registerAllModels() {
            // 清空注册表（保留 persistent 项）
            ModelRegistry.registeredBlockModels.clear();
            ModelRegistry.registeredBlockRotations.clear();
            ModelRegistry.registeredItemModels.keySet()
                    .removeIf(item -> !ModelRegistry.persistentItemModels.contains(item));
            // oversized 标记完全源自 loadedOversizedItems，下方循环会全量重建 ——
            // 清空以避免重注册后残留过期项（如资源包移除后旧的 oversized 标记）
            // oversized flags are fully derived from loadedOversizedItems and rebuilt below —
            // clear to avoid stale entries surviving re-registration (e.g. after pack removal)
            ModelRegistry.oversizedItems.clear();

            // Step 0: 物化 CatModels 声明式 spec（typed 常驻表 + itemFromBlockstate）
            CatModels.materialize();

            // Step 2: 处理 blockstates — 注册懒 BlockStateModel
            for (Map.Entry<String, Map<String, BlockstateJson>> nsEntry :
                    new ArrayList<>(ModelManagerDataLoader.loadedBlockstates.entrySet())) {
                String namespace = nsEntry.getKey();
                for (Map.Entry<String, BlockstateJson> bsEntry :
                        new ArrayList<>(nsEntry.getValue().entrySet())) {
                    String blockName = bsEntry.getKey();
                    BlockstateJson bs = bsEntry.getValue();
                    Block block = Utilities.findBlock(namespace, blockName);
                    if (block == null) continue;
                    // 已被 CatModels 物化或先前步骤处理的方块跳过
                    if (ModelRegistry.registeredBlockModels.containsKey(block)) continue;

                    registerResidentBlockModel(block, bs);
                }
            }

            // Step 3: 处理 model_mappings 中的 blocks（无 blockstate 的方块）
            for (Map.Entry<String, ModelMappings> entry :
                    ModelManagerDataLoader.loadedMappings.entrySet()) {
                String namespace = entry.getKey();
                ModelMappings mappings = entry.getValue();

                if (mappings.blocks != null) {
                    for (Map.Entry<String, String> blockEntry : mappings.blocks.entrySet()) {
                        String key = blockEntry.getKey();
                        String blockName;

                        if (key.contains(":")) {
                            String[] parts = key.split(":", 2);
                            blockName = parts[0];
                        } else {
                            blockName = key;
                        }

                        Block block = Utilities.findBlock(namespace, blockName);
                        if (block == null) continue;
                        // 已被 blockstate 处理的方块跳过
                        if (ModelRegistry.registeredBlockModels.containsKey(block)) continue;

                        if (mappings.state_mapping) {
                            // 新时代 state mapping：ID → blockstate 名，复用已加载的 state 定义
                            // New-era state mapping: ID → blockstate name, reuse the loaded state definition
                            String[] ref = parseStateRef(namespace, blockEntry.getValue());
                            Map<String, BlockstateJson> nsStates =
                                    ModelManagerDataLoader.loadedBlockstates.get(ref[0]);
                            BlockstateJson bs = nsStates != null ? nsStates.get(ref[1]) : null;
                            if (bs == null) {
                                CatFrame.logger.warn("[VMM] state_mapping block '{}' -> unknown blockstate '{}:{}'",
                                        key, ref[0], ref[1]);
                                continue;
                            }
                            registerResidentBlockModel(block, bs);
                        } else {
                            // legacy：使用懒单模型 —— 持有 modelPath，渲染时从 BakedModelCache 获取
                            ModelRegistry.registerBlockModel(block,
                                    new LazySingleBlockModel(blockEntry.getValue()));
                        }
                    }
                }
            }

            // Step 4: 注册 item 模型

            // 4a: items/ ItemState 决策树（最高优先 — 包括 ItemBlock）
            for (Map.Entry<String, Map<String, ItemStateNode>> nsEntry :
                    new ArrayList<>(ModelManagerDataLoader.loadedItemStates.entrySet())) {
                String namespace = nsEntry.getKey();
                for (Map.Entry<String, ItemStateNode> itemEntry : nsEntry.getValue().entrySet()) {
                    String itemName = itemEntry.getKey();
                    Item item = Utilities.findItem(namespace, itemName);
                    if (item == null) continue;
                    if (ModelRegistry.registeredItemModels.containsKey(item)) continue;

                    ItemStateModel ism = new ItemStateModel(itemEntry.getValue());
                    ModelRegistry.registeredItemModels.put(item, ism);
                    // 桥接：若决策树声明了 tints，注册到渲染侧 TintRegistry（由 TintRenderExtension 消费）
                    // Bridge: if the tree declares tints, register into the render-side TintRegistry
                    if (ism.hasAnyTint()) {
                        TintRegistry.registerItemTint(item, new ItemStateTintBridge(ism));
                    }
                    Set<String> nsOversized = ModelManagerDataLoader.loadedOversizedItems.get(namespace);
                    if (nsOversized != null && nsOversized.contains(itemName)) {
                        ModelRegistry.oversizedItems.add(item);
                    }
                    CatFrame.logger.debug("[VMM] Registered ItemState: {}:{}", namespace, itemName);
                }
            }

            // 4b: model_mappings 中的 items（跳过已被 ItemState 注册的；legacy / state mapping 双语义）
            for (Map.Entry<String, ModelMappings> entry :
                    ModelManagerDataLoader.loadedMappings.entrySet()) {
                registerMappingItems(entry.getKey(), entry.getValue());
            }

            // 4c: IItemState (Tier 3 接口发现)
            for (Map.Entry<Item, IItemStateProvider> entry : ModelManagerDataLoader.interfaceItemStates.entrySet()) {
                Item item = entry.getKey();
                if (ModelRegistry.registeredItemModels.containsKey(item)) continue;

                String registryName = Item.itemRegistry.getNameForObject(item);
                if (registryName == null) continue;

                ModelRegistry.registeredItemModels.put(item, entry.getValue());
                ModelRegistry.persistentItemModels.add(item);
                CatFrame.logger.debug("[VMM] Registered IItemState: {}", registryName);
            }

            // 4d: ITEM_MODEL 组件覆写（优先级最高 — 对标原版 item_model 组件）
            applyItemModelOverrides(false);

            // Step 5: Forge IItemRenderer 注册
            Set<Item> registered = new HashSet<>();
            int forgeRegistered = 0;

            for (Item item : ModelRegistry.registeredItemModels.keySet()) {
                if (registered.add(item)) {
                    MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
                    forgeRegistered++;
                }
            }
            for (Block block : ModelRegistry.registeredBlockModels.keySet()) {
                Item item = Item.getItemFromBlock(block);
                if (item != null && registered.add(item)) {
                    MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
                    forgeRegistered++;
                }
            }

            CatFrame.logger.info("VanillaModelManager: Registered {} block models, {} item models ({} Forge renderers)",
                    ModelRegistry.registeredBlockModels.size(),
                    ModelRegistry.registeredItemModels.size(), forgeRegistered);
        }

        /**
         * 增量重建 item 模型（item atlas 缝合后调用）。
         * <p>
         * 仅重新创建非 persistent 的 IItemState wrapper。
         * 由于使用懒模型，无需实际烘焙 — 只需更新注册表中的 wrapper 引用。
         */
        public static void registerItemModels() {
            // 移除非 persistent item models
            ModelRegistry.registeredItemModels.keySet()
                    .removeIf(item -> !ModelRegistry.persistentItemModels.contains(item));
            // oversized 标记全量重建（与 registerAllModels 同理，清除过期项）
            // Rebuild oversized flags from scratch (same as registerAllModels, drops stale entries)
            ModelRegistry.oversizedItems.clear();

            // 重新注册 ItemState 决策树（最高优先）
            for (Map.Entry<String, Map<String, ItemStateNode>> nsEntry :
                    ModelManagerDataLoader.loadedItemStates.entrySet()) {
                String namespace = nsEntry.getKey();
                for (Map.Entry<String, ItemStateNode> itemEntry : nsEntry.getValue().entrySet()) {
                    Item item = Utilities.findItem(namespace, itemEntry.getKey());
                    if (item == null) continue;
                    ItemStateModel ism = new ItemStateModel(itemEntry.getValue());
                    ModelRegistry.registeredItemModels.put(item, ism);
                    // 桥接：增量重建时同步重新注册 tints 桥接（指向新 ItemStateModel 实例）
                    // Bridge: re-register the tint bridge on incremental rebuild (points to the new instance)
                    if (ism.hasAnyTint()) {
                        TintRegistry.registerItemTint(item, new ItemStateTintBridge(ism));
                    }
                    Set<String> nsOversized = ModelManagerDataLoader.loadedOversizedItems.get(namespace);
                    if (nsOversized != null && nsOversized.contains(itemEntry.getKey())) {
                        ModelRegistry.oversizedItems.add(item);
                    }
                }
            }

            // 重新注册 model_mappings items（跳过已被 ItemState 注册的；legacy / state mapping 双语义）
            for (Map.Entry<String, ModelMappings> entry :
                    ModelManagerDataLoader.loadedMappings.entrySet()) {
                registerMappingItems(entry.getKey(), entry.getValue());
            }

            // 重新注册 IItemState items（跳过已有手动注册模型的 persistent 项）
            for (Map.Entry<Item, IItemStateProvider> entry : ModelManagerDataLoader.interfaceItemStates.entrySet()) {
                Item item = entry.getKey();
                if (ModelRegistry.registeredItemModels.containsKey(item)) continue;

                String registryName = Item.itemRegistry.getNameForObject(item);
                if (registryName == null) continue;

                ModelRegistry.registeredItemModels.put(item, entry.getValue());
                ModelRegistry.persistentItemModels.add(item);
                MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
            }

            // ITEM_MODEL 组件覆写（优先级最高 — 增量路径同样重新断言，防止被 items/ 约定匹配覆盖）
            applyItemModelOverrides(true);

            CatFrame.logger.info("VanillaModelManager: Re-registered {} item models (incremental, lazy)",
                    ModelRegistry.registeredItemModels.size());
        }

        /**
         * 注册单个方块的常驻 state 模型（Pane / Stairs / redirect / 常规 variants-multipart 分支）。
         * <p>
         * Register a resident state model for one block (Pane / Stairs / redirect / plain branches).
         * 从 {@link #registerAllModels} Step 2 提取，供 blockstate 遍历与
         * {@code state_mapping} 模式的 model_mappings 共用同一套注册逻辑。
         */
        private static void registerResidentBlockModel(Block block, BlockstateJson bs) {
            String blockId = Block.blockRegistry.getNameForObject(block);
            String ns = (blockId != null && blockId.contains(":"))
                    ? blockId.substring(0, blockId.indexOf(':')) : "minecraft";
            IMetadataBlockstateRedirect redirect = ModelManagerDataLoader.blockstateRedirects.get(block);

            // BlockPane: 运行时连接 multipart（per-face 合并 + AtlasGuard）
            if (block instanceof BlockPane) {
                ResidentStateModel.Builder pb = ResidentStateModel.builder(block)
                        .connectionMultipart()
                        .dynamic(VanillaBlockResolvers.PANE);
                if (redirect != null) {
                    pb.redirect(redirect, ns);
                } else {
                    pb.blockstate(bs);
                }
                ModelRegistry.registerBlockModel(block, pb.build());
                return;
            }

            // BlockStairs: 运行时转角检测（facing/half/shape 由 resolver 自建）
            if (block instanceof BlockStairs) {
                ModelRegistry.registerBlockModel(block, ResidentStateModel.builder(block)
                        .blockstate(bs)
                        .dynamic(VanillaBlockResolvers.STAIRS)
                        .build());
                return;
            }

            // Blockstate redirect: per-meta 重定向到另一个 blockstate
            if (redirect != null) {
                ResidentStateModel.Builder rb = ResidentStateModel.builder(block)
                        .redirect(redirect, ns);
                ModelRegistry.registerBlockModel(block, rb.build());
                return;
            }

            // 常规 variants/multipart
            ResidentStateModel.Builder nb = ResidentStateModel.builder(block).blockstate(bs);
            ModelRegistry.registerBlockModel(block, nb.build());
        }

        /**
         * 解析 state 引用字符串 {@code "ns:name"} 或 {@code "name"}（无前缀时默认当前 namespace）。
         * <p>
         * Parse a state reference {@code "ns:name"} / {@code "name"} (defaults to the current namespace).
         *
         * @return {@code [namespace, name]}
         */
        private static String[] parseStateRef(String defaultNs, String ref) {
            int sep = ref.indexOf(':');
            return sep >= 0
                    ? new String[]{ref.substring(0, sep), ref.substring(sep + 1)}
                    : new String[]{defaultNs, ref};
        }

        /**
         * 注册 model_mappings.items（legacy / state mapping 双语义，全量与增量入口共用）。
         * <p>
         * Register model_mappings.items with dual semantics, shared by the full and incremental paths:
         * <ul>
         *   <li>{@code state_mapping=false}（legacy）：值为模型路径 → 包装为单模型 {@link ItemStateModel}</li>
         *   <li>{@code state_mapping=true}：值为 ItemState 决策树名 → 复用已加载的决策树，
         *       tint 桥接与 {@code oversized_in_gui} 标记随被引用的 state 一并生效</li>
         * </ul>
         * 已被 items/ 约定匹配注册的物品不会被覆盖（ItemState 优先级更高）。
         */
        private static void registerMappingItems(String namespace, ModelMappings mappings) {
            if (mappings.items == null) return;

            for (Map.Entry<String, String> itemEntry : mappings.items.entrySet()) {
                String key = itemEntry.getKey();
                String itemName;

                if (key.contains(":")) {
                    String[] parts = key.split(":", 2);
                    itemName = parts[0];
                } else {
                    itemName = key;
                }

                Item item = Utilities.findItem(namespace, itemName);
                if (item == null) continue;
                // 跳过已被 ItemState 注册的
                if (ModelRegistry.registeredItemModels.containsKey(item)) continue;

                if (mappings.state_mapping) {
                    // 新时代 state mapping：ID → ItemState 决策树名，复用已加载的 state 定义
                    // New-era state mapping: ID → ItemState tree name, reuse the loaded definition
                    String[] ref = parseStateRef(namespace, itemEntry.getValue());
                    Map<String, ItemStateNode> nsStates = ModelManagerDataLoader.loadedItemStates.get(ref[0]);
                    ItemStateNode node = nsStates != null ? nsStates.get(ref[1]) : null;
                    if (node == null) {
                        CatFrame.logger.warn("[VMM] state_mapping item '{}' -> unknown item state '{}:{}'",
                                key, ref[0], ref[1]);
                        continue;
                    }
                    ItemStateModel ism = new ItemStateModel(node);
                    ModelRegistry.registeredItemModels.put(item, ism);
                    // tint 桥接与 oversized 标记随被引用的 state 同步生效
                    // Tint bridge and oversized flag follow the referenced state
                    if (ism.hasAnyTint()) {
                        TintRegistry.registerItemTint(item, new ItemStateTintBridge(ism));
                    }
                    Set<String> nsOversized = ModelManagerDataLoader.loadedOversizedItems.get(ref[0]);
                    if (nsOversized != null && nsOversized.contains(ref[1])) {
                        ModelRegistry.oversizedItems.add(item);
                    }
                } else {
                    // legacy：值为模型路径，包装为单模型 wrapper
                    ModelRegistry.registeredItemModels.put(item,
                            new ItemStateModel(itemEntry.getValue()));
                }
            }
        }

        /**
         * 应用 {@code ITEM_MODEL} 组件覆写（对标原版 item_model 组件，优先级最高）。
         * <p>
         * 遍历所有已注册物品，若物品挂了 {@code ITEM_MODEL} 默认组件，则以该值指向的模型
         * 映射为准，覆盖按注册 ID 约定匹配的 {@code items/{name}.json}，并标记为 persistent。
         * 值存在但无法解析时使用 {@code builtin/missing}
         * （见 {@link ModelRegistry#resolveItemModelOverride}）。
         *
         * @param registerRenderer 是否同时注册 Forge {@code IItemRenderer}
         */
        private static void applyItemModelOverrides(boolean registerRenderer) {
            for (Object obj : Item.itemRegistry) {
                Item item = (Item) obj;
                IItemStateProvider override = ModelRegistry.resolveItemModelOverride(item);
                if (override == null) continue;
                ModelRegistry.registeredItemModels.put(item, override);
                ModelRegistry.persistentItemModels.add(item);
                if (registerRenderer) {
                    MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
                }
                CatFrame.logger.debug("[VMM] Applied ITEM_MODEL override: {} -> {}",
                        Item.itemRegistry.getNameForObject(item), ModelRegistry.getItemModelOverride(item));
            }
        }

    }
}
