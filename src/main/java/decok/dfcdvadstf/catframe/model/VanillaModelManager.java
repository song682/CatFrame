package decok.dfcdvadstf.catframe.model;

import com.google.gson.Gson;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.RenderJsonItemRenderer;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.*;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.TextureStitchEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
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
    private static final Gson blockstateGson = BlockstateJson.createGson();
    private static final Gson gson = new Gson();

    // ==================== Block Models ====================

    /**
     * Block -> metadata -> list of baked quads
     */
    private static final Map<Block, Map<Integer, List<BakedQuad>>> bakedBlockModels = new HashMap<>();
    /**
     * Block -> metadata -> Y rotation degrees
     */
    private static final Map<Block, Map<Integer, Integer>> blockRotations = new HashMap<>();

    // ==================== IBlockStateProvider Registry ====================

    /**
     * Registered mod blocks using IBlockStateProvider
     */
    private static final List<Block> registeredStateBlocks = new ArrayList<>();
    /**
     * Block -> loaded BlockstateJson
     */
    private static final Map<Block, BlockstateJson> stateBlockData = new HashMap<>();

    // ==================== Metadata → Properties Mapper Registry ====================

    /**
     * Block -> metadata-to-properties mapper for vanilla blocks
     */
    private static final Map<Block, IMetadataMapper> metadataMappers = new HashMap<>();

    // ==================== Model Bake Cache ====================

    /**
     * Cache key "modelPath@rotY" -> baked quads. Shared by bake path and dynamic path.
     */
    private static final Map<String, List<BakedQuad>> bakedModelCache = new HashMap<>();

    // ==================== Item Models ====================

    /**
     * Item -> damage -> list of baked quads
     */
    private static final Map<Item, Map<Integer, List<BakedQuad>>> bakedItemModels = new HashMap<>();

    // ==================== JsonBlock Transition Flags ====================

    /**
     * Blocks that should use random Y rotation per position
     */
    private static final Set<Block> randomRotationBlocks = new HashSet<>();

    /**
     * Blocks with autoOverlay (metadata-indexed model list)
     */
    private static final Set<Block> autoOverlayBlocks = new HashSet<>();

    // ==================== BlockStateModel / ItemModel Registry ====================

    /**
     * Block -> BlockStateModel (new dispatch system)
     */
    private static final Map<Block, BlockStateModel> registeredBlockModels = new HashMap<>();

    /**
     * Block -> Map<metadata, rotation-deg> for world rendering (kept for transition)
     */
    private static final Map<Block, Map<Integer, Integer>> registeredBlockRotations = new HashMap<>();

    /**
     * Item -> ItemModel (new dispatch system)
     */
    private static final Map<Item, ItemModel> registeredItemModels = new HashMap<>();

    // ==================== CatStateDefinition Registry ====================

    /**
     * Block -> CatStateDefinition (typed property definitions, v0.3.0)
     */
    private static final Map<Block, CatStateDefinition<?>> blockStateDefinitions = new HashMap<>();

    // ==================== Texture Tracking ====================

    /**
     * All block texture paths that need registration on the block atlas (type 0)
     */
    private static final Set<String> pendingTextures = new LinkedHashSet<>();
    /**
     * All item texture paths that need registration on the item atlas (type 1)
     */
    private static final Set<String> pendingItemTextures = new LinkedHashSet<>();
    /**
     * Texture path -> IIcon after stitching
     */
    private static final Map<String, IIcon> textureIcons = new HashMap<>();

    // ==================== Loaded Data ====================

    /**
     * namespace -> blockName -> BlockstateJson
     */
    private static final Map<String, Map<String, BlockstateJson>> loadedBlockstates = new HashMap<>();
    /**
     * namespace -> ModelMappings (blocks + items)
     */
    private static final Map<String, ModelMappings> loadedMappings = new HashMap<>();
    /**
     * namespace -> blockName -> Map<metadata, Map<property, value>> (from metadata_map.json)
     */
    private static final Map<String, Map<String, Map<Integer, Map<String, String>>>> loadedMetadataMaps = new HashMap<>();
    /**
     * Registered namespaces
     */
    private static final List<String> namespaces = new ArrayList<>();

    private static boolean initialized = false;

    // ==================== Model Mappings Data Class ====================

    public static class ModelMappings {
        public Map<String, String> blocks;
        public Map<String, String> items;
    }

    // ==================== DataLoading ====================
    public static class DataLoading {

        /**
         * Initialize the model manager. Call during preInit.
         * Scans all registered namespaces for blockstates and model_mappings.
         */
        public static void init() {
            if (initialized) return;
            initialized = true;

            // Always include minecraft namespace
            registerNamespace("minecraft");

            CatFrame.logger.info("VanillaModelManager: Initializing...");

            for (String namespace : namespaces) {
                loadNamespace(namespace);
            }

            // Load blockstates for all registered IBlockStateProvider blocks
            for (Block block : registeredStateBlocks) {
                loadStateProviderBlock(block);
            }

            CatFrame.logger.info("VanillaModelManager: Loaded {} namespaces, {} state-blocks, {} block-textures pending, {} item-textures pending",
                    namespaces.size(), registeredStateBlocks.size(), pendingTextures.size(), pendingItemTextures.size());
        }

        /**
         * Register a namespace for model loading.
         * Other mods should call this in preInit to participate.
         */
        public static void registerNamespace(String namespace) {
            if (!namespaces.contains(namespace)) {
                namespaces.add(namespace);
                ModelResolver.registerNamespace(namespace);
            }
        }

        /**
         * Register a block that implements IBlockStateProvider for blockstate-driven rendering.
         * Call this during preInit (before init() completes).
         * <p>
         * The block's blockstate JSON will be loaded from:
         * assets/{namespace}/blockstates/{name}.json
         * <p>
         * On each render, the block's getStateProperties() is called to determine which
         * variant to use.
         *
         * @param block a Block that implements IBlockStateProvider
         * @throws IllegalArgumentException if block does not implement IBlockStateProvider
         */
        public static void registerBlock(Block block) {
            if (!(block instanceof IBlockStateProvider)) {
                throw new IllegalArgumentException("Block must implement IBlockStateProvider: " + block.getClass().getName());
            }
            if (!registeredStateBlocks.contains(block)) {
                registeredStateBlocks.add(block);
                // Ensure the namespace is registered
                String ns = ((IBlockStateProvider) block).getBlockstateNamespace();
                registerNamespace(ns);

                // If already initialized, load immediately
                if (initialized) {
                    loadStateProviderBlock(block);
                }
            }
        }

        /**
         * Register a metadata-to-properties mapper for a vanilla block.
         * This allows blockstate JSON files to use 1.16.5-style property keys
         * (e.g. {@code "variant=granite"}) instead of raw metadata numbers.
         *
         * <p>The mapper is called during baking — for each metadata value 0–15,
         * the returned property map is used to look up the variant entry in the
         * blockstate JSON.  Function-based registration always takes priority
         * over {@code metadata_map.json} entries.
         *
         * <p>This has no effect on blocks that implement {@link IBlockStateProvider}.
         *
         * @param block  the vanilla block (must NOT be an {@link IBlockStateProvider})
         * @param mapper function that converts metadata to a property map
         */
        public static void registerMetadataMapping(Block block, IMetadataMapper mapper) {
            if (mapper == null) {
                CatFrame.logger.warn("VanillaModelManager: registerMetadataMapping called with null mapper for {}", block);
                return;
            }
            if (!metadataMappers.containsKey(block)) {
                metadataMappers.put(block, mapper);
                CatFrame.logger.debug("VanillaModelManager: registered metadata mapper for {}", block);
            }
        }

        /**
         * Load blockstate data for a registered IBlockStateProvider block.
         */
        private static void loadStateProviderBlock(Block block) {
            IBlockStateProvider provider = (IBlockStateProvider) block;
            String namespace = provider.getBlockstateNamespace();
            String name = provider.getBlockstateName();

            BlockstateJson bs = loadSingleBlockstate(namespace, name);
            if (bs != null) {
                stateBlockData.put(block, bs);
                CatFrame.logger.info("Loaded blockstate for state-block: {}:{}", namespace, name);
            } else {
                CatFrame.logger.warn("Failed to load blockstate for state-block: {}:{}", namespace, name);
            }
        }

        /**
         * Load all model data from a namespace.
         */
        private static void loadNamespace(String namespace) {
            // Load model_mappings.json
            loadModelMappings(namespace);
            // Load blockstates
            loadBlockstatesFromMappings(namespace);
            // Load metadata_map.json (auxiliary mapping for vanilla blocks)
            loadMetadataMaps(namespace);
        }

        /**
         * Load model_mappings.json for a namespace.
         */
        private static void loadModelMappings(String namespace) {
            String path = "/assets/" + namespace + "/model_mappings.json";
            try (InputStream stream = VanillaModelManager.class.getResourceAsStream(path)) {
                if (stream == null) return;
                InputStreamReader reader = new InputStreamReader(stream);
                ModelMappings mappings = gson.fromJson(reader, ModelMappings.class);
                if (mappings != null) {
                    loadedMappings.put(namespace, mappings);
                    CatFrame.logger.info("Loaded model_mappings.json for namespace: {}", namespace);

                    // Resolve all models and collect textures
                    if (mappings.blocks != null) {
                        for (String modelPath : mappings.blocks.values()) {
                            TextureManagement.collectTexturesFromModel(modelPath, false);
                        }
                    }
                    if (mappings.items != null) {
                        for (String modelPath : mappings.items.values()) {
                            TextureManagement.collectTexturesFromModel(modelPath, true);
                        }
                    }
                }
            } catch (Exception e) {
                CatFrame.logger.debug("No model_mappings.json for namespace {}: {}", namespace, e.getMessage());
            }
        }

        /**
         * Load blockstate files referenced from model_mappings, or scan for them.
         */
        private static void loadBlockstatesFromMappings(String namespace) {
            Map<String, BlockstateJson> nsBlockstates = new HashMap<>();

            // Try to load an index file first
            String indexPath = "/assets/" + namespace + "/blockstates/_index.json";
            try (InputStream stream = VanillaModelManager.class.getResourceAsStream(indexPath)) {
                if (stream != null) {
                    InputStreamReader reader = new InputStreamReader(stream);
                    String[] names = gson.fromJson(reader, String[].class);
                    for (String name : names) {
                        BlockstateJson bs = loadSingleBlockstate(namespace, name);
                        if (bs != null) nsBlockstates.put(name, bs);
                    }
                }
            } catch (Exception ignored) {
            }

            // Also try to load blockstates for any blocks in model_mappings
            ModelMappings mappings = loadedMappings.get(namespace);
            if (mappings != null && mappings.blocks != null) {
                for (String blockName : mappings.blocks.keySet()) {
                    if (!nsBlockstates.containsKey(blockName)) {
                        BlockstateJson bs = loadSingleBlockstate(namespace, blockName);
                        if (bs != null) nsBlockstates.put(blockName, bs);
                    }
                }
            }

            // Try common vanilla block names if minecraft namespace
            if (namespace.equals("minecraft") && nsBlockstates.isEmpty()) {
                String[] commonBlocks = {
                        "stone", "dirt", "grass", "cobblestone", "planks", "sand", "gravel",
                        "gold_ore", "iron_ore", "coal_ore", "log", "log2", "leaves", "leaves2", "glass",
                        "lapis_ore", "lapis_block", "sandstone", "wool", "gold_block",
                        "iron_block", "brick_block", "tnt", "bookshelf", "mossy_cobblestone",
                        "obsidian", "diamond_ore", "diamond_block", "crafting_table",
                        "furnace", "redstone_ore", "ice", "snow", "clay", "netherrack",
                        "soul_sand", "glowstone", "stonebrick", "melon_block", "nether_brick",
                        "end_stone", "emerald_ore", "emerald_block", "quartz_block",
                        "hardened_clay", "stained_hardened_clay", "hay_block", "coal_block",
                        "cobblestone_wall", "stained_glass", "trapdoor",
                        "torch", "redstone_torch", "unlit_redstone_torch", "redstone_wire",
                        "unpowered_repeater", "powered_repeater",
                        "unpowered_comparator", "powered_comparator",
                        "redstone_lamp", "lit_redstone_lamp", "redstone_block",
                        "cauldron", "double_stone_slab", "stone_slab",
                        "double_wooden_slab", "wooden_slab", "cactus", "anvil",

                        // Additional blocks from item.csv (Has Block=true)
                        "sponge", "noteblock", "jukebox", "mycelium", "packed_ice",
                        "quartz_ore", "command_block", "beacon",
                        "monster_egg", "mob_spawner",
                        "cake", "glass_pane", "stained_glass_pane",
                        "dispenser", "dropper", "piston", "sticky_piston",
                        "lit_furnace", "pumpkin", "lit_pumpkin",
                        "sapling", "tallgrass", "deadbush", "yellow_flower", "red_flower",
                        "brown_mushroom", "red_mushroom",
                        "carrots", "potatoes", "cocoa", "double_plant", "waterlily", "vine",
                        "brown_mushroom_block", "red_mushroom_block",
                        "lever", "stone_button", "wooden_button",
                        "stone_pressure_plate", "wooden_pressure_plate",
                        "light_weighted_pressure_plate", "heavy_weighted_pressure_plate",
                        "daylight_detector", "tripwire_hook", "hopper",
                        "rail", "golden_rail", "detector_rail", "activator_rail",
                        "oak_stairs", "stone_stairs", "brick_stairs", "stone_brick_stairs",
                        "sandstone_stairs", "nether_brick_stairs",
                        "spruce_stairs", "birch_stairs", "jungle_stairs",
                        "quartz_stairs", "acacia_stairs", "dark_oak_stairs",
                        "fence", "fence_gate", "nether_brick_fence",
                        "web", "snow_layer", "carpet", "farmland", "ladder",
                        "enchanting_table", "ender_chest", "end_portal_frame",
                        "dragon_egg", "iron_bars", "trapped_chest", "chest",
                        "bedrock"
                };
                for (String name : commonBlocks) {
                    BlockstateJson bs = loadSingleBlockstate(namespace, name);
                    if (bs != null) nsBlockstates.put(name, bs);
                }
            }

            if (!nsBlockstates.isEmpty()) {
                loadedBlockstates.put(namespace, nsBlockstates);
            }
        }

        /**
         * Load metadata_map.json for a namespace as an auxiliary data-driven
         * metadata-to-properties mapping.  Function-based registration via
         * {@link #registerMetadataMapping} always takes priority.
         */
        private static void loadMetadataMaps(String namespace) {
            String path = "/assets/" + namespace + "/metadata_map.json";
            try (InputStream stream = VanillaModelManager.class.getResourceAsStream(path)) {
                if (stream == null) return;
                InputStreamReader reader = new InputStreamReader(stream);

                // Format: { "blockName": { "0": {"prop":"val"}, "1": {...} }, ... }
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Map<String, String>>> data = gson.fromJson(reader, Map.class);
                if (data == null) return;

                Map<String, Map<Integer, Map<String, String>>> nsMap = new HashMap<>();
                for (Map.Entry<String, Map<String, Map<String, String>>> blockEntry : data.entrySet()) {
                    String blockName = blockEntry.getKey();
                    Map<Integer, Map<String, String>> metaMap = new HashMap<>();
                    for (Map.Entry<String, Map<String, String>> metaEntry : blockEntry.getValue().entrySet()) {
                        try {
                            int meta = Integer.parseInt(metaEntry.getKey());
                            metaMap.put(meta, metaEntry.getValue());
                        } catch (NumberFormatException e) {
                            CatFrame.logger.warn("metadata_map.json [{}] invalid metadata key '{}'", blockName, metaEntry.getKey());
                        }
                    }
                    if (!metaMap.isEmpty()) {
                        nsMap.put(blockName, metaMap);
                    }
                }

                if (!nsMap.isEmpty()) {
                    loadedMetadataMaps.put(namespace, nsMap);
                    CatFrame.logger.info("Loaded metadata_map.json for namespace: {} ({} blocks)", namespace, nsMap.size());
                }
            } catch (Exception e) {
                CatFrame.logger.debug("No metadata_map.json for namespace {}: {}", namespace, e.getMessage());
            }
        }

        /**
         * Load a single blockstate JSON file.
         */
        private static BlockstateJson loadSingleBlockstate(String namespace, String blockName) {
            String path = "/assets/" + namespace + "/blockstates/" + blockName + ".json";
            try (InputStream stream = VanillaModelManager.class.getResourceAsStream(path)) {
                if (stream == null) return null;
                InputStreamReader reader = new InputStreamReader(stream);
                BlockstateJson bs = blockstateGson.fromJson(reader, BlockstateJson.class);
                if (bs != null) {
                    // Collect textures from all variant models
                    TextureManagement.collectTexturesFromBlockstate(bs);
                    CatFrame.logger.debug("Loaded blockstate: {}/{}", namespace, blockName);
                }
                return bs;
            } catch (Exception e) {
                CatFrame.logger.error("Error loading blockstate {}/{}: {}", namespace, blockName, e.getMessage());
                return null;
            }
        }
    }

    // ==================== TextureManagement ====================
    public static class TextureManagement {

        private static void collectTexturesFromBlockstate(BlockstateJson bs) {
            if (bs.variants != null) {
                for (BlockstateJson.VariantEntry entry : bs.variants.values()) {
                    if (entry.isArray()) {
                        for (BlockstateJson.Variant v : entry.list) {
                            collectTexturesFromModel(v.model, false);
                        }
                    } else if (entry.single != null) {
                        collectTexturesFromModel(entry.single.model, false);
                    }
                }
            }
            if (bs.multipart != null) {
                for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                    if (mpc.apply != null) {
                        collectTexturesFromModel(mpc.apply.model, false);
                    }
                }
            }
        }

        /**
         * Public API: collect all texture paths from a model JSON and add them
         * to pendingTextures for registration during TextureStitchEvent.Pre.
         * <p>
         * Defaults to block model (textures registered on block atlas).
         *
         * @param modelPath model path relative to /assets/{ns}/models/, e.g. "block/myblock"
         */
        public static void collectTextures(String modelPath) {
            collectTexturesFromModel(modelPath, false);
        }

        /**
         * Public API: collect all texture paths from a model JSON and add them
         * to the appropriate pending set based on model type.
         * <p>
         * The model type determines which atlas textures are registered on:
         * <ul>
         *   <li>{@code isItemModel = true} → item atlas (type 1)</li>
         *   <li>{@code isItemModel = false} → block atlas (type 0)</li>
         * </ul>
         *
         * @param modelPath   model path relative to /assets/{ns}/models/
         * @param isItemModel true if this is an item model, false for block model
         */
        public static void collectTextures(String modelPath, boolean isItemModel) {
            collectTexturesFromModel(modelPath, isItemModel);
        }

        /**
         * Internal: resolve a model and collect its textures into the appropriate
         * pending set based on model type.
         *
         * @param modelPath   model resource path
         * @param isItemModel true → item atlas, false → block atlas
         */
        private static void collectTexturesFromModel(String modelPath, boolean isItemModel) {
            if (modelPath == null) return;
            ModelJson resolved = ModelResolver.resolve(modelPath);
            if (resolved != null) {
                Set<String> textures = ModelResolver.collectTextures(resolved);
                for (String tex : textures) {
                    if (isItemModel) {
                        pendingItemTextures.add(tex);
                    } else {
                        pendingTextures.add(tex);
                    }
                }
            }
        }

        // ==================== Texture Registration ====================

        /**
         * Register all block textures with the block texture map (type 0).
         * Call during TextureStitchEvent.Pre when getTextureType() == 0.
         */
        public static void registerTextures(TextureMap map) {
            for (String texturePath : pendingTextures) {
                String iconName = Utilities.resolveTextureName(texturePath);
                if (iconName != null && !iconName.isEmpty()) {
                    map.registerIcon(iconName);
                }
            }
        }

        /**
         * Register all item textures with the item texture map (type 1).
         * Call during TextureStitchEvent.Pre when getTextureType() == 1.
         */
        public static void registerItemTextures(TextureMap map) {
            for (String texturePath : pendingItemTextures) {
                String iconName = Utilities.resolveTextureName(texturePath);
                if (iconName != null && !iconName.isEmpty()) {
                    map.registerIcon(iconName);
                }
            }
        }

        /**
         * Collect IIcon references after stitching and bake all models.
         * Call during TextureStitchEvent.Post when getTextureType() == 0.
         */
        public static void onTextureStitchPost(TextureMap map) {
            textureIcons.clear();
            // Block atlas icons
            for (String texturePath : pendingTextures) {
                String iconName = Utilities.resolveTextureName(texturePath);
                if (iconName != null) {
                    IIcon icon = map.getAtlasSprite(iconName);
                    if (icon != null) {
                        textureIcons.put(texturePath, icon);
                    }
                }
            }
            // Item atlas icons
            net.minecraft.client.renderer.texture.TextureMap itemMap =
                    (net.minecraft.client.renderer.texture.TextureMap) Minecraft.getMinecraft().getTextureManager()
                            .getTexture(TextureMap.locationItemsTexture);
            if (itemMap != null) {
                for (String texturePath : pendingItemTextures) {
                    String iconName = Utilities.resolveTextureName(texturePath);
                    if (iconName != null) {
                        IIcon icon = itemMap.getAtlasSprite(iconName);
                        if (icon != null) {
                            textureIcons.put(texturePath, icon);
                        }
                    }
                }
            }
            ModelBaking.bakeAllModels();
        }

        /**
         * 在 item atlas (type 1) 缝合完成后更新 item 纹理的 IIcon 引用并重新烘焙。
         * <p>
         * 1.7.10 中 block atlas (type 0) 的 {@link TextureStitchEvent.Post} 早于
         * item atlas (type 1) 的 Post。因此 type 0 Post 时 item atlas 可能尚未完全
         * 缝合，{@link TextureMap#getAtlasSprite(String)} 可能返回缝合前的占位 sprite
         * 甚至 missingno。
         * 本方法在 type 1 Post 中被调用，此时 item atlas 已缝合完成，可以获取正确的
         * sprite UV 坐标。
         */
        public static void onTextureStitchPostItem(TextureMap itemMap) {
            // 更新 item 纹理的 IIcon 引用（item atlas 此时已缝合完成）
            for (String texturePath : pendingItemTextures) {
                String iconName = Utilities.resolveTextureName(texturePath);
                if (iconName != null) {
                    IIcon icon = itemMap.getAtlasSprite(iconName);
                    if (icon != null) {
                        textureIcons.put(texturePath, icon);
                    }
                }
            }
            // 重新烘焙所有模型（清空缓存，用正确的 sprite 重建 quads）
            ModelBaking.bakeAllModels();
        }
    }

    // ==================== ModelBaking ====================
    public static class ModelBaking {

        /**
         * modelPath -> display transforms (from ModelJson.display).
         * Populated during bakeModel(), used when constructing ItemModelWrapper.
         */
        private static final Map<String, Map<String, ModelJson.DisplayTransform>> modelDisplayCache = new HashMap<>();

        /**
         * Item -> display transforms, tracked during item baking for auto-registration.
         */
        private static final Map<Item, Map<String, ModelJson.DisplayTransform>> itemDisplayTransforms = new HashMap<>();

        /**
         * Block -> first model path, tracked during block baking for display transform lookup.
         * Used when auto-registering ItemModelWrappers for blocks.
         */
        private static final Map<Block, String> blockModelPaths = new HashMap<>();

        private static void bakeAllModels() {
            bakedBlockModels.clear();
            bakedItemModels.clear();
            blockRotations.clear();
            bakedModelCache.clear();
            blockModelPaths.clear();

            // Bake from blockstates
            for (Map.Entry<String, Map<String, BlockstateJson>> nsEntry : loadedBlockstates.entrySet()) {
                String namespace = nsEntry.getKey();
                for (Map.Entry<String, BlockstateJson> bsEntry : nsEntry.getValue().entrySet()) {
                    String blockName = bsEntry.getKey();
                    BlockstateJson bs = bsEntry.getValue();
                    Block block = Utilities.findBlock(namespace, blockName);
                    if (block != null) {
                        // Auto-register mapper from metadata_map.json if no function-based mapper exists
                        if (!metadataMappers.containsKey(block)) {
                            IMetadataMapper jsonMapper = Utilities.findMetadataMapEntry(namespace, blockName);
                            if (jsonMapper != null) {
                                metadataMappers.put(block, jsonMapper);
                                CatFrame.logger.debug("Auto-registered metadata mapper from metadata_map.json for {}:{}", namespace, blockName);
                            }
                        }
                        bakeBlockstateForBlock(block, bs);
                    }
                }
            }

            // Bake from model_mappings (blocks that don't have blockstates)
            for (Map.Entry<String, ModelMappings> entry : loadedMappings.entrySet()) {
                String namespace = entry.getKey();
                ModelMappings mappings = entry.getValue();

                if (mappings.blocks != null) {
                    for (Map.Entry<String, String> blockEntry : mappings.blocks.entrySet()) {
                        String key = blockEntry.getKey();
                        String blockName;
                        int meta = -1; // -1 means "all metadata" (default slot 0)

                        // Support "name:metadata" format, e.g., "log:1" -> block=log, meta=1
                        if (key.contains(":")) {
                            String[] parts = key.split(":", 2);
                            blockName = parts[0];
                            try {
                                meta = Integer.parseInt(parts[1]);
                            } catch (NumberFormatException e) {
                                blockName = key; // Not a number after ':', treat whole thing as name
                            }
                        } else {
                            blockName = key;
                        }

                        Block block = Utilities.findBlock(namespace, blockName);
                        if (block == null) continue;

                        // Skip if blockstate already handles this block entirely
                        if (meta == -1 && bakedBlockModels.containsKey(block)) continue;

                        List<BakedQuad> quads = bakeModel(blockEntry.getValue(), 0);
                        if (quads == null) continue;

                        // Track model path for display transform lookup
                        if (!blockModelPaths.containsKey(block)) {
                            blockModelPaths.put(block, blockEntry.getValue());
                        }

                        Map<Integer, List<BakedQuad>> metaMap = bakedBlockModels.get(block);
                        if (metaMap == null) {
                            metaMap = new HashMap<>();
                            bakedBlockModels.put(block, metaMap);
                        }

                        int targetMeta = (meta == -1) ? 0 : meta;
                        if (!metaMap.containsKey(targetMeta)) {
                            metaMap.put(targetMeta, quads);
                            CatFrame.logger.debug("Baked block from mappings: {} meta={}", blockName, targetMeta);
                        }
                    }
                }

                if (mappings.items != null) {
                    for (Map.Entry<String, String> itemEntry : mappings.items.entrySet()) {
                        String key = itemEntry.getKey();
                        String itemName;
                        int damage = -1;

                        // Support "name:damage" format, e.g., "dye:4" -> item=dye, damage=4
                        if (key.contains(":")) {
                            String[] parts = key.split(":", 2);
                            itemName = parts[0];
                            try {
                                damage = Integer.parseInt(parts[1]);
                            } catch (NumberFormatException e) {
                                itemName = key;
                            }
                        } else {
                            itemName = key;
                        }

                        Item item = Utilities.findItem(namespace, itemName);
                        if (item == null) {
                            CatFrame.logger.debug("[VMM] bakeAllModels: item not found: {}:{}", namespace, itemName);
                            continue;
                        }

                        List<BakedQuad> quads = bakeModel(itemEntry.getValue(), 0);
                        if (quads == null) {
                            CatFrame.logger.warn("[VMM] bakeAllModels: failed to bake model '{}' for item '{}'",
                                    itemEntry.getValue(), itemName);
                            continue;
                        }

                        // Track display transforms for this item
                        Map<String, ModelJson.DisplayTransform> itemDisplay = modelDisplayCache.get(itemEntry.getValue());
                        if (itemDisplay != null) {
                            itemDisplayTransforms.put(item, itemDisplay);
                        }

                        Map<Integer, List<BakedQuad>> damageMap = bakedItemModels.get(item);
                        if (damageMap == null) {
                            damageMap = new HashMap<>();
                            bakedItemModels.put(item, damageMap);
                        }

                        int targetDamage = (damage == -1) ? 0 : damage;
                        if (!damageMap.containsKey(targetDamage)) {
                            damageMap.put(targetDamage, quads);
                            CatFrame.logger.debug("Baked item from mappings: {} damage={}", itemName, targetDamage);
                        }
                    }
                }
            }

            // Auto-register BlockStateModel wrappers from the old baked data (transition path)
            for (Map.Entry<Block, Map<Integer, List<BakedQuad>>> entry : bakedBlockModels.entrySet()) {
                Block block = entry.getKey();
                Map<Integer, List<BakedQuad>> metaMap = entry.getValue();
                Map<Integer, BlockStateModelPart> partMap = new HashMap<>();
                for (Map.Entry<Integer, List<BakedQuad>> me : metaMap.entrySet()) {
                    partMap.put(me.getKey(), BlockStateModelPart.fromQuads(me.getValue()));
                }
                BlockStateModelPart fallback = partMap.get(0);
                if (fallback == null) {
                    fallback = partMap.values().stream().findFirst().orElse(BlockStateModelPart.empty());
                }
                registeredBlockModels.put(block, new MetadataBlockModel(partMap, fallback));

                // Also copy rotations
                Map<Integer, Integer> rotMap = blockRotations.get(block);
                if (rotMap != null) {
                    Map<Integer, Integer> newRot = new HashMap<>(rotMap);
                    registeredBlockRotations.put(block, newRot);
                }
            }

            // Auto-register ItemModel wrappers from old baked data
            for (Map.Entry<Item, Map<Integer, List<BakedQuad>>> entry : bakedItemModels.entrySet()) {
                Item item = entry.getKey();
                Map<Integer, List<BakedQuad>> damageMap = entry.getValue();
                // Construct a simple metadata-based model that wraps damage->quads
                Map<Integer, BlockStateModelPart> partMap = new HashMap<>();
                for (Map.Entry<Integer, List<BakedQuad>> de : damageMap.entrySet()) {
                    partMap.put(de.getKey(), BlockStateModelPart.fromQuads(de.getValue()));
                }
                BlockStateModelPart fallback = partMap.get(0);
                if (fallback == null) {
                    fallback = partMap.values().stream().findFirst().orElse(BlockStateModelPart.empty());
                }
                MetadataBlockModel metaModel = new MetadataBlockModel(partMap, fallback);
                Map<String, ModelJson.DisplayTransform> display = itemDisplayTransforms.get(item);
                registeredItemModels.put(item, new ItemModelWrapper(metaModel, display));
            }

            // Auto-register ItemModel wrappers for blocks (from bakedBlockModels)
            // so block items get display transforms applied (gui / firstperson / thirdperson)
            for (Map.Entry<Block, Map<Integer, List<BakedQuad>>> entry : bakedBlockModels.entrySet()) {
                Block block = entry.getKey();
                Item item = Item.getItemFromBlock(block);
                if (item == null) continue;
                // Skip if this item already has a dedicated item model
                if (registeredItemModels.containsKey(item) || bakedItemModels.containsKey(item)) continue;

                Map<Integer, List<BakedQuad>> metaMap = entry.getValue();
                Map<Integer, BlockStateModelPart> partMap = new HashMap<>();
                for (Map.Entry<Integer, List<BakedQuad>> me : metaMap.entrySet()) {
                    partMap.put(me.getKey(), BlockStateModelPart.fromQuads(me.getValue()));
                }
                BlockStateModelPart fallback = partMap.get(0);
                if (fallback == null) {
                    fallback = partMap.values().stream().findFirst().orElse(BlockStateModelPart.empty());
                }
                String modelPath = blockModelPaths.get(block);
                Map<String, ModelJson.DisplayTransform> blockDisplay = (modelPath != null)
                        ? modelDisplayCache.get(modelPath) : null;
                if (blockDisplay != null) {
                    CatFrame.logger.debug("[VMM] bakeAllModels: registered ItemModel for block '{}' with display transforms",
                            Block.blockRegistry.getNameForObject(block));
                }
                registeredItemModels.put(item, new ItemModelWrapper(
                        new MetadataBlockModel(partMap, fallback), blockDisplay));
            }

            CatFrame.logger.info("VanillaModelManager: Baked {} block models, {} item models, registered {} BlockStateModels, {} ItemModels",
                    bakedBlockModels.size(), bakedItemModels.size(),
                    registeredBlockModels.size(), registeredItemModels.size());

            // 为所有拥有 CatFrame ItemModel 的物品注册 Forge IItemRenderer。
            // IdentityHashMap 的特性保证了同一 Item 实例重复注册只会覆盖旧值。
            int forgeRegistered = 0;
            for (Item item : registeredItemModels.keySet()) {
                MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemRenderer.INSTANCE);
                forgeRegistered++;
            }
            CatFrame.logger.info("VanillaModelManager: Registered {} items with Forge IItemRenderer",
                    forgeRegistered);
        }

        private static void bakeBlockstateForBlock(Block block, BlockstateJson bs) {
            Map<Integer, List<BakedQuad>> metaMap = new HashMap<>();
            Map<Integer, Integer> rotMap = new HashMap<>();

            // --- Track first model path for display transform lookup ---
            if (!blockModelPaths.containsKey(block)) {
                String path = findFirstModelPath(bs);
                if (path != null) {
                    blockModelPaths.put(block, path);
                }
            }

            // --- Get the effective mapper (function-based takes priority) ---
            IMetadataMapper mapper = metadataMappers.get(block);

            if (bs.variants != null) {
                if (mapper != null) {
                    // 1.16.5 path: enumerate metadata, convert via mapper, match property keys
                    for (int meta = 0; meta < 16; meta++) {
                        Map<String, String> props = mapper.map(meta);
                        String variantKey = PublicRenderAPI.buildVariantKey(props);
                        BlockstateJson.VariantEntry varEntry = bs.variants.get(variantKey);
                        if (varEntry == null) {
                            varEntry = bs.variants.get("normal");
                        }
                        if (varEntry == null) continue;

                        int seed = meta * 31;
                        BlockstateJson.Variant variant = varEntry.getVariant(seed);
                        if (variant == null || variant.model == null) continue;

                        List<BakedQuad> quads = bakeModel(variant.model, variant.y);
                        if (quads != null && !quads.isEmpty()) {
                            metaMap.put(meta, quads);
                            rotMap.put(meta, variant.y);
                        }
                    }
                } else {
                    // Compat path: iterate variant entries directly, parse metadata from key
                    boolean hasNumberKeys = false;
                    for (Map.Entry<String, BlockstateJson.VariantEntry> entry : bs.variants.entrySet()) {
                        String key = entry.getKey();
                        BlockstateJson.VariantEntry varEntry = entry.getValue();

                        int meta = parseMetadataFromKey(key);
                        if (isMetadataNumberKey(key)) {
                            hasNumberKeys = true;
                        }

                        BlockstateJson.Variant variant = varEntry.getVariant(0);
                        if (variant == null || variant.model == null) continue;

                        List<BakedQuad> quads = bakeModel(variant.model, variant.y);
                        if (quads != null) {
                            metaMap.put(meta, quads);
                            rotMap.put(meta, variant.y);
                        }
                    }
                    if (hasNumberKeys) {
                        CatFrame.logger.warn(
                                "VanillaModelManager: blockstate for '{}' uses deprecated metadata-number variant keys. "
                                        + "Consider registering an IMetadataMapper and switching to property keys (e.g. \"variant=granite\").",
                                Block.blockRegistry.getNameForObject(block));
                    }
                }
            }

            if (bs.multipart != null) {
                if (mapper != null) {
                    // Multipart with mapper: enumerate all metadata values, evaluate when conditions
                    for (int meta = 0; meta < 16; meta++) {
                        Map<String, String> props = mapper.map(meta);
                        List<BakedQuad> allQuads = new ArrayList<>();

                        for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                            boolean applies = (mpc.when == null) || mpc.when.matches(props);
                            if (applies && mpc.apply != null) {
                                List<BakedQuad> partQuads = bakeModel(mpc.apply.model, mpc.apply.y);
                                if (partQuads != null) {
                                    // 将 apply.y 旋转烘焙到 quad 顶点中（而不是依赖运行时全局旋转）
                                    partQuads = BlockJsonModelBake.applyYRotation(partQuads, mpc.apply.y);
                                    allQuads.addAll(partQuads);
                                }
                            }
                        }

                        if (!allQuads.isEmpty()) {
                            metaMap.put(meta, allQuads);
                            // multipart 各部分的旋转已烘焙到顶点中，运行时不需要额外旋转
                            rotMap.put(meta, 0);
                        }
                    }
                } else {
                    // Compat: bake all unconditional parts for meta 0
                    List<BakedQuad> allQuads = new ArrayList<>();
                    for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                        if (mpc.when == null && mpc.apply != null) {
                            List<BakedQuad> partQuads = bakeModel(mpc.apply.model, mpc.apply.y);
                            if (partQuads != null) {
                                partQuads = BlockJsonModelBake.applyYRotation(partQuads, mpc.apply.y);
                                allQuads.addAll(partQuads);
                            }
                        }
                    }
                    if (!allQuads.isEmpty()) {
                        metaMap.put(0, allQuads);
                    }
                }
            }

            if (!metaMap.isEmpty()) {
                bakedBlockModels.put(block, metaMap);
                blockRotations.put(block, rotMap);
            }
        }

        /**
         * Parse metadata value from a variant key.
         * Supports: "normal" -> 0, "0" -> 0, "1" -> 1, "facing=north" -> property-based
         *
         * @deprecated pure-number variant keys are legacy; use an IMetadataMapper + property keys instead
         */
        @Deprecated
        private static int parseMetadataFromKey(String key) {
            if (key.equals("normal") || key.isEmpty()) return 0;
            try {
                return Integer.parseInt(key);
            } catch (NumberFormatException e) {
                // Property-based key (e.g., "facing=north") - map to metadata based on order
                // For 1.7.10, we use simple metadata mapping
                return 0;
            }
        }

        /**
         * Check whether a variant key is a raw metadata number (legacy compat).
         */
        private static boolean isMetadataNumberKey(String key) {
            if (key.equals("normal") || key.isEmpty()) return false;
            try {
                Integer.parseInt(key);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        /**
         * Extract the first model path from a blockstate JSON, used for display transform lookup.
         * Checks variants first (first entry's model), then multipart (first unconditional apply).
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

        /**
         * Bake a model into quads with the given rotation.
         * Results are cached by {@code modelPath@rotationY}.
         */
        private static List<BakedQuad> bakeModel(String modelPath, int rotationY) {
            if (modelPath == null) return null;

            String cacheKey = modelPath + "@" + rotationY;
            List<BakedQuad> cached = bakedModelCache.get(cacheKey);
            if (cached != null) {
                CatFrame.logger.debug("[VMM] bakeModel: cache HIT for {} | quads={}", cacheKey, cached.size());
                return cached;
            }

            ModelJson resolved = ModelResolver.resolve(modelPath);
            if (resolved == null) {
                CatFrame.logger.warn("[VMM] bakeModel: ModelResolver.resolve({}) returned null", modelPath);
                return null;
            }

            // Cache display transforms (indexed by modelPath, not cacheKey — rotation doesn't affect display)
            if (resolved.display != null && !modelDisplayCache.containsKey(modelPath)) {
                modelDisplayCache.put(modelPath, resolved.display);
            }

            if (resolved.elements == null) {
                CatFrame.logger.warn("[VMM] bakeModel: resolved model '{}' has null elements", modelPath);
                return null;
            }

            // Build icon map
            Map<String, IIcon> iconMap = new HashMap<>();
            if (resolved.textures != null) {
                for (Map.Entry<String, String> tex : resolved.textures.entrySet()) {
                    String value = tex.getValue();
                    if (!value.startsWith("#")) {
                        IIcon icon = textureIcons.get(value);
                        if (icon == null) {
                            String iconName = Utilities.resolveTextureName(value);
                            CatFrame.logger.debug("[VMM] bakeModel: icon not in textureIcons | value={} | resolvedName={}", value, iconName);
                            if (iconName != null) {
                                // Try blocks texture map first, then items texture map
                                icon = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(iconName);
                                if (icon == null) {
                                    net.minecraft.client.renderer.texture.TextureMap itemMap =
                                            (net.minecraft.client.renderer.texture.TextureMap) Minecraft.getMinecraft().getTextureManager()
                                                    .getTexture(TextureMap.locationItemsTexture);
                                    if (itemMap != null) {
                                        // Try the original texture path first (for items/ prefix textures)
                                        icon = itemMap.getAtlasSprite(value);
                                        if (icon == null) {
                                            icon = itemMap.getAtlasSprite(iconName);
                                        }
                                        CatFrame.logger.debug("[VMM] bakeModel: try itemMap getAtlasSprite({}) = {}", value, icon);
                                    }
                                } else {
                                    CatFrame.logger.debug("[VMM] bakeModel: found icon in blocksMap: {}={}", iconName, icon);
                                }
                            }
                        }
                        if (icon != null) {
                            iconMap.put(tex.getKey(), icon);
                        } else {
                            CatFrame.logger.warn("[VMM] bakeModel: icon NOT FOUND for texture key '{}' value '{}'", tex.getKey(), value);
                        }
                    }
                }
            }

            // Bake elements
            List<BakedQuad> quads = new ArrayList<>();
            for (ModelJson.Element element : resolved.elements) {
                quads.addAll(BlockJsonModelBake.bakeElement(element, iconMap, resolved.texture_size));
            }
            // 传递模型级别的 guiLight 到所有 quad
            if (resolved.guiLight != null) {
                for (BakedQuad q : quads) {
                    q.guiLight = resolved.guiLight;
                }
            }
            // 传递模型级别的 display transforms 到所有 quad
            if (resolved.display != null) {
                for (BakedQuad q : quads) {
                    q.modelDisplay = resolved.display;
                }
            }
            CatFrame.logger.info("[VMM] bakeModel: baked '{}' | elements={} | quads={} | iconMapKeys={}",
                    cacheKey, resolved.elements.size(), quads.size(), iconMap.keySet());
            bakedModelCache.put(cacheKey, quads);
            return quads;
        }
    }

    // ==================== Utilities ====================
    public static class Utilities {

        static String resolveTextureName(String texturePath) {
            if (texturePath == null) return null;

            // Keep namespace if present, only strip 'blocks/' or 'items/' prefix
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

        // ==================== Block/Item Lookup ====================

        private static Block findBlock(String namespace, String name) {
            Block block = Block.getBlockFromName(name);
            if (block != null) return block;
            block = Block.getBlockFromName(namespace + ":" + name);
            return block;
        }

        private static Item findItem(String namespace, String name) {
            // Try with full qualified name
            Item item = (Item) Item.itemRegistry.getObject(namespace + ":" + name);
            if (item != null) return item;
            item = (Item) Item.itemRegistry.getObject(name);
            return item;
        }

        /**
         * Try to find a metadata_map.json entry for the given block and build an
         * {@link IMetadataMapper} from it.  Returns null if no entry exists.
         */
        private static IMetadataMapper findMetadataMapEntry(String namespace, String blockName) {
            // Search the same namespace first, then fallback to all registered namespaces
            Map<Integer, Map<String, String>> metaMap = null;
            Map<String, Map<Integer, Map<String, String>>> nsData = loadedMetadataMaps.get(namespace);
            if (nsData != null) {
                metaMap = nsData.get(blockName);
            }
            if (metaMap == null) {
                for (Map.Entry<String, Map<String, Map<Integer, Map<String, String>>>> e : loadedMetadataMaps.entrySet()) {
                    metaMap = e.getValue().get(blockName);
                    if (metaMap != null) break;
                }
            }
            if (metaMap == null) return null;

            // Build a mapper lambda from the data
            final Map<Integer, Map<String, String>> finalMap = metaMap;
            return metadata -> finalMap.getOrDefault(metadata, Collections.emptyMap());
        }
    }

    // ==================== ModelRegistration ====================
    public static class ModelRegistration {

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
            List<BakedQuad> quads = ModelBaking.bakeModel(modelPath, rotationY);
            if (quads == null) return BlockStateModelPart.empty();
            return BlockStateModelPart.fromQuads(quads);
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
         * (Transition support for JsonBlock blocks.)
         */
        public static void markRandomRotation(Block block) {
            randomRotationBlocks.add(block);
        }

        /**
         * Mark a block as using auto-overlay (metadata-indexed model list).
         * (Transition support for JsonBlock blocks.)
         */
        public static void markAutoOverlay(Block block) {
            autoOverlayBlocks.add(block);
        }

        /**
         * Register an ItemModel for an item.
         * Also immediately registers the Forge IItemRenderer if the model system has been initialized.
         */
        public static void registerItemModel(Item item, ItemModel model) {
            registeredItemModels.put(item, model);
            // 如果烘焙已完成，立即注册 Forge IItemRenderer
            if (initialized) {
                MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemRenderer.INSTANCE);
            }
        }

        /**
         * Get the registered ItemModel for an item, or null if not registered.
         */
        public static ItemModel getRegisteredItemModel(Item item) {
            if (item == null) return null;
            return registeredItemModels.get(item);
        }

        /**
         * Check if a block has a JSON model override (either static bake or dynamic state-provider)
         */
        public static boolean hasModel(Block block) {
            return bakedBlockModels.containsKey(block) || stateBlockData.containsKey(block)
                    || registeredBlockModels.containsKey(block);
        }

        /**
         * Check if an item has a JSON model override
         */
        public static boolean hasItemModel(Item item) {
            return bakedItemModels.containsKey(item) || registeredItemModels.containsKey(item);
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

    // ==================== PublicRenderAPI ====================
    public static class PublicRenderAPI {
        public static boolean renderBlock(IBlockAccess world, int x, int y, int z, Block block, RenderBlocks renderer) {
            int metadata = world.getBlockMetadata(x, y, z);

            // --- New path: check CatStateDefinition first (v0.3.0) ---
            CatStateDefinition<?> stateDef = blockStateDefinitions.get(block);
            if (stateDef != null && block instanceof IBlockStateProvider) {
                IBlockStateProvider sp = (IBlockStateProvider) block;
                CatBlockState catState = sp.getBlockState(world, x, y, z, metadata);
                if (catState != null) {
                    // Use CatBlockState.toVariantKey() for matching
                    BlockstateJson bs = stateBlockData.get(block);
                    if (bs != null) {
                        return renderStateWithCatBlockState(world, x, y, z, block, catState, bs);
                    }
                }
            }

            // --- New path: check registered BlockStateModel first ---
            BlockStateModel stateModel = registeredBlockModels.get(block);
            if (stateModel != null) {
                BlockStateModelPart part = stateModel.collectParts(world, x, y, z, metadata);
                if (part != null && !part.isEmpty()) {
                    // Compute rotation
                    int rot = 0;
                    if (randomRotationBlocks.contains(block)) {
                        rot = 90 * (Math.abs(x + y + z) % 4);
                    } else {
                        Map<Integer, Integer> rotMap = registeredBlockRotations.get(block);
                        if (rotMap != null) {
                            Integer r = rotMap.get(metadata);
                            if (r == null) r = rotMap.get(0);
                            if (r != null) rot = r;
                        }
                        // Also check old rotation map for transition
                        Map<Integer, Integer> oldRotMap = blockRotations.get(block);
                        if (oldRotMap != null) {
                            Integer r = oldRotMap.get(metadata);
                            if (r == null) r = oldRotMap.get(0);
                            if (r != null) rot = r;
                        }
                    }
                    UniformRenderPipeline.renderBlockQuads(part, world, x, y, z, block, rot);
                    return true;
                }
            }

            // Dynamic state-provider path: resolve variant at render time
            if (block instanceof IBlockStateProvider && stateBlockData.containsKey(block)) {
                return renderStateProviderBlock(world, x, y, z, block, metadata);
            }

            // Static baked path: use pre-baked metadata-keyed models
            Map<Integer, List<BakedQuad>> metaMap = bakedBlockModels.get(block);
            if (metaMap == null) return false;

            List<BakedQuad> quads = metaMap.get(metadata);
            if (quads == null) {
                // Fallback to meta 0
                quads = metaMap.get(0);
            }
            if (quads == null || quads.isEmpty()) return false;

            // Get rotation
            Map<Integer, Integer> rotMap = blockRotations.get(block);
            int rotationDeg = 0;
            if (rotMap != null) {
                Integer rot = rotMap.get(metadata);
                if (rot == null) rot = rotMap.get(0);
                if (rot != null) rotationDeg = rot;
            }

            // Route through the new pipeline via UniformRenderPipeline
            BlockStateModelPart part = BlockStateModelPart.fromQuads(quads);
            UniformRenderPipeline.renderBlockQuads(part, world, x, y, z, block, rotationDeg);
            return true;
        }

        /**
         * Render a block using CatBlockState's variant key for blockstate matching (v0.3.0).
         */
        private static boolean renderStateWithCatBlockState(IBlockAccess world, int x, int y, int z,
                                                             Block block, CatBlockState catState,
                                                             BlockstateJson bs) {
            if (bs == null) return false;

            if (bs.variants != null) {
                String variantKey = catState.toVariantKey();
                BlockstateJson.VariantEntry entry = bs.variants.get(variantKey);
                if (entry == null) entry = bs.variants.get("normal");
                if (entry == null) return false;

                int seed = x * 3129871 ^ z * 116129781 ^ y;
                BlockstateJson.Variant variant = entry.getVariant(seed);
                if (variant == null || variant.model == null) return false;

                BlockStateModelPart part = ModelRegistration.bakeModelPart(variant.model, variant.y);
                if (part == null || part.isEmpty()) return false;

                UniformRenderPipeline.renderBlockQuads(part, world, x, y, z, block, variant.y);
                return true;

            } else if (bs.multipart != null) {
                // Convert CatBlockState to property map for multipart condition matching
                java.util.Map<String, String> propMap = new java.util.HashMap<>();
                CatStateDefinition<?> def = catState.getDefinition();
                if (def != null) {
                    for (Property<?> p : def.getProperties()) {
                        propMap.put(p.getName(), catState.getValue(p).toString());
                    }
                }

                java.util.List<BakedQuad> allQuads = new java.util.ArrayList<>();

                for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                    boolean applies = (mpc.when == null) || mpc.when.matches(propMap);
                    if (applies && mpc.apply != null) {
                        java.util.List<BakedQuad> partQuads = ModelBaking.bakeModel(mpc.apply.model, mpc.apply.y);
                        if (partQuads != null) {
                            // 将 apply.y 烘焙到 quad 顶点中
                            partQuads = BlockJsonModelBake.applyYRotation(partQuads, mpc.apply.y);
                            allQuads.addAll(partQuads);
                        }
                    }
                }

                if (allQuads.isEmpty()) return false;
                BlockStateModelPart part = BlockStateModelPart.fromQuads(allQuads);
                UniformRenderPipeline.renderBlockQuads(part, world, x, y, z, block, 0);
                return true;
            }

            return false;
        }

        /**
         * Render a block using IBlockStateProvider's dynamic variant resolution.
         * Matches current block properties to blockstate variants or multipart conditions.
         */
        private static boolean renderStateProviderBlock(IBlockAccess world, int x, int y, int z, Block block, int metadata) {
            IBlockStateProvider provider = (IBlockStateProvider) block;
            BlockstateJson bs = stateBlockData.get(block);
            if (bs == null) return false;

            Map<String, String> properties = provider.getStateProperties(world, x, y, z, metadata);
            if (properties == null) properties = Collections.emptyMap();

            if (bs.variants != null) {
                // Build variant key from properties: "key1=val1,key2=val2" (sorted)
                String variantKey = buildVariantKey(properties);
                BlockstateJson.VariantEntry entry = bs.variants.get(variantKey);

                // Fallback: try "normal" if exact match fails
                if (entry == null) entry = bs.variants.get("normal");
                if (entry == null) return false;

                // Use position-based seed for weighted random
                int seed = x * 3129871 ^ z * 116129781 ^ y;
                BlockstateJson.Variant variant = entry.getVariant(seed);
                if (variant == null || variant.model == null) return false;

                BlockStateModelPart part = ModelRegistration.bakeModelPart(variant.model, variant.y);
                if (part == null || part.isEmpty()) return false;

                UniformRenderPipeline.renderBlockQuads(part, world, x, y, z, block, variant.y);
                return true;

            } else if (bs.multipart != null) {
                // Multipart: combine all matching parts
                List<BakedQuad> allQuads = new ArrayList<>();

                for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                    boolean applies = (mpc.when == null) || mpc.when.matches(properties);
                    if (applies && mpc.apply != null) {
                        List<BakedQuad> partQuads = ModelBaking.bakeModel(mpc.apply.model, mpc.apply.y);
                        if (partQuads != null) {
                            partQuads = BlockJsonModelBake.applyYRotation(partQuads, mpc.apply.y);
                            allQuads.addAll(partQuads);
                        }
                    }
                }

                if (allQuads.isEmpty()) return false;
                BlockStateModelPart part = BlockStateModelPart.fromQuads(allQuads);
                UniformRenderPipeline.renderBlockQuads(part, world, x, y, z, block, 0);
                return true;
            }

            return false;
        }

        /**
         * Build a variant key string from properties map.
         * Properties are sorted alphabetically and joined as "key1=val1,key2=val2".
         */
        private static String buildVariantKey(Map<String, String> properties) {
            if (properties.isEmpty()) return "normal";
            List<String> keys = new ArrayList<>(properties.keySet());
            Collections.sort(keys);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(keys.get(i)).append('=').append(properties.get(keys.get(i)));
            }
            return sb.toString();
        }

        /**
         * Render an item using its JSON model (GUI / inventory context).
         * 接收 ItemStack 以便扩展能读取 NBT / damage / 附魔等完整上下文。
         */
        public static void renderItem(ItemStack stack) {
            if (stack == null) return;
            Item item = stack.getItem();
            if (item == null) return;

            // --- New path: check registered ItemModel first ---
            ItemModel itemModel = registeredItemModels.get(item);
            CatFrame.logger.info("[VMM] renderItem | item={} | registeredItemModel={} | bakedItemModels.containsKey={}",
                    Item.itemRegistry.getNameForObject(item), itemModel, bakedItemModels.containsKey(item));
            if (itemModel != null) {
                itemModel.render(stack, RenderPhase.ITEM_GUI);
                return;
            }

            // --- Fallback to old baked path ---
            Map<Integer, List<BakedQuad>> damageMap = bakedItemModels.get(item);
            if (damageMap == null) return;

            int damage = stack.getItemDamage();
            List<BakedQuad> quads = damageMap.get(damage);
            if (quads == null) quads = damageMap.get(0);
            if (quads == null || quads.isEmpty()) return;

            BlockStateModelPart part = BlockStateModelPart.fromQuads(quads);
            UniformRenderPipeline.renderItemQuads(part, stack, RenderPhase.ITEM_GUI);
        }

        /**
         * 旧接口兼容薄包装：无 NBT 上下文，扩展仅能看到 item+damage。
         */
        public static void renderItem(Item item, int damage) {
            if (item == null) return;
            renderItem(new ItemStack(item, 1, damage));
        }

        /**
         * Render an item's JSON model as a 3D model for in-hand rendering.
         * Unlike {@link #renderItem(ItemStack)} which is designed for 2D GUI
         * context, this method renders the model at the current GL origin with
         * proper 3D centering — the caller ({@code ItemRenderer#renderItem})
         * has already set up the hand position / rotation transforms.
         *
         * @param stack        物品栈
         * @param isFirstPerson true=第一人称, false=第三人称
         */
        public static void renderItemInHand(ItemStack stack, boolean isFirstPerson) {
            if (stack == null) return;
            Item item = stack.getItem();
            if (item == null) return;

            RenderPhase phase = isFirstPerson
                    ? RenderPhase.ITEM_HAND_FIRST_PERSON
                    : RenderPhase.ITEM_HAND_THIRD_PERSON;

            // --- New path: check registered ItemModel first ---
            ItemModel itemModel = registeredItemModels.get(item);
            CatFrame.logger.info("[VMM] renderItemInHand | item={} | isFirstPerson={} | registeredItemModel={} | bakedItemModels.containsKey={}",
                    Item.itemRegistry.getNameForObject(item), isFirstPerson, itemModel, bakedItemModels.containsKey(item));
            if (itemModel != null) {
                itemModel.render(stack, phase);
                return;
            }

            // --- Fallback to old baked path ---
            Map<Integer, List<BakedQuad>> damageMap = bakedItemModels.get(item);
            if (damageMap == null) return;

            int damage = stack.getItemDamage();
            List<BakedQuad> quads = damageMap.get(damage);
            if (quads == null) quads = damageMap.get(0);
            if (quads == null || quads.isEmpty()) return;

            BlockStateModelPart part = BlockStateModelPart.fromQuads(quads);
            UniformRenderPipeline.renderItemQuads(part, stack, phase);
        }

        /**
         * 旧接口兼容 — 默认第一人称。
         */
        public static void renderItemInHand(ItemStack stack) {
            renderItemInHand(stack, true);
        }

        /**
         * 旧接口兼容薄包装。
         */
        public static void renderItemInHand(Item item, int damage) {
            if (item == null) return;
            renderItemInHand(new ItemStack(item, 1, damage), true);
        }
    }
}

