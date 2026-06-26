package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.CatStateDefinition;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model registration API extracted from {@link VanillaModelManager}.
 * <p>
 * Responsible for registering BlockStateModels, ItemModels, rotations, and
 * providing the public API for looking up registered models (including
 * GTNHLib-style ItemBlock fallback and Tier 3 auto-discovery).
 */
@SideOnly(Side.CLIENT)
public class VanillaModelRegistry {

    /**
     * Public API: bake a model path into a BlockStateModelPart (with cache).
     * Used by StateProviderBlockModel and MultipartBlockModel for on-demand baking.
     */
    public static BlockStateModelPart bakeModelPart(String modelPath) {
        return bakeModelPart(modelPath, 0);
    }

    /**
     * Public API: bake a model path into a BlockStateModelPart with Y rotation.
     */
    public static BlockStateModelPart bakeModelPart(String modelPath, int rotationY) {
        return bakeModelPart(modelPath, 0, rotationY);
    }

    /**
     * Public API: bake a model path into a BlockStateModelPart with X and Y rotation.
     * [W3] 支持 blockstate 中的 x 旋转字段。
     */
    public static BlockStateModelPart bakeModelPart(String modelPath, int rotationX, int rotationY) {
        List<BakedQuad> quads = VanillaModelModelBaking.bakeModel(modelPath, rotationX, rotationY);
        if (quads == null) return BlockStateModelPart.empty();
        return BlockStateModelPart.fromQuads(quads);
    }

    /**
     * Register a BlockStateModel for a block. Overrides any previously registered model.
     */
    public static void registerBlockModel(Block block, BlockStateModel model) {
        VanillaModelManager.registeredBlockModels.put(block, model);
    }

    /**
     * Get the registered BlockStateModel for a block, or null if not registered.
     */
    public static BlockStateModel getBlockModel(Block block) {
        return VanillaModelManager.registeredBlockModels.get(block);
    }

    /**
     * Register a rotation for a block/metadata combination.
     */
    public static void registerBlockRotation(Block block, int metadata, int rotationDeg) {
        VanillaModelManager.registeredBlockRotations.computeIfAbsent(block, k -> new HashMap<>())
                .put(metadata, rotationDeg);
    }

    /**
     * Mark a block as using random Y rotation based on position.
     * (Transition support for JsonBlock blocks.)
     */
    public static void markRandomRotation(Block block) {
        VanillaModelManager.randomRotationBlocks.add(block);
    }

    /**
     * Mark a block as using auto-overlay (metadata-indexed model list).
     * (Transition support for JsonBlock blocks.)
     */
    public static void markAutoOverlay(Block block) {
        VanillaModelManager.autoOverlayBlocks.add(block);
    }

    /**
     * Register an ItemModel for an item.
     * Also immediately registers the Forge IItemRenderer if the model system has been initialized.
     * <p>
     * Manually registered models are marked persistent: they survive
     * {@link VanillaModelModelBaking#rebuildItemModels()}, which only clears auto-generated
     * ItemModelWrappers from {@code model_mappings.json}.
     */
    public static void registerItemModel(Item item, ItemModel model) {
        VanillaModelManager.registeredItemModels.put(item, model);
        VanillaModelManager.persistentItemModels.add(item);
        // 如果烘焙已完成，立即注册 Forge IItemRenderer
        if (VanillaModelManager.initialized) {
            MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
        }
    }

    /**
     * Get the registered ItemModel for an item, or null if not registered.
     * <p>
     * <b>GTNHLib-style fallback:</b> If the item is an {@link ItemBlock} and has no
     * dedicated ItemModel, the block's {@link BlockStateModel} (from
     * {@link VanillaModelRegistry#registeredBlockModels}) is returned wrapped in an {@link ItemModelWrapper}
     * with the block's display transforms. This means block items don't need a
     * separate entry in {@link #registeredItemModels} — the block model IS the item model.
     */
    public static ItemModel getRegisteredItemModel(Item item) {
        if (item == null) return null;
        ItemModel itemModel = VanillaModelManager.registeredItemModels.get(item);
        if (itemModel != null) return itemModel;

        // GTNHLib-style: ItemBlock without dedicated item model → use block model
        if (item instanceof net.minecraft.item.ItemBlock) {
            Block block = Block.getBlockFromItem(item);
            if (block != null) {
                BlockStateModel blockModel = VanillaModelManager.registeredBlockModels.get(block);
                if (blockModel == null && VanillaModelManager.bakedBlockModels.containsKey(block)) {
                    // Block was baked but not registered as BlockStateModel yet — build on-demand
                    Map<Integer, List<BakedQuad>> metaMap = VanillaModelManager.bakedBlockModels.get(block);
                    Map<Integer, BlockStateModelPart> partMap = new HashMap<>();
                    for (Map.Entry<Integer, List<BakedQuad>> me : metaMap.entrySet()) {
                        partMap.put(me.getKey(), BlockStateModelPart.fromQuads(me.getValue()));
                    }
                    BlockStateModelPart fallback = partMap.get(0);
                    if (fallback == null) {
                        fallback = partMap.values().stream().findFirst().orElse(BlockStateModelPart.empty());
                    }
                    blockModel = new MetadataBlockModel(partMap, fallback);
                }
                if (blockModel != null) {
                    // display=null is fine: quads already carry modelDisplay from baking
                    return new ItemModelWrapper(blockModel);
                }
            }
        }

        // Tier 3: 约定路径懒发现 (namespace:item/{name}.json)
        return autoDiscoverItemModel(item);
    }

    /**
     * Tier 3: 尝试通过约定路径 {@code namespace:item/{name}.json} 懒发现并烘焙 item 模型。
     * <p>成功则缓存到 {@code registeredItemModels} 并注册 Forge IItemRenderer；
     * 失败则记入 {@code autoDiscoveryMissCache} 不再重试。
     * <p><b>已知限制</b>：懒发现发生在 atlas 缝合之后，纹理会显示 missingno，
     * 日志会警告用户应预先注册纹理。
     */
    private static ItemModel autoDiscoverItemModel(Item item) {
        if (VanillaModelManager.autoDiscoveryMissCache.contains(item)) return null;

        String registryName = Item.itemRegistry.getNameForObject(item);
        if (registryName == null) {
            VanillaModelManager.autoDiscoveryMissCache.add(item);
            return null;
        }

        // 分离 namespace:name → namespace:item/name
        int colonIdx = registryName.indexOf(':');
        String namespace = colonIdx >= 0 ? registryName.substring(0, colonIdx) : "minecraft";
        String name = colonIdx >= 0 ? registryName.substring(colonIdx + 1) : registryName;
        String modelPath = namespace + ":item/" + name;

        ModelJson resolved = ModelResolver.resolve(modelPath);
        if (resolved == null) {
            VanillaModelManager.autoDiscoveryMissCache.add(item);
            return null;
        }

        // 发现成功 — 烘焙并缓存
        CatFrame.logger.info("[VMM] Tier 3 auto-discovered item model: {} -> {}", registryName, modelPath);
        // 警告：纹理可能未加入 atlas
        CatFrame.logger.warn("[VMM] Auto-discovered model '{}' texture may not be in atlas " +
                "(discovered after atlas stitching). Register textures earlier to avoid missingno.",
                modelPath);

        List<BakedQuad> quads = VanillaModelModelBaking.bakeModel(modelPath, 0);
        if (quads == null || quads.isEmpty()) {
            VanillaModelManager.autoDiscoveryMissCache.add(item);
            return null;
        }

        BlockStateModelPart part = BlockStateModelPart.fromQuads(quads);
        Map<String, ModelJson.DisplayTransform> display = resolved.display;
        ItemModelWrapper wrapper = new ItemModelWrapper(part, display);

        VanillaModelManager.registeredItemModels.put(item, wrapper);
        // 注册 Forge IItemRenderer（已初始化阶段）
        if (VanillaModelManager.initialized) {
            net.minecraftforge.client.MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
        }
        return wrapper;
    }

    /**
     * Check if a block has a JSON model override (either static bake or dynamic state-provider)
     */
    public static boolean hasModel(Block block) {
        return VanillaModelManager.bakedBlockModels.containsKey(block)
                || VanillaModelManager.stateBlockData.containsKey(block)
                || VanillaModelManager.registeredBlockModels.containsKey(block);
    }

    /**
     * Check if an item has a JSON model override
     */
    public static boolean hasItemModel(Item item) {
        return VanillaModelManager.bakedItemModels.containsKey(item)
                || VanillaModelManager.registeredItemModels.containsKey(item);
    }

    // ==================== CatStateDefinition API (v0.3.0) ====================

    /**
     * Register a type-safe CatStateDefinition for a block. This enables property-based
     * model dispatch using CatBlockState instead of raw String maps.
     *
     * @param block the block
     * @param def   the CatStateDefinition defining typed properties
     */
    public static void registerStateDefinition(Block block, CatStateDefinition<?> def) {
        VanillaModelManager.blockStateDefinitions.put(block, def);
    }

    /**
     * Check if a block has a registered CatStateDefinition.
     */
    public static boolean hasStateDefinition(Block block) {
        return VanillaModelManager.blockStateDefinitions.containsKey(block);
    }

    /**
     * Get the registered CatStateDefinition for a block, or null.
     */
    public static CatStateDefinition<?> getStateDefinition(Block block) {
        return VanillaModelManager.blockStateDefinitions.get(block);
    }
}
