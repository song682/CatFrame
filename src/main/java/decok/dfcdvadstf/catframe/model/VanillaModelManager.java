package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.async.AsyncBakePipeline;
import decok.dfcdvadstf.catframe.model.lazy.LazyBlockstateModel;
import decok.dfcdvadstf.catframe.model.lazy.LazyRedirectModel;
import decok.dfcdvadstf.catframe.model.lazy.LazySingleBlockModel;
import decok.dfcdvadstf.catframe.model.lazy.StairsBlockModel;
import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import decok.dfcdvadstf.catframe.model.state.block.PaneMultipartRedirectModel;
import decok.dfcdvadstf.catframe.model.state.block.SingleBlockModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStairs;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import javax.annotation.Nullable;
import java.util.*;

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
        public Map<String, String> blocks;
        public Map<String, String> items;
    }

    // ==================== Utilities ====================

    public static class Utilities {

        public static String resolveTextureName(String texturePath) {
            if (texturePath == null) return null;

            String namespace = "";
            String pathPart = texturePath;

            if (texturePath.contains(":")) {
                namespace = texturePath.substring(0, texturePath.indexOf(':') + 1);
                pathPart = texturePath.substring(texturePath.indexOf(':') + 1);
            }

            if (pathPart.startsWith("blocks/")) {
                pathPart = pathPart.substring("blocks/".length());
            } else if (pathPart.startsWith("items/")) {
                pathPart = pathPart.substring("items/".length());
            } else if (pathPart.startsWith("block/")) {
                pathPart = pathPart.substring("block/".length());
            } else if (pathPart.startsWith("item/")) {
                pathPart = pathPart.substring("item/".length());
            }

            return namespace + pathPart;
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

        @Nullable
        public static IMetadataMapper findMetadataMapEntry(String namespace, String blockName) {
            Map<Integer, Map<String, String>> metaMap = null;
            Map<String, Map<Integer, Map<String, String>>> nsData = ModelManagerDataLoader.loadedMetadataMaps.get(namespace);
            if (nsData != null) {
                metaMap = nsData.get(blockName);
            }
            if (metaMap == null) {
                for (Map.Entry<String, Map<String, Map<Integer, Map<String, String>>>> e : ModelManagerDataLoader.loadedMetadataMaps.entrySet()) {
                    metaMap = e.getValue().get(blockName);
                    if (metaMap != null) break;
                }
            }
            if (metaMap == null) return null;

            final Map<Integer, Map<String, String>> finalMap = metaMap;
            return metadata -> finalMap.getOrDefault(metadata, Collections.emptyMap());
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

        public static void registerMetadataMapping(Block block, IMetadataMapper mapper) {
            ModelManagerDataLoader.registerMetadataMapping(block, mapper);
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
     *   <li>注册 metadata mapper（从 metadata_map.json）</li>
     *   <li>为 blockstate 创建懒模型（{@link LazyBlockstateModel}、{@link LazyRedirectModel}）</li>
     *   <li>为 model_mappings 创建懒模型（{@link SingleBlockModel}、{@link ItemStateModel}）</li>
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

            // Step 1: 自动注册 metadata mapper（从 metadata_map.json）
            for (Map.Entry<String, Map<String, Map<Integer, Map<String, String>>>> nsEntry :
                    ModelManagerDataLoader.loadedMetadataMaps.entrySet()) {
                String namespace = nsEntry.getKey();
                for (Map.Entry<String, Map<Integer, Map<String, String>>> blockEntry :
                        nsEntry.getValue().entrySet()) {
                    String blockName = blockEntry.getKey();
                    Block block = Utilities.findBlock(namespace, blockName);
                    if (block != null && !ModelManagerDataLoader.metadataMappers.containsKey(block)) {
                        IMetadataMapper jsonMapper = Utilities.findMetadataMapEntry(namespace, blockName);
                        if (jsonMapper != null) {
                            ModelManagerDataLoader.metadataMappers.put(block, jsonMapper);
                            CatFrame.logger.debug("Auto-registered metadata mapper from metadata_map.json for {}:{}",
                                    namespace, blockName);
                        }
                    }
                }
            }

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

                    // BlockPane: 使用运行时连接模型
                    if (block instanceof BlockPane) {
                        IMetadataBlockstateRedirect redirect = ModelManagerDataLoader.blockstateRedirects.get(block);
                        String blockId = Block.blockRegistry.getNameForObject(block);
                        String ns = blockId.contains(":") ? blockId.substring(0, blockId.indexOf(':')) : "minecraft";
                        if (redirect != null) {
                            ModelRegistry.registerBlockModel(block,
                                    new PaneMultipartRedirectModel(block, redirect, ns, false));
                        } else {
                            ModelRegistry.registerBlockModel(block,
                                    new PaneMultipartRedirectModel(block, bs, false));
                        }
                        continue;
                    }

                    // BlockStairs: 运行时转角检测
                    if (block instanceof BlockStairs) {
                        IMetadataMapper stairsMapper = ModelManagerDataLoader.metadataMappers.get(block);
                        ModelRegistry.registerBlockModel(block, new StairsBlockModel(bs, stairsMapper));
                        continue;
                    }

                    // Blockstate redirect: 使用懒重定向模型
                    IMetadataBlockstateRedirect redirect = ModelManagerDataLoader.blockstateRedirects.get(block);
                    if (redirect != null) {
                        String blockId = Block.blockRegistry.getNameForObject(block);
                        String ns = blockId.contains(":") ? blockId.substring(0, blockId.indexOf(':')) : "minecraft";
                        IMetadataMapper mapper = ModelManagerDataLoader.metadataMappers.get(block);
                        ModelRegistry.registerBlockModel(block,
                                new LazyRedirectModel(redirect, mapper, ns));
                        continue;
                    }

                    // 常规 variants/multipart: 使用懒 blockstate 模型
                    IMetadataMapper mapper = ModelManagerDataLoader.metadataMappers.get(block);
                    ModelRegistry.registerBlockModel(block, new LazyBlockstateModel(bs, mapper));
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

                        // 使用懒单模型：持有 modelPath，渲染时从 BakedModelCache 获取
                        ModelRegistry.registerBlockModel(block,
                                new LazySingleBlockModel(blockEntry.getValue()));
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

                    ModelRegistry.registeredItemModels.put(item,
                            new ItemStateModel(itemEntry.getValue()));
                    Set<String> nsOversized = ModelManagerDataLoader.loadedOversizedItems.get(namespace);
                    if (nsOversized != null && nsOversized.contains(itemName)) {
                        ModelRegistry.oversizedItems.add(item);
                    }
                    CatFrame.logger.debug("[VMM] Registered ItemState: {}:{}", namespace, itemName);
                }
            }

            // 4b: model_mappings 中的 items（跳过已被 ItemState 注册的）
            for (Map.Entry<String, ModelMappings> entry :
                    ModelManagerDataLoader.loadedMappings.entrySet()) {
                ModelMappings mappings = entry.getValue();
                if (mappings.items == null) continue;

                for (Map.Entry<String, String> itemEntry : mappings.items.entrySet()) {
                    String key = itemEntry.getKey();
                    String itemName;

                    if (key.contains(":")) {
                        String[] parts = key.split(":", 2);
                        itemName = parts[0];
                    } else {
                        itemName = key;
                    }

                    Item item = Utilities.findItem(entry.getKey(), itemName);
                    if (item == null) continue;
                    // 跳过已被 ItemState 注册的
                    if (ModelRegistry.registeredItemModels.containsKey(item)) continue;

                    ModelRegistry.registeredItemModels.put(item,
                            new ItemStateModel(itemEntry.getValue()));
                }
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

            // 重新注册 ItemState 决策树（最高优先）
            for (Map.Entry<String, Map<String, ItemStateNode>> nsEntry :
                    ModelManagerDataLoader.loadedItemStates.entrySet()) {
                String namespace = nsEntry.getKey();
                for (Map.Entry<String, ItemStateNode> itemEntry : nsEntry.getValue().entrySet()) {
                    Item item = Utilities.findItem(namespace, itemEntry.getKey());
                    if (item == null) continue;
                    ModelRegistry.registeredItemModels.put(item,
                            new ItemStateModel(itemEntry.getValue()));
                    Set<String> nsOversized = ModelManagerDataLoader.loadedOversizedItems.get(namespace);
                    if (nsOversized != null && nsOversized.contains(itemEntry.getKey())) {
                        ModelRegistry.oversizedItems.add(item);
                    }
                }
            }

            // 重新注册 model_mappings items（跳过已被 ItemState 注册的）
            for (Map.Entry<String, ModelMappings> entry :
                    ModelManagerDataLoader.loadedMappings.entrySet()) {
                String namespace = entry.getKey();
                ModelMappings mappings = entry.getValue();
                if (mappings.items == null) continue;

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

                    ModelRegistry.registeredItemModels.put(item,
                            new ItemStateModel(itemEntry.getValue()));
                }
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
