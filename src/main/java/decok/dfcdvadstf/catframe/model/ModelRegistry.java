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
 * Items (block items included) without a registered model are NOT force-converted
 * to {@code builtin/missing}; {@link #getRegisteredItemModel} returns {@code null}
 * for them and they fall back to vanilla item rendering — aligning with
 * {@link RenderJsonItemModel}'s documented contract and the "strategy B" policy
 * (only explicitly registered models are driven by the CatFrame pipeline).
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
         * Get the registered IItemState model for an item, or {@code null} if none.
         * <p>
         * Only models explicitly registered (from {@code items/{name}.json} via
         * {@link #registerItemModel}, from {@code model_mappings.json}, or via an
         * {@code ITEM_MODEL} component override) are returned. Any item — block item
         * or not — without such a registration returns {@code null} and falls back to
         * vanilla item rendering.
         * <p>
         * This method intentionally does NOT force block items to {@code builtin/missing}:
         * doing so would (a) contradict {@link RenderJsonItemModel}'s contract, and
         * (b) permanently hijack any block item merely queried here into showing the
         * missingno sprite instead of its vanilla appearance. Block items that want
         * CatFrame rendering must ship an {@code items/{name}.json}.
         */
        public static IItemStateProvider getRegisteredItemModel(Item item) {
            if (item == null) return null;
            // 只返回显式注册的物品模型；未注册的物品（含 ItemBlock）返回 null，退回原版渲染。
            return registeredItemModels.get(item);
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