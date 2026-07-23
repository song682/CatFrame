package decok.dfcdvadstf.catframe.model;


import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.core.component.DataComponents;
import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.CatStateDefinition;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
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
 * providing the public API for looking up registered models.
 * <p>
 * Item-context rendering (hand / GUI / dropped) is driven exclusively by
 * registered {@link IItemStateProvider}s loaded from {@code items/{name}.json}.
 * ItemBlocks whose {@code items/{name}.json} is missing fall back to the
 * {@code builtin/missing} model (never to vanilla rendering).
 */
@SideOnly(Side.CLIENT)
public class ModelRegistry {

    // ==================== 注册表 ====================

    public static final Map<Block, BlockStateModel> registeredBlockModels = new HashMap<>();
    public static final Map<Block, Map<Integer, Integer>> registeredBlockRotations = new HashMap<>();
    public static final Map<Item, IItemStateProvider> registeredItemModels = new HashMap<>();
    public static final Set<Item> persistentItemModels = new HashSet<>();
    /** items with {@code oversized_in_gui=true} — GUI 中允许模型几何溢出槽位（走 PiP 通道，不裁剪不钳制）。 */
    public static final Set<Item> oversizedItems = new HashSet<>();
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
         * Get the registered IItemState model for an item.
         * <p>
         * Lookup order:
         * <ol>
         *   <li>A model registered from {@code items/{name}.json} (via
         *       {@link #registerItemModel}) — returned as-is.</li>
         *   <li>Otherwise, if the item is an {@link ItemBlock}, fall back to the
         *       {@code builtin/missing} model. Every ItemBlock is required to ship an
         *       {@code items/{name}.json}; when the author omits it the block item shows
         *       the missingno model rather than falling back to vanilla rendering. The
         *       fallback model is cached and its Forge {@code IItemRenderer} registered so
         *       later lookups are cheap.</li>
         *   <li>Otherwise (non-block item without a registered model) returns {@code null},
         *       letting the item fall back to vanilla rendering.</li>
         * </ol>
         */
        public static IItemStateProvider getRegisteredItemModel(Item item) {
            if (item == null) return null;
            IItemStateProvider itemModel = registeredItemModels.get(item);
            if (itemModel != null) return itemModel;

            // 方块物品缺省回退：每个 ItemBlock 都必须编写 items/{name}.json；
            // 作者遗漏时回退到 builtin/missing（紫黑 missingno），使方块物品永不落回原版渲染。
            if (item instanceof ItemBlock) {
                ItemStateModel missing = new ItemStateModel("builtin/missing");
                registeredItemModels.put(item, missing);
                if (ModelManagerDataLoader.initialized) {
                    MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
                }
                return missing;
            }

            // 非方块物品：无注册模型则返回 null，回退原版物品渲染。
            return null;
        }

        /**
         * 读取物品的 {@code ITEM_MODEL} 组件覆写值（per-item 默认原型）。
         * <p>
         * 对标原版 {@code DataComponents.ITEM_MODEL}：该值是一个模型映射 ID
         * （{@code "命名空间:路径"}），解析为 {@code assets/<命名空间>/items/<路径>.json}。
         *
         * @return 模型映射 ID，未设置组件时返回 {@code null}
         */
        public static String getItemModelOverride(Item item) {
            if (item == null) return null;
            return DataComponents.getDefaults(item).get(DataComponents.ITEM_MODEL);
        }

        /**
         * 将物品的 {@code ITEM_MODEL} 组件覆写解析为可渲染的物品模型。
         * <p>
         * 查找 {@link ModelManagerDataLoader#loadedItemStates} 中 {@code ns:path} 对应的
         * ItemState 决策树；值存在但无法解析时返回 {@code builtin/missing}
         * （对标原版 item_model「无法解析则使用无效模型」的语义）。
         *
         * @return 覆写模型；未设置 {@code ITEM_MODEL} 组件时返回 {@code null}
         */
        public static IItemStateProvider resolveItemModelOverride(Item item) {
            String modelId = getItemModelOverride(item);
            if (modelId == null) return null;

            String namespace;
            String path;
            int sep = modelId.indexOf(':');
            if (sep >= 0) {
                namespace = modelId.substring(0, sep);
                path = modelId.substring(sep + 1);
            } else {
                namespace = "minecraft";
                path = modelId;
            }

            Map<String, ItemStateNode> nsStates = ModelManagerDataLoader.loadedItemStates.get(namespace);
            ItemStateNode node = nsStates != null ? nsStates.get(path) : null;
            if (node != null) {
                return new ItemStateModel(node);
            }
            // 值存在但无法解析 → 无效模型（builtin/missing）
            return new ItemStateModel("builtin/missing");
        }

        /**
         * Check if an item opts into oversized GUI rendering ({@code oversized_in_gui=true}).
         * <p>
         * When true, {@code GuiGraphicsExtractor.item()} routes the stack to the PiP
         * oversized channel (natural size, no slot scissor, no clamp).
         */
        public static boolean isOversizedInGui(Item item) {
            return item != null && oversizedItems.contains(item);
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