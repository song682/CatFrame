package decok.dfcdvadstf.catframe.model;

import com.google.gson.Gson;
import decok.dfcdvadstf.catframe.CatFrame;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Resolves model JSON files with parent inheritance chain.
 * Merges textures and elements from parent models recursively.
 */
public class ModelResolver {
    private static final Gson gson = new Gson();
    private static final Map<String, ModelJson> cache = new HashMap<>();
    private static final List<String> registeredNamespaces = new ArrayList<>();
    private static final int MAX_DEPTH = 16;

    // ==================== Builtin Models ====================

    /**
     * 硬编码的内建模型，优先级高于从 JSON 文件加载。
     * 模型路径以 "builtin/" 开头的都由这里提供。
     */
    private static final Map<String, ModelJson> builtinModels = new HashMap<>();

    static {
        registeredNamespaces.add("minecraft");

        // --- builtin/generated: 标准平面物品模型（单层） ---
        ModelJson generated = new ModelJson();
        generated.guiLight = "front";

        // 一个平面 element，在 Z=8 平面上显示纹理
        ModelJson.Element elem = new ModelJson.Element();
        elem.from = new float[]{0, 0, 8};
        elem.to = new float[]{16, 16, 8};
        elem.faces = new ModelJson.Faces();
        elem.faces.north = new ModelJson.Face();
        elem.faces.north.texture = "#layer0";
        elem.faces.north.uv = new float[]{0, 0, 16, 16};
        elem.faces.south = new ModelJson.Face();
        elem.faces.south.texture = "#layer0";
        elem.faces.south.uv = new float[]{0, 0, 16, 16};
        generated.elements = new ArrayList<>();
        generated.elements.add(elem);

        // display transforms
        generated.display = new HashMap<>();
        generated.display.put("gui", display(0, 0, 0, 0, 0, 0, 1, 1, 1));
        generated.display.put("ground", display(0, 0, 0, 0, 2, 0, 0.5f, 0.5f, 0.5f));
        generated.display.put("fixed", display(0, 0, 0, 0, 0, 0, 1, 1, 1));
        generated.display.put("thirdperson_righthand", display(0, 0, 0, 0, 3, 1, 0.55f, 0.55f, 0.55f));
        generated.display.put("firstperson_righthand", display(1.13f, 3.2f, 1.13f, 0, 0, 0, 0.68f, 0.68f, 0.68f));
        generated.display.put("firstperson_lefthand", display(1.13f, 3.2f, 1.13f, 0, 0, 0, 0.68f, 0.68f, 0.68f));
        builtinModels.put("builtin/generated", generated);

        // --- builtin/missing: MissingNo 纹理模型 ---
        ModelJson missing = new ModelJson();
        missing.textures = new HashMap<>();
        missing.textures.put("all", "minecraft:missingno");

        ModelJson.Element missingElem = new ModelJson.Element();
        missingElem.from = new float[]{0, 0, 8};
        missingElem.to = new float[]{16, 16, 8};
        missingElem.faces = new ModelJson.Faces();
        missingElem.faces.north = new ModelJson.Face();
        missingElem.faces.north.texture = "#all";
        missingElem.faces.north.uv = new float[]{0, 0, 16, 16};
        missingElem.faces.south = new ModelJson.Face();
        missingElem.faces.south.texture = "#all";
        missingElem.faces.south.uv = new float[]{0, 0, 16, 16};
        missing.elements = new ArrayList<>();
        missing.elements.add(missingElem);
        builtinModels.put("builtin/missing", missing);
    }

    /**
     * 辅助方法：快速创建 DisplayTransform
     */
    private static ModelJson.DisplayTransform display(float tx, float ty, float tz,
                                                       float rx, float ry, float rz,
                                                       float sx, float sy, float sz) {
        ModelJson.DisplayTransform dt = new ModelJson.DisplayTransform();
        dt.translation = new float[]{tx, ty, tz};
        dt.rotation = new float[]{rx, ry, rz};
        dt.scale = new float[]{sx, sy, sz};
        return dt;
    }

    /**
     * Resolve a model by path (e.g., "block/stone") with full parent chain resolution.
     * The resolved model will have all elements and textures merged from parents.
     */
    public static ModelJson resolve(String modelPath) {
        if (cache.containsKey(modelPath)) {
            return cache.get(modelPath);
        }

        ModelJson resolved = resolveInternal(modelPath, 0);
        if (resolved != null) {
            // Resolve texture variables in the final model
            resolveTextureVariables(resolved);
            cache.put(modelPath, resolved);
        }
        return resolved;
    }

    private static ModelJson resolveInternal(String modelPath, int depth) {
        if (depth > MAX_DEPTH) {
            CatFrame.logger.error("Model parent chain too deep for: {}", modelPath);
            return null;
        }

        // 优先检查硬编码的内建模型（路径以 "builtin/" 开头）
        if (modelPath.startsWith("builtin/")) {
            ModelJson builtin = builtinModels.get(modelPath);
            if (builtin != null) {
                CatFrame.logger.debug("Using builtin model: {}", modelPath);
                return deepCopy(builtin);
            }
            CatFrame.logger.warn("Unknown builtin model: {}", modelPath);
            return null;
        }

        // Load model from JSON resource files
        ModelJson model = loadFromResources(modelPath);
        if (model == null) {
            CatFrame.logger.warn("Could not find model: {}", modelPath);
            return null;
        }

        // If no parent, return as-is
        if (model.parent == null || model.parent.isEmpty()) {
            return model;
        }

        // Resolve parent recursively
        ModelJson parent = resolveInternal(model.parent, depth + 1);
        if (parent == null) {
            return model;
        }

        // Merge: child overrides parent
        return merge(parent, model);
    }

    /**
     * Merge parent and child models. Child overrides parent's textures.
     * If child has no elements, inherit from parent.
     */
    private static ModelJson merge(ModelJson parent, ModelJson child) {
        ModelJson merged = new ModelJson();

        // Elements: child overrides parent if present
        if (child.elements != null && !child.elements.isEmpty()) {
            merged.elements = child.elements;
        } else {
            merged.elements = parent.elements;
        }

        // Textures: merge with child overriding parent
        merged.textures = new HashMap<>();
        if (parent.textures != null) {
            merged.textures.putAll(parent.textures);
        }
        if (child.textures != null) {
            merged.textures.putAll(child.textures);
        }

        // Display: child overrides parent per-slot, merge map
        if (parent.display != null || child.display != null) {
            merged.display = new HashMap<>();
            if (parent.display != null) merged.display.putAll(parent.display);
            if (child.display != null) merged.display.putAll(child.display);
        }

        // gui_light: child overrides parent
        merged.guiLight = (child.guiLight != null) ? child.guiLight : parent.guiLight;

        // texture_size: child overrides parent
        merged.texture_size = (child.texture_size != null) ? child.texture_size : parent.texture_size;

        return merged;
    }

    /**
     * 纹理变量不允许循环映射，例如 {@code "particle": "#texture"} 和
     * {@code "texture": "#particle"} 同时存在。这种情况下这两个纹理变量
     * 都会被映射到无效纹理（Missingno），在游戏的日志中也会产生警告。
     * <p>
     * 解析纹理变量引用（例如 #all → 实际纹理路径）。
     * 以 '#' 开头的纹理值表示引用另一个纹理键。
     */
    private static void resolveTextureVariables(ModelJson model) {
        if (model.textures == null) return;

        // Missingno 纹理的标识符
        final String MISSINGNO = "minecraft:missingno";

        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, String> entry : model.textures.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // 追踪引用链，用于循环检测
            // 使用 LinkedHashSet 提供 O(1) contains 查询 + 保持插入序（用于日志链展示）
            Set<String> chain = new LinkedHashSet<>();
            chain.add(key);

            while (value.startsWith("#")) {
                String ref = value.substring(1);

                // 循环检测：如果该引用已在链中出现过，说明存在循环
                if (chain.contains(ref)) {
                    chain.add(ref);
                    // 构建警告消息：chain -> ref1 -> ref2 -> ... -> cycleKey
                    StringBuilder chainMsg = new StringBuilder();
                    boolean first = true;
                    for (String elem : chain) {
                        if (!first) chainMsg.append("->");
                        chainMsg.append(elem);
                        first = false;
                    }
                    CatFrame.logger.warn(
                        "Unable to resolve texture due to reference chain {} in {}",
                        chainMsg, key
                    );
                    value = MISSINGNO;
                    break;
                }
                chain.add(ref);

                String refValue = model.textures.get(ref);
                if (refValue == null) {
                    // 引用的纹理变量不存在，使用 missingno
                    value = MISSINGNO;
                    break;
                }
                value = refValue;
            }
            resolved.put(key, value);
        }
        model.textures = resolved;
    }

    /**
     * Load a model JSON from the classpath resources.
     * Search order:
     * 1. If modelPath contains ':', use it as namespace:path
     * 2. Otherwise search: assets/minecraft/models/{path}.json
     * 3. Fallback: each registered mod namespace
     */
    private static ModelJson loadFromResources(String modelPath) {
        List<String> searchPaths = new ArrayList<>();

        if (modelPath.contains(":")) {
            // Explicit namespace (e.g., "minecraft:block/cube")
            String namespace = modelPath.substring(0, modelPath.indexOf(':'));
            String path = modelPath.substring(modelPath.indexOf(':') + 1);
            searchPaths.add("/assets/" + namespace + "/models/" + path + ".json");
        } else {
            // Default: search minecraft then all registered namespaces
            searchPaths.add("/assets/minecraft/models/" + modelPath + ".json");
            for (String ns : registeredNamespaces) {
                if (!ns.equals("minecraft")) {
                    searchPaths.add("/assets/" + ns + "/models/" + modelPath + ".json");
                }
            }
        }

        for (String resourcePath : searchPaths) {
            try (InputStream stream = ModelResolver.class.getResourceAsStream(resourcePath)) {
                if (stream != null) {
                    InputStreamReader reader = new InputStreamReader(stream);
                    ModelJson model = gson.fromJson(reader, ModelJson.class);
                    if (model != null) {
                        CatFrame.logger.debug("Loaded model from: {}", resourcePath);
                        return model;
                    }
                }
            } catch (Exception e) {
                CatFrame.logger.error("Error loading model {}: {}", resourcePath, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Register a mod namespace for model searching.
     * Other mods can call this to make their models discoverable.
     */
    public static void registerNamespace(String namespace) {
        if (!registeredNamespaces.contains(namespace)) {
            registeredNamespaces.add(namespace);
        }
    }

    private static ModelJson deepCopy(ModelJson source) {
        ModelJson copy = new ModelJson();
        if (source.parent != null) {
            copy.parent = source.parent;
        }
        if (source.textures != null) {
            copy.textures = new HashMap<>(source.textures);
        }
        if (source.elements != null) {
            copy.elements = new ArrayList<>(source.elements);
        }
        if (source.display != null) {
            copy.display = new HashMap<>(source.display);
        }
        copy.guiLight = source.guiLight;
        if (source.texture_size != null) {
            copy.texture_size = source.texture_size.clone();
        }
        return copy;
    }

    /**
     * Clear the model cache. Call when resources are reloaded.
     */
    public static void clearCache() {
        cache.clear();
    }

    /**
     * Collect all texture paths referenced in a resolved model.
     * Returns texture paths without the '#' prefix (already resolved).
     */
    public static Set<String> collectTextures(ModelJson model) {
        Set<String> textures = new HashSet<>();
        if (model == null || model.textures == null) return textures;

        for (String value : model.textures.values()) {
            if (!value.startsWith("#")) {
                textures.add(value);
            }
        }
        return textures;
    }
}
