package decok.dfcdvadstf.catframe.model;

import com.google.gson.Gson;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * 单个 namespace 的数据加载任务。
 * <p>
 * 从 {@link VMMDataLoader} 提取的纯执行逻辑：所有写入都发生在本地集合中，
 * 不触碰 {@link VanillaModelManager} 的共享静态字段。
 * 这使得本类可以被多线程安全地并发执行（如 Akka Futures 或线程池）。
 * <p>
 * 执行完成后返回 {@link NamespaceLoadResult}，由主线程合并到共享字段。
 */
public class NamespaceLoadTask {

    private static final Gson BLOCKSTATE_GSON = BlockstateJson.createGson();
    private static final Gson GSON = new Gson();

    /** minecraft namespace 的常见方块列表（从 VMMDataLoader 提取） */
    private static final String[] COMMON_MINECRAFT_BLOCKS = {
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
            "sponge", "noteblock", "jukebox", "mycelium", "packed_ice",
            "quartz_ore", "command_block", "beacon",
            "monster_egg", "mob_spawner", "cake", "glass_pane", "stained_glass_pane",
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

    /**
     * 执行单个 namespace 的数据加载。
     * <p>
     * 所有结果收集到本地集合中，不写入任何共享静态字段。
     * 可在多线程环境中安全并发执行。
     *
     * @param namespace 命名空间名称
     * @return 加载结果
     */
    public static NamespaceLoadResult execute(String namespace) {
        // 本地集合 — 不触碰 VanillaModelManager 的共享字段
        Map<String, BlockstateJson> localBlockstates = new HashMap<>();
        VanillaModelManager.ModelMappings localMappings = null;
        Map<String, Map<Integer, Map<String, String>>> localMetadataMaps = new HashMap<>();
        Set<String> localBlockTextures = new LinkedHashSet<>();
        Set<String> localItemTextures = new LinkedHashSet<>();

        // 1. 加载 model_mappings.json
        localMappings = loadModelMappings(namespace, localBlockTextures, localItemTextures);

        // 2. 加载 blockstates
        loadBlockstatesFromMappings(namespace, localMappings, localBlockstates,
                localBlockTextures, localItemTextures);

        // 3. 加载 metadata_map.json
        loadMetadataMaps(namespace, localMetadataMaps);

        CatFrame.logger.debug("[NamespaceLoadTask] namespace '{}' loaded: {} blockstates, {} block textures, {} item textures",
                namespace, localBlockstates.size(), localBlockTextures.size(), localItemTextures.size());

        return new NamespaceLoadResult(namespace, localBlockstates, localMappings,
                localMetadataMaps, localBlockTextures, localItemTextures);
    }

    // ==================== 内部加载方法（从 VMMDataLoader 提取，改为写本地集合） ====================

    private static VanillaModelManager.ModelMappings loadModelMappings(String namespace,
                                                                        Set<String> blockTextures,
                                                                        Set<String> itemTextures) {
        String path = "/assets/" + namespace + "/model_mappings.json";
        try (InputStream stream = NamespaceLoadTask.class.getResourceAsStream(path)) {
            if (stream == null) return null;
            InputStreamReader reader = new InputStreamReader(stream);
            VanillaModelManager.ModelMappings mappings = GSON.fromJson(reader, VanillaModelManager.ModelMappings.class);
            if (mappings != null) {
                CatFrame.logger.info("Loaded model_mappings.json for namespace: {}", namespace);

                if (mappings.blocks != null) {
                    for (String modelPath : mappings.blocks.values()) {
                        collectTexturesFromModel(modelPath, false, blockTextures);
                    }
                }
                if (mappings.items != null) {
                    for (String modelPath : mappings.items.values()) {
                        collectTexturesFromModel(modelPath, true, itemTextures);
                    }
                }
            }
            return mappings;
        } catch (Exception e) {
            CatFrame.logger.debug("No model_mappings.json for namespace {}: {}", namespace, e.getMessage());
            return null;
        }
    }

    private static void loadBlockstatesFromMappings(String namespace,
                                                     VanillaModelManager.ModelMappings mappings,
                                                     Map<String, BlockstateJson> localBlockstates,
                                                     Set<String> blockTextures,
                                                     Set<String> itemTextures) {
        // Try to load an index file first
        String indexPath = "/assets/" + namespace + "/blockstates/_index.json";
        try (InputStream stream = NamespaceLoadTask.class.getResourceAsStream(indexPath)) {
            if (stream != null) {
                InputStreamReader reader = new InputStreamReader(stream);
                String[] names = GSON.fromJson(reader, String[].class);
                for (String name : names) {
                    BlockstateJson bs = loadSingleBlockstate(namespace, name, blockTextures);
                    if (bs != null) localBlockstates.put(name, bs);
                }
            }
        } catch (Exception ignored) {
        }

        // Also try to load blockstates for any blocks in model_mappings
        if (mappings != null && mappings.blocks != null) {
            for (String blockName : mappings.blocks.keySet()) {
                if (!localBlockstates.containsKey(blockName)) {
                    BlockstateJson bs = loadSingleBlockstate(namespace, blockName, blockTextures);
                    if (bs != null) localBlockstates.put(blockName, bs);
                }
            }
        }

        // Try common vanilla block names if minecraft namespace
        if (namespace.equals("minecraft") && localBlockstates.isEmpty()) {
            for (String name : COMMON_MINECRAFT_BLOCKS) {
                BlockstateJson bs = loadSingleBlockstate(namespace, name, blockTextures);
                if (bs != null) localBlockstates.put(name, bs);
            }
        }
    }

    private static void loadMetadataMaps(String namespace,
                                          Map<String, Map<Integer, Map<String, String>>> localMetadataMaps) {
        String path = "/assets/" + namespace + "/metadata_map.json";
        try (InputStream stream = NamespaceLoadTask.class.getResourceAsStream(path)) {
            if (stream == null) return;
            InputStreamReader reader = new InputStreamReader(stream);

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Map<String, String>>> data = GSON.fromJson(reader, Map.class);
            if (data == null) return;

            for (Map.Entry<String, Map<String, Map<String, String>>> blockEntry : data.entrySet()) {
                String blockName = blockEntry.getKey();
                Map<Integer, Map<String, String>> metaMap = new HashMap<>();
                for (Map.Entry<String, Map<String, String>> metaEntry : blockEntry.getValue().entrySet()) {
                    try {
                        int meta = Integer.parseInt(metaEntry.getKey());
                        metaMap.put(meta, metaEntry.getValue());
                    } catch (NumberFormatException e) {
                        CatFrame.logger.warn("metadata_map.json [{}] invalid metadata key '{}'",
                                blockName, metaEntry.getKey());
                    }
                }
                if (!metaMap.isEmpty()) {
                    localMetadataMaps.put(blockName, metaMap);
                }
            }

            if (!localMetadataMaps.isEmpty()) {
                CatFrame.logger.info("Loaded metadata_map.json for namespace: {} ({} blocks)",
                        namespace, localMetadataMaps.size());
            }
        } catch (Exception e) {
            CatFrame.logger.debug("No metadata_map.json for namespace {}: {}", namespace, e.getMessage());
        }
    }

    private static BlockstateJson loadSingleBlockstate(String namespace, String blockName,
                                                        Set<String> blockTextures) {
        String path = "/assets/" + namespace + "/blockstates/" + blockName + ".json";
        try (InputStream stream = NamespaceLoadTask.class.getResourceAsStream(path)) {
            if (stream == null) return null;
            InputStreamReader reader = new InputStreamReader(stream);
            BlockstateJson bs = BLOCKSTATE_GSON.fromJson(reader, BlockstateJson.class);
            if (bs != null) {
                collectTexturesFromBlockstate(bs, blockTextures);
                CatFrame.logger.debug("Loaded blockstate: {}/{}", namespace, blockName);
            }
            return bs;
        } catch (Exception e) {
            CatFrame.logger.error("Error loading blockstate {}/{}: {}", namespace, blockName, e.getMessage());
            return null;
        }
    }

    // ==================== 纹理收集（本地版本，写本地集合） ====================

    private static void collectTexturesFromBlockstate(BlockstateJson bs, Set<String> textures) {
        if (bs.variants != null) {
            for (BlockstateJson.VariantEntry entry : bs.variants.values()) {
                if (entry.isArray()) {
                    for (BlockstateJson.Variant v : entry.list) {
                        collectTexturesFromModel(v.model, false, textures);
                    }
                } else if (entry.single != null) {
                    collectTexturesFromModel(entry.single.model, false, textures);
                }
            }
        }
        if (bs.multipart != null) {
            for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                if (mpc.apply != null) {
                    collectTexturesFromModel(mpc.apply.model, false, textures);
                }
            }
        }
    }

    private static void collectTexturesFromModel(String modelPath, boolean isItemModel, Set<String> textures) {
        if (modelPath == null) return;
        ModelJson resolved = ModelResolver.resolve(modelPath);
        if (resolved != null) {
            Set<String> modelTextures = ModelResolver.collectTextures(resolved);
            textures.addAll(modelTextures);
        }
    }
}
