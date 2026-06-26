package decok.dfcdvadstf.catframe.model;

import com.google.gson.Gson;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.state.*;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import java.util.*;

/**
 * Manages vanilla block/item model overrides using JSON model files.
 * Supports two mapping approaches:
 * 1. Blockstates: assets/{namespace}/blockstates/{name}.json (1.8+ format with variants/multipart)
 * 2. Model Mappings: assets/{namespace}/model_mappings.json (simple key-value block/item -> model path)
 * <p>
 * Other mods can register their namespace to participate in model loading.
 */
@SideOnly(Side.CLIENT)
public class VanillaModelManager {
    static final Gson blockstateGson = BlockstateJson.createGson();
    private static final Gson gson = new Gson();

    // ==================== Block Models ====================

    /**
     * Block -> metadata -> list of baked quads
     */
    static final Map<Block, Map<Integer, List<BakedQuad>>> bakedBlockModels = new HashMap<>();
    /**
     * Block -> metadata -> Y rotation degrees
     */
    static final Map<Block, Map<Integer, Integer>> blockRotations = new HashMap<>();

    // ==================== IBlockStateProvider Registry ====================

    /**
     * Registered mod blocks using IBlockStateProvider
     */
    static final List<Block> registeredStateBlocks = new ArrayList<>();
    /**
     * Block -> loaded BlockstateJson
     */
    static final Map<Block, BlockstateJson> stateBlockData = new HashMap<>();

    // ==================== Metadata → Properties Mapper Registry ====================

    /**
     * Block -> metadata-to-properties mapper for vanilla blocks
     */
    static final Map<Block, IMetadataMapper> metadataMappers = new HashMap<>();

    // ==================== Model Bake Cache ====================

    /**
     * Cache key "modelPath@rotY" -> baked quads. Shared by bake path and dynamic path.
     */
    static final Map<String, List<BakedQuad>> bakedModelCache = new HashMap<>();

    // ==================== Item Models ====================

    /**
     * Item -> damage -> list of baked quads
     */
    static final Map<Item, Map<Integer, List<BakedQuad>>> bakedItemModels = new HashMap<>();

    // ==================== JsonBlock Transition Flags ====================

    /**
     * Blocks that should use random Y rotation per position
     */
    static final Set<Block> randomRotationBlocks = new HashSet<>();

    /**
     * Blocks with autoOverlay (metadata-indexed model list)
     */
    static final Set<Block> autoOverlayBlocks = new HashSet<>();

    // ==================== BlockStateModel / ItemModel Registry ====================

    /**
     * Block -> BlockStateModel (new dispatch system)
     */
    static final Map<Block, BlockStateModel> registeredBlockModels = new HashMap<>();

    /**
     * Block -> Map<metadata, rotation-deg> for world rendering (kept for transition)
     */
    static final Map<Block, Map<Integer, Integer>> registeredBlockRotations = new HashMap<>();

    /**
     * Item -> ItemModel (new dispatch system)
     */
    static final Map<Item, ItemModel> registeredItemModels = new HashMap<>();

    /**
     * Items registered via {@link ModelRegistration#registerItemModel} (manual/persistent).
     * These entries survive {@link ModelBaking#rebuildItemModels()} — only auto-generated
     * ItemModelWrappers from {@code model_mappings.json} are cleared on rebuild.
     */
    static final Set<Item> persistentItemModels = new HashSet<>();

    /**
     * Items discovered via {@link IItemJsonModel} interface scanning in
     * {@link DataLoading#init()}. Maps item → its IItemJsonModel reference
     * for deferred baking in {@link ModelBaking#bakeAllModels()}.
     */
    static final Map<Item, IItemJsonModel> interfaceItemModels = new LinkedHashMap<>();

    /**
     * Tier 3 约定路径懒发现的 miss 缓存。命中此集合的 item 不会再次尝试解析。
     */
    static final java.util.Set<Item> autoDiscoveryMissCache = new HashSet<>();

    // ==================== CatStateDefinition Registry ====================

    /**
     * Block -> CatStateDefinition (typed property definitions, v0.3.0)
     */
    static final Map<Block, CatStateDefinition<?>> blockStateDefinitions = new HashMap<>();

    // ==================== Texture Tracking ====================

    /**
     * All block texture paths that need registration on the block atlas (type 0)
     */
    static final Set<String> pendingTextures = new LinkedHashSet<>();
    /**
     * All item texture paths that need registration on the item atlas (type 1)
     */
    static final Set<String> pendingItemTextures = new LinkedHashSet<>();
    /**
     * Texture path -> IIcon after stitching
     */
    static final Map<String, IIcon> textureIcons = new HashMap<>();

    // ==================== Loaded Data ====================

    /**
     * namespace -> blockName -> BlockstateJson
     */
    static final Map<String, Map<String, BlockstateJson>> loadedBlockstates = new HashMap<>();
    /**
     * namespace -> ModelMappings (blocks + items)
     */
    static final Map<String, ModelMappings> loadedMappings = new HashMap<>();
    /**
     * namespace -> blockName -> Map<metadata, Map<property, value>> (from metadata_map.json)
     */
    static final Map<String, Map<String, Map<Integer, Map<String, String>>>> loadedMetadataMaps = new HashMap<>();
    /**
     * Registered namespaces
     */
    static final List<String> namespaces = new ArrayList<>();

    static boolean initialized = false;

    // ==================== Model Mappings Data Class ====================

    public static class ModelMappings {
        public Map<String, String> blocks;
        public Map<String, String> items;
    }

    // ==================== DataLoading ====================
    public static class DataLoading {
    
        public static void init() {
            ModelDataLoader.init();
        }
    
        public static void registerNamespace(String namespace) {
            ModelDataLoader.registerNamespace(namespace);
        }
    
        public static void registerBlock(Block block) {
            ModelDataLoader.registerBlock(block);
        }
    
        public static void registerMetadataMapping(Block block, IMetadataMapper mapper) {
            ModelDataLoader.registerMetadataMapping(block, mapper);
        }
    }

    // ==================== ModelRegistration ====================
    public static class ModelRegistration {

        public static BlockStateModelPart bakeModelPart(String modelPath) {
            return VanillaModelRegistry.bakeModelPart(modelPath);
        }

        public static BlockStateModelPart bakeModelPart(String modelPath, int rotationY) {
            return VanillaModelRegistry.bakeModelPart(modelPath, rotationY);
        }

        public static BlockStateModelPart bakeModelPart(String modelPath, int rotationX, int rotationY) {
            return VanillaModelRegistry.bakeModelPart(modelPath, rotationX, rotationY);
        }

        public static void registerBlockModel(Block block, BlockStateModel model) {
            VanillaModelRegistry.registerBlockModel(block, model);
        }

        public static BlockStateModel getBlockModel(Block block) {
            return VanillaModelRegistry.getBlockModel(block);
        }

        public static void registerBlockRotation(Block block, int metadata, int rotationDeg) {
            VanillaModelRegistry.registerBlockRotation(block, metadata, rotationDeg);
        }

        public static void markRandomRotation(Block block) {
            VanillaModelRegistry.markRandomRotation(block);
        }

        public static void markAutoOverlay(Block block) {
            VanillaModelRegistry.markAutoOverlay(block);
        }

        public static void registerItemModel(Item item, ItemModel model) {
            VanillaModelRegistry.registerItemModel(item, model);
        }

        public static ItemModel getRegisteredItemModel(Item item) {
            return VanillaModelRegistry.getRegisteredItemModel(item);
        }

        public static boolean hasModel(Block block) {
            return VanillaModelRegistry.hasModel(block);
        }

        public static boolean hasItemModel(Item item) {
            return VanillaModelRegistry.hasItemModel(item);
        }

        public static void registerStateDefinition(Block block, CatStateDefinition<?> def) {
            VanillaModelRegistry.registerStateDefinition(block, def);
        }

        public static boolean hasStateDefinition(Block block) {
            return VanillaModelRegistry.hasStateDefinition(block);
        }

        public static CatStateDefinition<?> getStateDefinition(Block block) {
            return VanillaModelRegistry.getStateDefinition(block);
        }
    }

    // ==================== PublicRenderAPI ====================
    public static class PublicRenderAPI {

        public static boolean renderBlock(IBlockAccess world, int x, int y, int z, Block block, RenderBlocks renderer) {
            return VanillaRenderDispatcher.renderBlock(world, x, y, z, block, renderer);
        }

        public static void renderItem(ItemStack stack) {
            VanillaRenderDispatcher.renderItem(stack);
        }

        public static void renderItem(Item item, int damage) {
            VanillaRenderDispatcher.renderItem(item, damage);
        }

        public static void renderItemInHand(ItemStack stack, boolean isFirstPerson) {
            VanillaRenderDispatcher.renderItemInHand(stack, isFirstPerson);
        }

        public static void renderItemInHand(ItemStack stack) {
            VanillaRenderDispatcher.renderItemInHand(stack);
        }

        public static void renderItemInHand(Item item, int damage) {
            VanillaRenderDispatcher.renderItemInHand(item, damage);
        }

        public static void renderDroppedItem(ItemStack stack) {
            VanillaRenderDispatcher.renderDroppedItem(stack);
        }

        public static void renderDroppedBlock(ItemStack stack, IBlockAccess world, int x, int y, int z, Block block) {
            VanillaRenderDispatcher.renderDroppedBlock(stack, world, x, y, z, block);
        }
    }
}

