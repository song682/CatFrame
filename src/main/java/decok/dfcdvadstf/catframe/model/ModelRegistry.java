package decok.dfcdvadstf.catframe.model;


import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.CatStateDefinition;
import decok.dfcdvadstf.catframe.model.state.item.BlockStateItemState;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.client.MinecraftForgeClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Model registration API extracted from {@link VanillaModelManager}.
 * <p>
 * Responsible for registering BlockStateModels, ItemModels, rotations, and
 * providing the public API for looking up registered models (including
 * GTNHLib-style ItemBlock fallback and Tier 3 auto-discovery).
 */
@SideOnly(Side.CLIENT)
public class ModelRegistry {

    // ==================== 注册表 ====================

    public static final Map<Block, BlockStateModel> registeredBlockModels = new HashMap<>();
    public static final Map<Block, Map<Integer, Integer>> registeredBlockRotations = new HashMap<>();
    public static final Map<Item, IItemStateProvider> registeredItemModels = new HashMap<>();
    public static final Set<Item> persistentItemModels = new HashSet<>();
    static final Set<Item> autoDiscoveryMissCache = new HashSet<>();
    static final Set<Block> randomRotationBlocks = new HashSet<>();
    static final Set<Block> autoOverlayBlocks = new HashSet<>();
    static final Map<Block, CatStateDefinition<?>> blockStateDefinitions = new HashMap<>();

    /**
     * Public API: bake a model path into a BlockStateModelPart (with cache).
     * Used by StateProviderBlockModel and MultipartBlockModel for on-demand baking.
     * 通过 {@link BakedModelCache} 懒烘焙，线程安全。
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
         * 通过 {@link BakedModelCache} 懒烘焙，线程安全。
         */
        public static BlockStateModelPart bakeModelPart(String modelPath, int rotationX, int rotationY) {
            String cacheKey = BakedModelCache.buildKey(modelPath, rotationX, rotationY);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            return part != null ? part : BlockStateModelPart.empty();
        }

        /**
         * Register a BlockStateModel for a block. Overrides any previously registered model.
         */
        public static void registerBlockModel(Block block, BlockStateModel model) {
            registeredBlockModels.put(block, model);
        }

        /**
         * Get the registered BlockStateModel for a block, or null if not registered.
         */
        public static BlockStateModel getBlockModel(Block block) {
            return registeredBlockModels.get(block);
        }

        /**
         * Register a rotation for a block/metadata combination.
         */
        public static void registerBlockRotation(Block block, int metadata, int rotationDeg) {
            registeredBlockRotations.computeIfAbsent(block, k -> new HashMap<>())
                    .put(metadata, rotationDeg);
        }

        /**
         * Mark a block as using random Y rotation based on position.
         */
        public static void markRandomRotation(Block block) {
            randomRotationBlocks.add(block);
        }

        /**
         * Mark a block as using auto-overlay (metadata-indexed model list).
         */
        public static void markAutoOverlay(Block block) {
            autoOverlayBlocks.add(block);
        }

        /**
         * Register an IItemState model for an item.
         * Also immediately registers the Forge IItemRenderer if the model system has been initialized.
         * <p>
         * Manually registered models are marked persistent: they survive
         * {@link VanillaModelManager.Baking#registerItemModels()}, which only clears auto-generated
         * wrappers from {@code model_mappings.json}.
         */
        public static void registerItemModel(Item item, IItemStateProvider model) {
            registeredItemModels.put(item, model);
            persistentItemModels.add(item);
            // 如果烘焙已完成，立即注册 Forge IItemRenderer
            if (ModelManagerDataLoader.initialized) {
                MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
            }
        }

        /**
         * Get the registered IItemState model for an item, or null if not registered.
         * <p>
         * <b>GTNHLib-style fallback:</b> If the item is an {@link ItemBlock} and has no
         * dedicated item model, the block's {@link BlockStateModel} (from
         * {@link ModelRegistry#registeredBlockModels}) is returned wrapped in a {@link BlockStateItemState}
         * with the block's display transforms. This means block items don't need a
         * separate entry in {@link #registeredItemModels} — the block model IS the item model.
         */
        public static IItemStateProvider getRegisteredItemModel(Item item) {
            if (item == null) return null;
            IItemStateProvider itemModel = registeredItemModels.get(item);
            if (itemModel != null) return itemModel;

            // GTNHLib-style: ItemBlock without dedicated item model → use block model
            if (item instanceof ItemBlock) {
                Block block = Block.getBlockFromItem(item);
                if (block != null) {
                    BlockStateModel blockModel = registeredBlockModels.get(block);
                    if (blockModel != null) {
                        return new BlockStateItemState(blockModel);
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
        private static IItemStateProvider autoDiscoverItemModel(Item item) {
            if (autoDiscoveryMissCache.contains(item)) return null;

            String registryName = Item.itemRegistry.getNameForObject(item);
            if (registryName == null) {
                autoDiscoveryMissCache.add(item);
                return null;
            }

            // 分离 namespace:name → namespace:item/name
            int colonIdx = registryName.indexOf(':');
            String namespace = colonIdx >= 0 ? registryName.substring(0, colonIdx) : "minecraft";
            String name = colonIdx >= 0 ? registryName.substring(colonIdx + 1) : registryName;
            String modelPath = namespace + ":item/" + name;

            ModelJson resolved = ModelResolver.resolve(modelPath);
            if (resolved == null) {
                autoDiscoveryMissCache.add(item);
                return null;
            }

            // 发现成功 — 创建懒模型（烘焙由 BakedModelCache 懒执行）
            CatFrame.logger.info("[VMM] Tier 3 auto-discovered item model: {} -> {}", registryName, modelPath);
            // 警告：纹理可能未加入 atlas
            CatFrame.logger.warn("[VMM] Auto-discovered model '{}' texture may not be in atlas " +
                    "(discovered after atlas stitching). Register textures earlier to avoid missingno.",
                    modelPath);

            ItemStateModel wrapper = new ItemStateModel(modelPath);

            registeredItemModels.put(item, wrapper);
            // 注册 Forge IItemRenderer（已初始化阶段）
            if (ModelManagerDataLoader.initialized) {
                MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
            }
            return wrapper;
        }

        /**
         * Check if a block has a JSON model override (either registered model or dynamic state-provider)
         */
        public static boolean hasModel(Block block) {
            return registeredBlockModels.containsKey(block)
                    || ModelManagerDataLoader.stateBlockData.containsKey(block);
        }

        /**
         * Check if an item has a JSON model override
         */
        public static boolean hasItemModel(Item item) {
            return registeredItemModels.containsKey(item);
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
            blockStateDefinitions.put(block, def);
        }

        /**
         * Check if a block has a registered CatStateDefinition.
         */
        public static boolean hasStateDefinition(Block block) {
            return blockStateDefinitions.containsKey(block);
        }

        /**
         * Get the registered CatStateDefinition for a block, or null.
         */
        public static CatStateDefinition<?> getStateDefinition(Block block) {
            return blockStateDefinitions.get(block);
        }
    }