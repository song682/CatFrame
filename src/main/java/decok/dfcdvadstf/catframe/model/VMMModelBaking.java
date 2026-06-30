package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import javax.annotation.Nullable;
import java.util.Map;

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
 *   <li>为 model_mappings 创建懒模型（{@link SingleBlockModel}、{@link LazyItemModel}）</li>
 *   <li>处理 BlockPane 特殊注册</li>
 *   <li>注册 Forge IItemRenderer</li>
 * </ul>
 */
public class VMMModelBaking {

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
        VanillaModelRegistry.registeredBlockModels.clear();
        VanillaModelRegistry.registeredBlockRotations.clear();
        VanillaModelRegistry.registeredItemModels.keySet()
                .removeIf(item -> !VanillaModelRegistry.persistentItemModels.contains(item));

        // Step 1: 自动注册 metadata mapper（从 metadata_map.json）
        for (Map.Entry<String, Map<String, Map<Integer, Map<String, String>>>> nsEntry :
                VMMDataLoader.loadedMetadataMaps.entrySet()) {
            String namespace = nsEntry.getKey();
            for (Map.Entry<String, Map<Integer, Map<String, String>>> blockEntry :
                    nsEntry.getValue().entrySet()) {
                String blockName = blockEntry.getKey();
                Block block = VanillaModelManager.Utilities.findBlock(namespace, blockName);
                if (block != null && !VMMDataLoader.metadataMappers.containsKey(block)) {
                    IMetadataMapper jsonMapper = VanillaModelManager.Utilities.findMetadataMapEntry(namespace, blockName);
                    if (jsonMapper != null) {
                        VMMDataLoader.metadataMappers.put(block, jsonMapper);
                        CatFrame.logger.debug("Auto-registered metadata mapper from metadata_map.json for {}:{}",
                                namespace, blockName);
                    }
                }
            }
        }

        // Step 2: 处理 blockstates — 注册懒 BlockStateModel
        for (Map.Entry<String, Map<String, BlockstateJson>> nsEntry :
                new java.util.ArrayList<>(VMMDataLoader.loadedBlockstates.entrySet())) {
            String namespace = nsEntry.getKey();
            for (Map.Entry<String, BlockstateJson> bsEntry :
                    new java.util.ArrayList<>(nsEntry.getValue().entrySet())) {
                String blockName = bsEntry.getKey();
                BlockstateJson bs = bsEntry.getValue();
                Block block = VanillaModelManager.Utilities.findBlock(namespace, blockName);
                if (block == null) continue;

                // BlockPane: 使用运行时连接模型
                if (block instanceof BlockPane) {
                    IMetadataBlockstateRedirect redirect = VMMDataLoader.blockstateRedirects.get(block);
                    String blockId = Block.blockRegistry.getNameForObject(block);
                    String ns = blockId.contains(":") ? blockId.substring(0, blockId.indexOf(':')) : "minecraft";
                    if (redirect != null) {
                        VanillaModelRegistry.registerBlockModel(block,
                                new PaneMultipartRedirectModel(block, redirect, ns, false));
                    } else {
                        VanillaModelRegistry.registerBlockModel(block,
                                new PaneMultipartRedirectModel(block, bs, false));
                    }
                    continue;
                }

                // Blockstate redirect: 使用懒重定向模型
                IMetadataBlockstateRedirect redirect = VMMDataLoader.blockstateRedirects.get(block);
                if (redirect != null) {
                    String blockId = Block.blockRegistry.getNameForObject(block);
                    String ns = blockId.contains(":") ? blockId.substring(0, blockId.indexOf(':')) : "minecraft";
                    IMetadataMapper mapper = VMMDataLoader.metadataMappers.get(block);
                    VanillaModelRegistry.registerBlockModel(block,
                            new LazyRedirectModel(redirect, mapper, ns));
                    continue;
                }

                // 常规 variants/multipart: 使用懒 blockstate 模型
                IMetadataMapper mapper = VMMDataLoader.metadataMappers.get(block);
                VanillaModelRegistry.registerBlockModel(block, new LazyBlockstateModel(bs, mapper));
            }
        }

        // Step 3: 处理 model_mappings 中的 blocks（无 blockstate 的方块）
        for (Map.Entry<String, VanillaModelManager.ModelMappings> entry :
                VMMDataLoader.loadedMappings.entrySet()) {
            String namespace = entry.getKey();
            VanillaModelManager.ModelMappings mappings = entry.getValue();

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

                    Block block = VanillaModelManager.Utilities.findBlock(namespace, blockName);
                    if (block == null) continue;
                    // 已被 blockstate 处理的方块跳过
                    if (VanillaModelRegistry.registeredBlockModels.containsKey(block)) continue;

                    // 使用懒单模型：持有 modelPath，渲染时从 BakedModelCache 获取
                    VanillaModelRegistry.registerBlockModel(block,
                            new LazySingleBlockModel(blockEntry.getValue()));
                }
            }
        }

        // Step 4: 注册 item 模型

        // 4a: 为有 blockstate 的方块注册 item 模型（ItemBlock 形式）
        for (Map.Entry<Block, BlockstateJson> entry : collectBlockstateBlocks()) {
            Block block = entry.getKey();
            Item item = Item.getItemFromBlock(block);
            if (item == null) continue;
            if (VanillaModelRegistry.registeredItemModels.containsKey(item)) continue;

            String modelPath = findFirstModelPath(entry.getValue());
            if (modelPath == null) continue;

            Map<String, ModelJson.DisplayTransform> display = resolveDisplay(modelPath);
            VanillaModelRegistry.registeredItemModels.put(item,
                    new ItemModelWrapper(VanillaModelRegistry.registeredBlockModels.get(block), display));
        }

        // 4a-items: items/ ItemState 决策树（最高优先）
        for (Map.Entry<String, Map<String, ItemStateNode>> nsEntry :
                new java.util.ArrayList<>(VMMDataLoader.loadedItemStates.entrySet())) {
            String namespace = nsEntry.getKey();
            for (Map.Entry<String, ItemStateNode> itemEntry : nsEntry.getValue().entrySet()) {
                String itemName = itemEntry.getKey();
                Item item = VanillaModelManager.Utilities.findItem(namespace, itemName);
                if (item == null) continue;
                if (VanillaModelRegistry.registeredItemModels.containsKey(item)) continue;

                VanillaModelRegistry.registeredItemModels.put(item,
                        new ItemStateItemModel(itemEntry.getValue()));
                CatFrame.logger.debug("[VMM] Registered ItemState: {}:{}", namespace, itemName);
            }
        }

        // 4b: model_mappings 中的 items（跳过已被 ItemState 注册的）
        for (Map.Entry<String, VanillaModelManager.ModelMappings> entry :
                VMMDataLoader.loadedMappings.entrySet()) {
            VanillaModelManager.ModelMappings mappings = entry.getValue();
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

                Item item = VanillaModelManager.Utilities.findItem(entry.getKey(), itemName);
                if (item == null) continue;
                // 跳过已被 ItemState 注册的
                if (VanillaModelRegistry.registeredItemModels.containsKey(item)) continue;

                Map<String, ModelJson.DisplayTransform> display = resolveDisplay(itemEntry.getValue());
                VanillaModelRegistry.registeredItemModels.put(item,
                        new LazyItemModel(itemEntry.getValue(), display));
            }
        }

        // 4c: IItemState (Tier 3 接口发现)
        for (Map.Entry<Item, IItemState> entry : VMMDataLoader.interfaceItemStates.entrySet()) {
            Item item = entry.getKey();
            if (VanillaModelRegistry.registeredItemModels.containsKey(item)) continue;

            // 通过约定路径发现模型
            String registryName = Item.itemRegistry.getNameForObject(item);
            if (registryName == null) continue;
            int colonIdx = registryName.indexOf(':');
            String ns = colonIdx >= 0 ? registryName.substring(0, colonIdx) : "minecraft";
            String name = colonIdx >= 0 ? registryName.substring(colonIdx + 1) : registryName;
            String modelPath = ns + ":item/" + name;

            ModelJson resolved = ModelResolver.resolve(modelPath);
            if (resolved == null) continue;

            Map<String, ModelJson.DisplayTransform> display = resolved.display;
            VanillaModelRegistry.registeredItemModels.put(item,
                    new LazyItemModel(modelPath, display));
            VanillaModelRegistry.persistentItemModels.add(item);
            CatFrame.logger.debug("[VMM] Registered IItemState: {} -> {}",
                    registryName, modelPath);
        }

        // Step 5: Forge IItemRenderer 注册
        java.util.Set<Item> registered = new java.util.HashSet<>();
        int forgeRegistered = 0;

        for (Item item : VanillaModelRegistry.registeredItemModels.keySet()) {
            if (registered.add(item)) {
                MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
                forgeRegistered++;
            }
        }
        for (Block block : VanillaModelRegistry.registeredBlockModels.keySet()) {
            Item item = Item.getItemFromBlock(block);
            if (item != null && registered.add(item)) {
                MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
                forgeRegistered++;
            }
        }

        CatFrame.logger.info("VanillaModelManager: Registered {} block models, {} item models ({} Forge renderers)",
                VanillaModelRegistry.registeredBlockModels.size(),
                VanillaModelRegistry.registeredItemModels.size(), forgeRegistered);
    }

    /**
     * 增量重建 item 模型（item atlas 缝合后调用）。
     * <p>
     * 仅重新创建非 persistent 的 ItemModel wrapper。
     * 由于使用懒模型，无需实际烘焙 — 只需更新注册表中的 wrapper 引用。
     */
    public static void registerItemModels() {
        // 移除非 persistent item models
        VanillaModelRegistry.registeredItemModels.keySet()
                .removeIf(item -> !VanillaModelRegistry.persistentItemModels.contains(item));

        // 重新注册 ItemState 决策树（最高优先）
        for (Map.Entry<String, Map<String, ItemStateNode>> nsEntry :
                VMMDataLoader.loadedItemStates.entrySet()) {
            String namespace = nsEntry.getKey();
            for (Map.Entry<String, ItemStateNode> itemEntry : nsEntry.getValue().entrySet()) {
                Item item = VanillaModelManager.Utilities.findItem(namespace, itemEntry.getKey());
                if (item == null) continue;
                VanillaModelRegistry.registeredItemModels.put(item,
                        new ItemStateItemModel(itemEntry.getValue()));
            }
        }

        // 重新注册 model_mappings items（跳过已被 ItemState 注册的）
        for (Map.Entry<String, VanillaModelManager.ModelMappings> entry :
                VMMDataLoader.loadedMappings.entrySet()) {
            String namespace = entry.getKey();
            VanillaModelManager.ModelMappings mappings = entry.getValue();
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

                Item item = VanillaModelManager.Utilities.findItem(namespace, itemName);
                if (item == null) continue;
                // 跳过已被 ItemState 注册的
                if (VanillaModelRegistry.registeredItemModels.containsKey(item)) continue;

                Map<String, ModelJson.DisplayTransform> display = resolveDisplay(itemEntry.getValue());
                VanillaModelRegistry.registeredItemModels.put(item,
                        new LazyItemModel(itemEntry.getValue(), display));
            }
        }

        // 重新注册 IItemState items（跳过已有手动注册模型的 persistent 项，如 DualRenderItemModel）
        for (Map.Entry<Item, IItemState> entry : VMMDataLoader.interfaceItemStates.entrySet()) {
            Item item = entry.getKey();
            if (VanillaModelRegistry.registeredItemModels.containsKey(item)) continue;

            String registryName = Item.itemRegistry.getNameForObject(item);
            if (registryName == null) continue;
            int colonIdx = registryName.indexOf(':');
            String ns = colonIdx >= 0 ? registryName.substring(0, colonIdx) : "minecraft";
            String name = colonIdx >= 0 ? registryName.substring(colonIdx + 1) : registryName;
            String modelPath = ns + ":item/" + name;

            ModelJson resolved = ModelResolver.resolve(modelPath);
            if (resolved == null) continue;

            Map<String, ModelJson.DisplayTransform> display = resolved.display;
            VanillaModelRegistry.registeredItemModels.put(item,
                    new LazyItemModel(modelPath, display));
            MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
        }

        CatFrame.logger.info("VanillaModelManager: Re-registered {} item models (incremental, lazy)",
                VanillaModelRegistry.registeredItemModels.size());
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 ModelJson 解析 display transforms（仅读 JSON，不烘焙 quads）。
     */
    @Nullable
    private static Map<String, ModelJson.DisplayTransform> resolveDisplay(String modelPath) {
        if (modelPath == null) return null;
        ModelJson resolved = ModelResolver.resolve(modelPath);
        return resolved != null ? resolved.display : null;
    }

    /**
     * 收集所有有 blockstate 的 block 及其 BlockstateJson（快照迭代）。
     */
    private static java.util.List<Map.Entry<Block, BlockstateJson>> collectBlockstateBlocks() {
        java.util.List<Map.Entry<Block, BlockstateJson>> result = new java.util.ArrayList<>();
        for (Map.Entry<String, Map<String, BlockstateJson>> nsEntry :
                new java.util.ArrayList<>(VMMDataLoader.loadedBlockstates.entrySet())) {
            String namespace = nsEntry.getKey();
            for (Map.Entry<String, BlockstateJson> bsEntry : nsEntry.getValue().entrySet()) {
                Block block = VanillaModelManager.Utilities.findBlock(namespace, bsEntry.getKey());
                if (block != null) {
                    result.add(new java.util.AbstractMap.SimpleEntry<>(block, bsEntry.getValue()));
                }
            }
        }
        return result;
    }

    /**
     * Extract the first model path from a blockstate JSON.
     */
    private static String findFirstModelPath(BlockstateJson bs) {
        if (bs.variants != null) {
            for (BlockstateJson.VariantEntry ve : bs.variants.values()) {
                if (ve.isArray()) {
                    if (ve.list != null && !ve.list.isEmpty() && ve.list.get(0).model != null) {
                        return ve.list.get(0).model;
                    }
                } else if (ve.single != null && ve.single.model != null) {
                    return ve.single.model;
                }
            }
        }
        if (bs.multipart != null) {
            for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                if (mpc.apply != null && mpc.apply.model != null) {
                    return mpc.apply.model;
                }
            }
        }
        return null;
    }

}
