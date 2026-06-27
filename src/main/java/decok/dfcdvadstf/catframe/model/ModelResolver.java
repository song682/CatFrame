package decok.dfcdvadstf.catframe.model;

import com.google.gson.Gson;
import decok.dfcdvadstf.catframe.CatFrame;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves model JSON files with parent inheritance chain.
 * Merges textures and elements from parent models recursively.
 */
public class ModelResolver {
    public static final Gson GSON = new Gson();
    private static final Map<String, ModelJson> cache = new ConcurrentHashMap<>();
    private static final List<String> registeredNamespaces = new ArrayList<>();
    private static final int MAX_DEPTH = 16;

    /**
     * 循环检测：跟踪当前线程正在解析的模型路径。
     * 如果 resolve 过程中再次遇到已在集合中的路径，说明存在循环依赖。
     */
    private static final ThreadLocal<Set<String>> resolvingStack = new ThreadLocal<Set<String>>() {
        @Override
        protected Set<String> initialValue() {
            return new LinkedHashSet<>();
        }
    };

    /**
     * 依赖图：记录每个模型的 parent 依赖关系。
     * 用于拓扑排序确定烘焙顺序。
     * key = 模型路径, value = 该模型依赖的 parent 路径集合
     */
    private static final Map<String, Set<String>> dependencyGraph = new ConcurrentHashMap<>();

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
        generated.builtinGenerated = true;

        // 带 1px 厚度的平面 element，与原版 ItemModelGenerator 一致
        // MIN_Z=7.5, MAX_Z=8.5，1像素厚度防止双面共面 z-fighting
        ModelJson.Element elem = new ModelJson.Element();
        elem.from = new float[]{0, 0, 7.5f};
        elem.to = new float[]{16, 16, 8.5f};
        elem.faces = new ModelJson.Faces();
        // north 面（-Z方向）UV 水平翻转：与 26.1.2 NORTH_FACE_UVS 一致
        elem.faces.north = new ModelJson.Face();
        elem.faces.north.texture = "#layer0";
        elem.faces.north.uv = new float[]{16, 0, 0, 16};
        elem.faces.north.tintIndex = 0;
        // south 面（+Z方向）正常 UV：与 26.1.2 SOUTH_FACE_UVS 一致
        elem.faces.south = new ModelJson.Face();
        elem.faces.south.texture = "#layer0";
        elem.faces.south.uv = new float[]{0, 0, 16, 16};
        elem.faces.south.tintIndex = 0;
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

        Set<String> stack = resolvingStack.get();
        stack.clear();
        ModelJson resolved = resolveInternal(modelPath, 0);
        stack.clear();

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

        // 循环检测：检查当前路径是否已在解析栈中
        Set<String> stack = resolvingStack.get();
        if (!stack.add(modelPath)) {
            // 已经在栈中 → 循环依赖
            CatFrame.logger.error("Circular model dependency detected: {} -> ... -> {}",
                    buildCyclePath(stack, modelPath), modelPath);
            return null;
        }

        try {
            return resolveInternalChecked(modelPath, depth);
        } finally {
            stack.remove(modelPath);
        }
    }

    /**
     * 实际的解析逻辑（从原 resolveInternal 提取），
     * 循环检测和栈管理由外层 resolveInternal 处理。
     */
    private static ModelJson resolveInternalChecked(String modelPath, int depth) {

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

        // Resolve parent recursively and record dependency
        if (model.parent != null && !model.parent.isEmpty()) {
            dependencyGraph.computeIfAbsent(modelPath, k -> new LinkedHashSet<>()).add(model.parent);
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

        // builtinGenerated: OR 传播 — 只要任一祖先来自 builtin/generated，子模型就继承该标记
        merged.builtinGenerated = parent.builtinGenerated || child.builtinGenerated;

        // texture_size: child overrides parent
        merged.texture_size = (child.texture_size != null) ? child.texture_size : parent.texture_size;

        // builtin/generated 多层扩展：扫描纹理中的 layerN 键，为每层生成独立 element
        // 默认最多支持 4 层（layer0~layer3），ModernItem 走自己的 N-pass 机制不受此限制
        if (merged.builtinGenerated) {
            expandBuiltinGeneratedLayers(merged);
        }

        return merged;
    }

    /**
     * builtin/generated 多层扩展。
     * <p>
     * 扫描合并后的纹理映射，找出 {@code layer0}~{@code layer3}（最多 4 层）。
     * 为每个额外层创建一个与原始 element 相同几何体但引用不同纹理的副本，
     * 并为每层的 north/south 面设置对应的 {@code tintIndex}（layer0→0, layer1→1, …）。
     * <p>
     * 侧面 quad 由 {@link VanillaModelManager} 中的 bakeSideFaces 循环按纹理键名自动处理，
     * 无需在此处额外生成。
     */
    private static void expandBuiltinGeneratedLayers(ModelJson model) {
        if (model.textures == null || model.elements == null || model.elements.isEmpty()) return;

        // 收集存在的 layerN（N >= 1）
        List<Integer> extraLayers = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            if (model.textures.containsKey("layer" + i)) {
                extraLayers.add(i);
            }
        }
        if (extraLayers.isEmpty()) return;

        // 安全拷贝 elements 列表，避免污染父模型引用
        model.elements = new ArrayList<>(model.elements);

        // 深拷贝原始 element，避免修改 tintIndex 时污染静态 builtin 模型
        ModelJson.Element original = deepCopyElement(model.elements.get(0));
        model.elements.set(0, original);
        ensureFaceTintIndex(original, 0);

        // 为每个额外层创建 element 副本
        for (int layer : extraLayers) {
            ModelJson.Element elem = deepCopyElement(original);
            if (elem.faces.north != null) elem.faces.north.texture = "#layer" + layer;
            if (elem.faces.south != null) elem.faces.south.texture = "#layer" + layer;
            if (elem.faces.east  != null) elem.faces.east.texture  = "#layer" + layer;
            if (elem.faces.west  != null) elem.faces.west.texture  = "#layer" + layer;
            if (elem.faces.up    != null) elem.faces.up.texture    = "#layer" + layer;
            if (elem.faces.down  != null) elem.faces.down.texture  = "#layer" + layer;
            ensureFaceTintIndex(elem, layer);
            model.elements.add(elem);
        }

        CatFrame.logger.debug("[ModelResolver] expanded builtin/generated to {} layers",
                model.elements.size());
    }

    /**
     * 确保 element 的所有面的 tintIndex 与给定层号一致。
     */
    private static void ensureFaceTintIndex(ModelJson.Element elem, int tintIndex) {
        if (elem.faces == null) return;
        if (elem.faces.north != null) elem.faces.north.tintIndex = tintIndex;
        if (elem.faces.south != null) elem.faces.south.tintIndex = tintIndex;
        if (elem.faces.east  != null) elem.faces.east.tintIndex  = tintIndex;
        if (elem.faces.west  != null) elem.faces.west.tintIndex  = tintIndex;
        if (elem.faces.up    != null) elem.faces.up.tintIndex    = tintIndex;
        if (elem.faces.down  != null) elem.faces.down.tintIndex  = tintIndex;
    }

    /**
     * 深拷贝一个 Element（仅复制几何与面数据，不共享引用）。
     */
    private static ModelJson.Element deepCopyElement(ModelJson.Element src) {
        ModelJson.Element dst = new ModelJson.Element();
        dst.from = src.from != null ? src.from.clone() : null;
        dst.to   = src.to   != null ? src.to.clone()   : null;
        dst.ambientocclusion = src.ambientocclusion;
        dst.shade = src.shade;
        if (src.rotation != null) {
            dst.rotation = new ModelJson.Rotation();
            dst.rotation.angle = src.rotation.angle;
            dst.rotation.axis  = src.rotation.axis;
            dst.rotation.origin = src.rotation.origin != null ? src.rotation.origin.clone() : null;
            dst.rotation.x = src.rotation.x;
            dst.rotation.y = src.rotation.y;
            dst.rotation.z = src.rotation.z;
        }
        if (src.faces != null) {
            dst.faces = new ModelJson.Faces();
            dst.faces.north = copyFace(src.faces.north);
            dst.faces.south = copyFace(src.faces.south);
            dst.faces.east  = copyFace(src.faces.east);
            dst.faces.west  = copyFace(src.faces.west);
            dst.faces.up    = copyFace(src.faces.up);
            dst.faces.down  = copyFace(src.faces.down);
        }
        return dst;
    }

    private static ModelJson.Face copyFace(ModelJson.Face src) {
        if (src == null) return null;
        ModelJson.Face dst = new ModelJson.Face();
        dst.uv = src.uv != null ? src.uv.clone() : null;
        dst.texture  = src.texture;
        dst.rotation = src.rotation;
        dst.cullface = src.cullface;
        dst.tintIndex = src.tintIndex;
        return dst;
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
                    ModelJson model = GSON.fromJson(reader, ModelJson.class);
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
        copy.builtinGenerated = source.builtinGenerated;
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
        dependencyGraph.clear();
    }

    /**
     * 获取依赖图快照。用于拓扑排序确定烘焙顺序。
     */
    public static Map<String, Set<String>> getDependencyGraph() {
        return Collections.unmodifiableMap(dependencyGraph);
    }

    /**
     * 拓扑排序：返回按依赖顺序排列的模型路径列表。
     * 先烘焙无依赖的模型（叶子节点），再烘焙依赖它们的模型。
     * <p>
     * 使用 Kahn 算法（BFS 拓扑排序）。
     *
     * @param modelPaths 需要排序的模型路径集合
     * @return 按拓扑顺序排列的模型路径列表
     */
    public static List<String> topologicalSort(Set<String> modelPaths) {
        // 构建入度表和邻接表（只考虑 modelPaths 范围内的模型）
        Map<String, Set<String>> adj = new HashMap<>();  // parent → children
        Map<String, Integer> inDegree = new HashMap<>();

        for (String path : modelPaths) {
            inDegree.putIfAbsent(path, 0);
            Set<String> deps = dependencyGraph.getOrDefault(path, Collections.emptySet());
            for (String dep : deps) {
                if (modelPaths.contains(dep)) {
                    adj.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(path);
                    inDegree.merge(path, 1, Integer::sum);
                }
            }
        }

        // BFS：从入度为 0 的节点开始
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);
            for (String child : adj.getOrDefault(node, Collections.emptySet())) {
                int newDegree = inDegree.merge(child, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(child);
                }
            }
        }

        // 如果排序结果少于输入，说明有环（不应发生，因为 resolve 已经检测了）
        if (sorted.size() < modelPaths.size()) {
            Set<String> unsorted = new LinkedHashSet<>(modelPaths);
            unsorted.removeAll(sorted);
            CatFrame.logger.warn("[ModelResolver] topologicalSort: {} models in cycle: {}",
                    unsorted.size(), unsorted);
            // 将剩余的追加到末尾（降级处理）
            sorted.addAll(unsorted);
        }

        return sorted;
    }

    /**
     * 构建循环依赖路径字符串，用于错误日志。
     */
    private static String buildCyclePath(Set<String> stack, String cycleTarget) {
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (String s : stack) {
            if (s.equals(cycleTarget)) found = true;
            if (found) {
                if (sb.length() > 0) sb.append(" -> ");
                sb.append(s);
            }
        }
        return sb.toString();
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
