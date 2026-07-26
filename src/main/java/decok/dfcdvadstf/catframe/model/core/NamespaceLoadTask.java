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
 * 单个 namespace 的数据加载任务。
 * <p>
 * 从 {@link ModelManagerDataLoader} 提取的纯执行逻辑：所有写入都发生在本地集合中，
 * 不触碰 {@link VanillaModelManager} 的共享静态字段。
 * 这使得本类可以被多线程安全地并发执行（如 Akka Futures 或线程池）。
 * <p>
 * 执行完成后返回 {@link NamespaceLoadResult}，由主线程合并到共享字段。
 */
public class NamespaceLoadTask {

    private static final Gson BLOCKSTATE_GSON = BlockstateJson.createGson();
    private static final Gson GSON = new Gson();
    private static final Gson ITEM_STATE_GSON = ItemStateNode.createGson();

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
        Set<String> localBlockTextures = new LinkedHashSet<>();
        Set<String> localItemTextures = new LinkedHashSet<>();
        Map<String, ItemStateNode> localItemStates = new HashMap<>();
        Set<String> localOversizedItems = new HashSet<>();

        // 1. 加载 model_mappings.json
        localMappings = loadModelMappings(namespace, localBlockTextures, localItemTextures);

        // 2. 加载 blockstates
        loadBlockstatesFromMappings(namespace, localMappings, localBlockstates,
                localBlockTextures, localItemTextures);

        // 3. 加载 items/ ItemState 决策树
        loadItemStates(namespace, localItemStates, localBlockTextures, localItemTextures, localMappings, localOversizedItems);

        CatFrame.logger.debug("[NamespaceLoadTask] namespace '{}' loaded: {} blockstates, {} item states, {} block textures, {} item textures",
                namespace, localBlockstates.size(), localItemStates.size(), localBlockTextures.size(), localItemTextures.size());

        return new NamespaceLoadResult(namespace, localBlockstates, localMappings,
                localBlockTextures, localItemTextures, localItemStates, localOversizedItems);
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
                CatFrame.logger.info("Loaded model_mappings.json for namespace: {} (state_mapping={})",
                        namespace, mappings.state_mapping);

                // state_mapping 模式下值是 state 名而非模型路径，纹理由被引用的
                // blockstate/ItemState 加载途径收集，此处跳过
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
            // state_mapping 模式：值引用的 blockstate 可能不对应任何已注册方块
            //（纯供引用的 state 定义），需额外按值加载保证引用可解析
            // state_mapping mode: referenced blockstates may not match any registered block
            // (pure reference-only state definitions), load them by value as well
            if (mappings.state_mapping) {
                for (String ref : mappings.blocks.values()) {
                    String refName = ref.contains(":") ? ref.substring(ref.indexOf(':') + 1) : ref;
                    String refNs = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : namespace;
                    // 跨 namespace 引用由目标 namespace 自己的加载任务负责
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
        // Block 注册表遍历自动发现（与 items/ 的发现方式一致）：
        // 对该 namespace 下每个已注册方块尝试加载 blockstates/{name}.json。
        // 取代原先硬编码的 COMMON_MINECRAFT_BLOCKS 列表，并同时修复无 model_mappings.json
        // 的第三方 namespace 的 blockstate 发现缺口。
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
     * 加载 items/ 目录下的 ItemState 决策树 JSON。
     * <p>
     * 支持两种发现方式（按优先级）：
     * <ol>
     *   <li>遍历 Item 注册表，对每个属于该 namespace 的物品尝试加载 {@code items/{name}.json}<br>
     *       （与高版本 Minecraft 行为一致：有则用，无则跳过）</li>
     *   <li>补充：从 model_mappings.json 的 items 字段推断（覆盖未注册到 Item 注册表的特殊情况）</li>
     * </ol>
     *
     * @param namespace    命名空间
     * @param localItemStates  输出：itemName → 决策树根节点
     * @param localItemTextures 输出：收集到的物品纹理
     * @param mappings     model_mappings 数据（可为 null）
     */
    private static void loadItemStates(String namespace,
                                        Map<String, ItemStateNode> localItemStates,
                                        Set<String> localBlockTextures,
                                        Set<String> localItemTextures,
                                        VanillaModelManager.ModelMappings mappings,
                                        Set<String> localOversizedItems) {
        Set<String> itemNames = new LinkedHashSet<>();

        // 1. 遍历 Item 注册表自动发现（与高版本 Minecraft 行为一致：有 items/{name}.json 就加载）
        for (Object obj : Item.itemRegistry) {
            if (obj == null) continue;
            String registryName = Item.itemRegistry.getNameForObject(obj);
            if (registryName == null) continue;
            String ns = registryName.contains(":") ? registryName.substring(0, registryName.indexOf(':')) : "minecraft";
            if (!ns.equals(namespace)) continue;
            String name = registryName.contains(":") ? registryName.substring(registryName.indexOf(':') + 1) : registryName;
            itemNames.add(name);
        }

        // 2. 补充：从 model_mappings 的 items 字段推断（覆盖未注册到 Item 注册表的特殊情况）
        if (mappings != null && mappings.items != null) {
            for (String key : mappings.items.keySet()) {
                String itemName = key.contains(":") ? key.split(":")[0] : key;
                itemNames.add(itemName);
            }
            // state_mapping 模式：值引用的 ItemState 决策树也加入候选，
            // 保证纯供引用的 items/{name}.json 被加载（跨 namespace 引用由目标 namespace 负责）
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

        // 3. 加载每个物品的 ItemState JSON
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
                    // 收集决策树中引用的所有模型纹理
                    Set<String> modelPaths = new HashSet<>();
                    root.collectModelPaths(modelPaths);
                    for (String modelPath : modelPaths) {
                        // 收集模型纹理，按路径前缀分流：block/ 开头 → block atlas，items/ 开头 → item atlas
                        ModelJson resolved = ModelResolver.resolve(modelPath);
                        if (resolved != null) {
                            Set<String> modelTextures = ModelResolver.collectTextures(resolved);
                            for (String tex : modelTextures) {
                                // 提取 namespace 后的路径部分用于前缀判断
                                // 例: "minecraft:block/sapling_oak" → "block/sapling_oak"
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
