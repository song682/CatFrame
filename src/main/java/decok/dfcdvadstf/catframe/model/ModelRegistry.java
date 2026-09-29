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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model registration API extracted from {@link VanillaModelManager}.
 * <p>
 * Responsible for registering BlockStateModels, ItemModels, rotations, and
 * providing the public API for looking up registered models.
 * <p>
 * Item-context rendering (hand / GUI / dropped) is driven exclusively by
 * registered {@link IItemStateProvider}s loaded from {@code items/{name}.json}.
 * {@link #getRegisteredItemModel} never returns {@code null}: items without an
 * explicit model receive the {@code builtin/missing} model (purple-black MissingNo),
 * aligning with the modern design where the model system has no "fall back to vanilla"
 * path (see {@link decok.dfcdvadstf.catframe.model.core.BuiltinMissingModel}).
 * <p>
 * Use {@link #hasItemModel} to determine whether an item is explicitly in the
 * CatFrame pipeline before deciding to intercept vanilla rendering.
 */
@SideOnly(Side.CLIENT)
public class ModelRegistry {

    // ==================== Registry ====================

    /** builtin/missing singleton — mirrors modern BuiltinMissingModel, fallback for any item with no model. */
    private static final IItemStateProvider MISSING_MODEL = new ItemStateModel("builtin/missing");

    public static final Map<Block, BlockStateModel> registeredBlockModels = new ConcurrentHashMap<>();
    public static final Map<Block, Map<Integer, Integer>> registeredBlockRotations = new ConcurrentHashMap<>();
    public static final Map<Item, IItemStateProvider> registeredItemModels = new ConcurrentHashMap<>();
    public static final Set<Item> persistentItemModels = ConcurrentHashMap.newKeySet();
    /** items with {@code oversized_in_gui=true} — GUI models may overflow slot (goes through PiP channel, no clip/clamp). */
    public static final Set<Item> oversizedItems = ConcurrentHashMap.newKeySet();
    static final Set<Block> randomRotationBlocks = ConcurrentHashMap.newKeySet();
    static final Set<Block> autoOverlayBlocks = ConcurrentHashMap.newKeySet();
    static final Map<Block, CatStateDefinition<?>> blockStateDefinitions = new ConcurrentHashMap<>();

    /**
     * Public API: bake a model path into a BlockStateModelPart (with cache).
     * Used by StateProviderBlockModel and MultipartBlockModel for on-demand baking.
     * Via {@link BakedModelCache} lazy bake, thread-safe.
     */
    public static BlockStateModelPart bakeModelPart(String modelPath) {
            return bakeModelPart(modelPath, 0);
        }

        /**
         * Public API: bake a model path into a BlockStateModelPart with Y rotation.
         */
        public static BlockStateModelPart bakeModelPart(String modelPath, float rotationY) {
            return bakeModelPart(modelPath, 0, rotationY);
        }

/**
     * Public API: bake a model path into a BlockStateModelPart with X and Y rotation.
     * [W3] Supports x rotation field in blockstate.
     * Via {@link BakedModelCache} lazy bake, thread-safe.
     */
        public static BlockStateModelPart bakeModelPart(String modelPath, float rotationX, float rotationY) {
            String cacheKey = BakedModelCache.buildKey(modelPath, rotationX, rotationY);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            return part != null ? part : BlockStateModelPart.empty();
        }

/**
     * Public API: bake a model path into a BlockStateModelPart with X, Y and Z rotation.
     * Via {@link BakedModelCache} lazy bake, thread-safe.
     */
        public static BlockStateModelPart bakeModelPart(String modelPath, float rotationX, float rotationY, float rotationZ) {
            String cacheKey = BakedModelCache.buildKey(modelPath, rotationX, rotationY, rotationZ);
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
            registeredBlockRotations.computeIfAbsent(block, k -> new ConcurrentHashMap<>())
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
            // If baking done, immediately register Forge IItemRenderer
            if (ModelManagerDataLoader.initialized) {
                MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
            }
        }

/**
     * Get the item model for rendering — never returns {@code null}.
     * <p>
     * Returns the explicitly registered model (from {@code items/{name}.json},
     * {@code model_mappings.json}, {@code IItemStateProvider} scan, or
     * {@code ITEM_MODEL} component override). If no model is registered,
     * returns the {@code builtin/missing} model (purple-black MissingNo).
     * <p>
     * Mirrors modern design ({@link decok.dfcdvadstf.catframe.model.core.BuiltinMissingModel}):
     * item model system has no "fall back to vanilla" path; item either has an explicit model
     * or renders MissingNo. Use {@link #hasItemModel} to check if item is in CatFrame pipeline.
     */
        public static IItemStateProvider getRegisteredItemModel(Item item) {
            if (item == null) return MISSING_MODEL;
            IItemStateProvider model = registeredItemModels.get(item);
            return model != null ? model : MISSING_MODEL;
        }

/**
     * Read item's {@code ITEM_MODEL} component override value (per-item default prototype).
     * <p>
     * Mirrors vanilla {@code DataComponents.ITEM_MODEL}: value is a model mapping ID
     * ({@code "namespace:path"}), resolves to {@code assets/<namespace>/items/<path>.json}.
     *
     * @return model mapping ID, or {@code null} if component not set
     */
        public static String getItemModelOverride(Item item) {
            if (item == null) return null;
            return DataComponents.getDefaults(item).get(DataComponents.ITEM_MODEL);
        }

/**
     * Resolve item's {@code ITEM_MODEL} component override into a renderable item model.
     * <p>
     * Looks up {@code ns:path} in {@link ModelManagerDataLoader#loadedItemStates} for
     * the ItemState decision tree; value present but unresolvable returns {@code builtin/missing}
     * (mirrors vanilla item_model "unresolvable → missing model" semantics).
     *
     * @return override model; {@code null} if {@code ITEM_MODEL} component not set
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
            // Value present but unresolvable → missing model (builtin/missing)
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