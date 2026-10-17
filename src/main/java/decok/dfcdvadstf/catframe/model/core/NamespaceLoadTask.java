package decok.dfcdvadstf.catframe.model.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateRoot;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Data loading task for a single namespace.
 * <p>
 * Pure execution logic extracted from {@link ModelManagerDataLoader}: all writes happen in local
 * collections and never touch {@link VanillaModelManager}'s shared static fields.
 * This lets the class be executed safely and concurrently from multiple threads (e.g. Akka Futures or a thread pool).
 * <p>
 * When execution completes it returns a {@link NamespaceLoadResult}, which the main thread merges into the shared fields.
 */
public class NamespaceLoadTask {

    private static final Gson BLOCKSTATE_GSON = BlockstateJson.createGson();
    private static final Gson GSON = new Gson();
    private static final Gson ITEM_STATE_GSON = ItemStateNode.createGson();

    /**
     * Executes the data load for a single namespace.
     * <p>
     * All results are collected into local collections; no shared static field is written.
     * Safe to execute concurrently in a multithreaded environment.
     *
     * @param namespace the namespace name
     * @return the load result
     */
    public static NamespaceLoadResult execute(String namespace) {
        // Local collections — do not touch VanillaModelManager's shared fields
        Map<String, BlockstateJson> localBlockstates = new HashMap<>();
        VanillaModelManager.ModelMappings localMappings = null;
        Set<String> localBlockTextures = new LinkedHashSet<>();
        Set<String> localItemTextures = new LinkedHashSet<>();
        Map<String, ItemStateNode> localItemStates = new HashMap<>();
        Set<String> localOversizedItems = new HashSet<>();

        // 1. Load model_mappings.json
        localMappings = loadModelMappings(namespace, localBlockTextures, localItemTextures);

        // 2. Load blockstates
        loadBlockstatesFromMappings(namespace, localMappings, localBlockstates,
                localBlockTextures, localItemTextures);

        // 3. Load the items/ ItemState decision trees
        loadItemStates(namespace, localItemStates, localBlockTextures, localItemTextures, localMappings, localOversizedItems);

        CatFrame.logger.debug("[NamespaceLoadTask] namespace '{}' loaded: {} blockstates, {} item states, {} block textures, {} item textures",
                namespace, localBlockstates.size(), localItemStates.size(), localBlockTextures.size(), localItemTextures.size());

        return new NamespaceLoadResult(namespace, localBlockstates, localMappings,
                localBlockTextures, localItemTextures, localItemStates, localOversizedItems);
    }

    // ==================== Internal loading methods (extracted from VMMDataLoader, changed to write local collections) ====================

    private static VanillaModelManager.ModelMappings loadModelMappings(String namespace,
                                                                        Set<String> blockTextures,
                                                                        Set<String> itemTextures) {
        String path = "/assets/" + namespace + "/model_mappings.json";
        try (InputStream stream = NamespaceLoadTask.class.getResourceAsStream(path)) {
            if (stream == null) return null;
            InputStreamReader reader = new InputStreamReader(stream);
            VanillaModelManager.ModelMappings mappings = GSON.fromJson(reader, VanillaModelManager.ModelMappings.class);
            if (mappings != null) {
                CatFrame.logger.info("Loaded model_mappings.json for namespace: {} (state_mapping={})",
                        namespace, mappings.state_mapping);

                // In state_mapping mode values are state names, not model paths —
                // textures are collected via the referenced blockstate/ItemState loading instead
                if (!mappings.state_mapping) {
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
            // state_mapping mode: referenced blockstates may not match any registered block
            // (pure reference-only state definitions), load them by value as well
            if (mappings.state_mapping) {
                for (String ref : mappings.blocks.values()) {
                    String refName = ref.contains(":") ? ref.substring(ref.indexOf(':') + 1) : ref;
                    String refNs = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : namespace;
                    // Cross-namespace refs are handled by the target namespace's own load task
                    if (!refNs.equals(namespace) || localBlockstates.containsKey(refName)) continue;
                    BlockstateJson bs = loadSingleBlockstate(namespace, refName, blockTextures);
                    if (bs != null) localBlockstates.put(refName, bs);
                }
            }
        }

        // Block registry traversal auto-discovery (mirrors the items/ discovery path):
        // try loading blockstates/{name}.json for every registered block in this namespace.
        // Replaces the former hardcoded COMMON_MINECRAFT_BLOCKS list and also fixes
        // discovery for non-minecraft namespaces that ship no model_mappings.json.
        for (Object obj : Block.blockRegistry) {
            if (obj == null) continue;
            String registryName = Block.blockRegistry.getNameForObject(obj);
            if (registryName == null) continue;
            String ns = registryName.contains(":") ? registryName.substring(0, registryName.indexOf(':')) : "minecraft";
            if (!ns.equals(namespace)) continue;
            String name = registryName.contains(":") ? registryName.substring(registryName.indexOf(':') + 1) : registryName;
            if (localBlockstates.containsKey(name)) continue;
            BlockstateJson bs = loadSingleBlockstate(namespace, name, blockTextures);
            if (bs != null) localBlockstates.put(name, bs);
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

    /**
     * Loads the ItemState decision tree JSONs under the items/ directory.
     * <p>
     * Supports two discovery methods (in priority order):
     * <ol>
     *   <li>Traverse the Item registry and try to load {@code items/{name}.json} for every item
     *       belonging to this namespace<br>
     *       (matches modern Minecraft behavior: load if present, skip if absent)</li>
     *   <li>Supplement: infer from the items field of model_mappings.json (covers items not
     *       registered in the Item registry)</li>
     * </ol>
     *
     * @param namespace    the namespace
     * @param localItemStates  output: itemName → decision tree root node
     * @param localItemTextures output: the collected item textures
     * @param mappings     the model_mappings data (may be null)
     */
    private static void loadItemStates(String namespace,
                                        Map<String, ItemStateNode> localItemStates,
                                        Set<String> localBlockTextures,
                                        Set<String> localItemTextures,
                                        VanillaModelManager.ModelMappings mappings,
                                        Set<String> localOversizedItems) {
        Set<String> itemNames = new LinkedHashSet<>();

        // 1. Auto-discovery by traversing the Item registry (matches modern Minecraft behavior: load items/{name}.json when present)
        for (Object obj : Item.itemRegistry) {
            if (obj == null) continue;
            String registryName = Item.itemRegistry.getNameForObject(obj);
            if (registryName == null) continue;
            String ns = registryName.contains(":") ? registryName.substring(0, registryName.indexOf(':')) : "minecraft";
            if (!ns.equals(namespace)) continue;
            String name = registryName.contains(":") ? registryName.substring(registryName.indexOf(':') + 1) : registryName;
            itemNames.add(name);
        }

        // 2. Supplement: infer from the items field of model_mappings (covers items not registered in the Item registry)
        if (mappings != null && mappings.items != null) {
            for (String key : mappings.items.keySet()) {
                String itemName = key.contains(":") ? key.split(":")[0] : key;
                itemNames.add(itemName);
            }
            // state_mapping mode: also add referenced ItemState tree names as candidates so
            // reference-only items/{name}.json get loaded (cross-ns refs handled by that namespace)
            if (mappings.state_mapping) {
                for (String ref : mappings.items.values()) {
                    String refNs = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : namespace;
                    if (!refNs.equals(namespace)) continue;
                    itemNames.add(ref.contains(":") ? ref.substring(ref.indexOf(':') + 1) : ref);
                }
            }
        }

        // 3. Load each item's ItemState JSON
        for (String itemName : itemNames) {
            String path = "/assets/" + namespace + "/items/" + itemName + ".json";
            try (InputStream stream = NamespaceLoadTask.class.getResourceAsStream(path)) {
                if (stream == null) continue;
                InputStreamReader reader = new InputStreamReader(stream);
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null) continue;

                ItemStateRoot rootFull = ItemStateNode.parseRootFull(json);
                ItemStateNode root = rootFull != null ? rootFull.model : null;
                if (root != null) {
                    localItemStates.put(itemName, root);
                    if (rootFull.oversizedInGui) {
                        localOversizedItems.add(itemName);
                    }
                    // Collect all model textures referenced by the decision tree
                    Set<String> modelPaths = new HashSet<>();
                    root.collectModelPaths(modelPaths);
                    for (String modelPath : modelPaths) {
                        // Collect model textures, routed by path prefix: block/ → block atlas, items/ → item atlas
                        ModelJson resolved = ModelResolver.resolve(modelPath);
                        if (resolved != null) {
                            Set<String> modelTextures = ModelResolver.collectTextures(resolved);
                            for (String tex : modelTextures) {
                                // Extract the path part after the namespace for the prefix check
                                // e.g. "minecraft:block/sapling_oak" → "block/sapling_oak"
                                String pathPart = tex;
                                int colon = tex.indexOf(':');
                                if (colon >= 0) {
                                    pathPart = tex.substring(colon + 1);
                                }
                                if (pathPart.startsWith("block/") || pathPart.startsWith("blocks/")) {
                                    localBlockTextures.add(tex);
                                } else {
                                    localItemTextures.add(tex);
                                }
                            }
                        }
                    }
                    CatFrame.logger.debug("Loaded ItemState: {}/items/{}", namespace, itemName);
                }
            } catch (Exception e) {
                CatFrame.logger.error("Error loading ItemState {}/items/{}: {}",
                        namespace, itemName, e.getMessage());
            }
        }

        if (!localItemStates.isEmpty()) {
            CatFrame.logger.info("Loaded {} ItemState files for namespace: {} (scanned {} candidates)",
                    localItemStates.size(), namespace, itemNames.size());
        }
    }

    // ==================== Texture collection (local version, writes local collections) ====================

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
                    if (mpc.apply.isArray()) {
                        for (BlockstateJson.Variant v : mpc.apply.list) {
                            collectTexturesFromModel(v.model, false, textures);
                        }
                    } else if (mpc.apply.single != null) {
                        collectTexturesFromModel(mpc.apply.single.model, false, textures);
                    }
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
