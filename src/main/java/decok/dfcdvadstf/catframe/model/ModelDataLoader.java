package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.ModernItem;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import decok.dfcdvadstf.catframe.model.state.IBlockStateProvider;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Data loading extracted from {@link VanillaModelManager}.
 * <p>
 * Responsible for namespace discovery, blockstate/mapping JSON loading,
 * and metadata map loading.
 */
@SideOnly(Side.CLIENT)
public class ModelDataLoader {

    /**
     * Initialize the model manager. Call during preInit.
     * Scans all registered namespaces for blockstates and model_mappings.
     */
    public static void init() {
        if (VanillaModelManager.initialized) return;
        VanillaModelManager.initialized = true;

        // Always include minecraft namespace
        registerNamespace("minecraft");

        CatFrame.logger.info("VanillaModelManager: Initializing...");

        for (String namespace : VanillaModelManager.namespaces) {
            loadNamespace(namespace);
        }

        // Load blockstates for all registered IBlockStateProvider blocks
        for (Block block : VanillaModelManager.registeredStateBlocks) {
            loadStateProviderBlock(block);
        }

        // Scan Item.itemRegistry for IItemJsonModel implementations (Tier 2 discovery)
        for (Object obj : Item.itemRegistry) {
            if (obj instanceof IItemJsonModel) {
                IItemJsonModel ijm = (IItemJsonModel) obj;
                if (!ijm.shouldHandle()) continue;  // explicitly opt out
                Item item = (Item) obj;
                String modelPath = ijm.getModelPath();
                if (modelPath != null) {
                    // Auto-collect textures for this model
                    VanillaTextureTracker.collectTexturesFromModel(modelPath, true);
                    // For ModernItem dual-models, also collect hand model textures
                    if (obj instanceof ModernItem) {
                        ModernItem mi = (ModernItem) obj;
                        if (mi.hasDualModels()) {
                            VanillaTextureTracker.collectTexturesFromModel(mi.getHandModelPath(), true);
                        }
                    }
                    VanillaModelManager.interfaceItemModels.put(item, ijm);
                    CatFrame.logger.debug("[VMM] IItemJsonModel discovered: {} -> {}",
                            Item.itemRegistry.getNameForObject(item), modelPath);
                }
            }
        }
        if (!VanillaModelManager.interfaceItemModels.isEmpty()) {
            CatFrame.logger.info("VanillaModelManager: Discovered {} IItemJsonModel items",
                    VanillaModelManager.interfaceItemModels.size());
        }

        CatFrame.logger.info("VanillaModelManager: Loaded {} namespaces, {} state-blocks, {} block-textures pending, {} item-textures pending",
                VanillaModelManager.namespaces.size(), VanillaModelManager.registeredStateBlocks.size(),
                VanillaModelManager.pendingTextures.size(), VanillaModelManager.pendingItemTextures.size());
    }

    /**
     * Register a namespace for model loading.
     * Other mods should call this in preInit to participate.
     */
    public static void registerNamespace(String namespace) {
        if (!VanillaModelManager.namespaces.contains(namespace)) {
            VanillaModelManager.namespaces.add(namespace);
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
        if (!VanillaModelManager.registeredStateBlocks.contains(block)) {
            VanillaModelManager.registeredStateBlocks.add(block);
            // Ensure the namespace is registered
            String ns = ((IBlockStateProvider) block).getBlockstateNamespace();
            registerNamespace(ns);

            // If already initialized, load immediately
            if (VanillaModelManager.initialized) {
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
        if (!VanillaModelManager.metadataMappers.containsKey(block)) {
            VanillaModelManager.metadataMappers.put(block, mapper);
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
            VanillaModelManager.stateBlockData.put(block, bs);
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
        try (InputStream stream = ModelDataLoader.class.getResourceAsStream(path)) {
            if (stream == null) return;
            InputStreamReader reader = new InputStreamReader(stream);
            VanillaModelManager.ModelMappings mappings = (new com.google.gson.Gson()).fromJson(reader, VanillaModelManager.ModelMappings.class);
            if (mappings != null) {
                VanillaModelManager.loadedMappings.put(namespace, mappings);
                CatFrame.logger.info("Loaded model_mappings.json for namespace: {}", namespace);

                // Resolve all models and collect textures
                if (mappings.blocks != null) {
                    for (String modelPath : mappings.blocks.values()) {
                        VanillaTextureTracker.collectTexturesFromModel(modelPath, false);
                    }
                }
                if (mappings.items != null) {
                    for (String modelPath : mappings.items.values()) {
                        VanillaTextureTracker.collectTexturesFromModel(modelPath, true);
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
        try (InputStream stream = ModelDataLoader.class.getResourceAsStream(indexPath)) {
            if (stream != null) {
                InputStreamReader reader = new InputStreamReader(stream);
                String[] names = (new com.google.gson.Gson()).fromJson(reader, String[].class);
                for (String name : names) {
                    BlockstateJson bs = loadSingleBlockstate(namespace, name);
                    if (bs != null) nsBlockstates.put(name, bs);
                }
            }
        } catch (Exception ignored) {
        }

        // Also try to load blockstates for any blocks in model_mappings
        VanillaModelManager.ModelMappings mappings = VanillaModelManager.loadedMappings.get(namespace);
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
            VanillaModelManager.loadedBlockstates.put(namespace, nsBlockstates);
        }
    }

    /**
     * Load metadata_map.json for a namespace as an auxiliary data-driven
     * metadata-to-properties mapping.  Function-based registration via
     * {@link #registerMetadataMapping} always takes priority.
     */
    private static void loadMetadataMaps(String namespace) {
        String path = "/assets/" + namespace + "/metadata_map.json";
        try (InputStream stream = ModelDataLoader.class.getResourceAsStream(path)) {
            if (stream == null) return;
            InputStreamReader reader = new InputStreamReader(stream);

            // Format: { "blockName": { "0": {"prop":"val"}, "1": {...} }, ... }
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Map<String, String>>> data = (new com.google.gson.Gson()).fromJson(reader, Map.class);
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
                VanillaModelManager.loadedMetadataMaps.put(namespace, nsMap);
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
        try (InputStream stream = ModelDataLoader.class.getResourceAsStream(path)) {
            if (stream == null) return null;
            InputStreamReader reader = new InputStreamReader(stream);
            BlockstateJson bs = VanillaModelManager.blockstateGson.fromJson(reader, BlockstateJson.class);
            if (bs != null) {
                // Collect textures from all variant models
                VanillaTextureTracker.collectTexturesFromBlockstate(bs);
                CatFrame.logger.debug("Loaded blockstate: {}/{}", namespace, blockName);
            }
            return bs;
        } catch (Exception e) {
            CatFrame.logger.error("Error loading blockstate {}/{}: {}", namespace, blockName, e.getMessage());
            return null;
        }
    }
}
