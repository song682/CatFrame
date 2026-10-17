package decok.dfcdvadstf.catframe.model.core;

import com.google.gson.Gson;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves model JSON files with parent inheritance chain.
 * Merges textures and elements from parent models recursively.
 */
public class ModelResolver {
    public static final Gson GSON = ModelJson.createGson();
    private static final Map<String, ModelJson> cache = new ConcurrentHashMap<>();
    private static final List<String> registeredNamespaces = new ArrayList<>();
    private static final int MAX_DEPTH = 16;

    /**
     * Cycle detection: tracks the model paths currently being resolved by this thread.
     * If a path already in the set is encountered again during resolution, a circular dependency exists.
     */
    private static final ThreadLocal<Set<String>> resolvingStack = new ThreadLocal<Set<String>>() {
        @Override
        protected Set<String> initialValue() {
            return new LinkedHashSet<>();
        }
    };

    /**
     * Dependency graph: records each model's parent dependency.
     * Used by topological sorting to determine bake order.
     * key = model path, value = set of parent paths this model depends on
     */
    private static final Map<String, Set<String>> dependencyGraph = new ConcurrentHashMap<>();

    // ==================== Builtin Models ====================

    /**
     * Hardcoded built-in models, which take priority over loading from JSON files.
     * Model paths starting with "builtin/" are all provided here.
     */
    private static final Map<String, ModelJson> builtinModels = new HashMap<>();

    static {
        registeredNamespaces.add("minecraft");
        builtinModels.put("builtin/generated", BuiltinGeneratedModel.create());
        builtinModels.put("builtin/missing",   BuiltinMissingModel.create());
    }

    /**
     * Resolve a model by path (e.g., "block/stone") with full parent chain resolution.
     * The resolved model will have all elements and textures merged from parents.
     *
     * <p>If the model is not found or fails to load, it automatically falls back to the
     * {@code builtin/missing} placeholder model (displays the purple/black missingno texture),
     * preventing render pipeline failures caused by missing models.
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
        } else {
            // [W5] Model load failed → fallback to builtin/missing (not cached, retried next time)
            CatFrame.logger.warn("[ModelResolver] Model '{}' not found or errored, falling back to builtin/missing", modelPath);
            resolved = deepCopy(builtinModels.get("builtin/missing"));
        }
        return resolved;
    }

    private static ModelJson resolveInternal(String modelPath, int depth) {
        if (depth > MAX_DEPTH) {
            CatFrame.logger.error("Model parent chain too deep for: {}", modelPath);
            return null;
        }

        // Cycle detection: check whether the current path is already in the resolution stack
        Set<String> stack = resolvingStack.get();
        if (!stack.add(modelPath)) {
            // Already in the stack → circular dependency
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
     * The actual resolution logic (extracted from the original resolveInternal);
     * cycle detection and stack management are handled by the outer resolveInternal.
     */
    private static ModelJson resolveInternalChecked(String modelPath, int depth) {

        // Check the hardcoded built-in models first (paths starting with "builtin/")
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

        // ambientocclusion: child overrides parent (model-level flag; when neither declares it the
        // value stays null and the bake stage applies the 26.1.2 default of true)
        merged.ambientocclusion = (child.ambientocclusion != null)
                ? child.ambientocclusion : parent.ambientocclusion;

        // raw: merge unrecognized keys (child overrides parent, same policy as textures/display), so compatibility snapshots are not lost through the inheritance chain
        if (parent.raw != null || child.raw != null) {
            merged.raw = new HashMap<>();
            if (parent.raw != null) merged.raw.putAll(parent.raw);
            if (child.raw != null) merged.raw.putAll(child.raw);
        }

        // builtinGenerated: OR propagation — if any ancestor comes from builtin/generated, the child model inherits this flag
        merged.builtinGenerated = parent.builtinGenerated || child.builtinGenerated;

        // texture_size: child overrides parent
        merged.texture_size = (child.texture_size != null) ? child.texture_size : parent.texture_size;

        // builtin/generated multi-layer expansion: scan the layerN texture keys and generate a separate element per layer
        // Supports up to 4 layers by default (layer0~layer3); ModernItem uses its own N-pass mechanism and is not subject to this limit
        if (merged.builtinGenerated) {
            expandBuiltinGeneratedLayers(merged);
        }

        return merged;
    }

    /**
     * Multi-layer expansion for builtin/generated.
     * <p>
     * Scans the merged texture map for {@code layer0}~{@code layer3} (at most 4 layers) and
     * creates, for each extra layer, a copy with the same geometry as the original element but
     * referencing a different texture, setting the matching {@code tintIndex} on each layer's
     * north/south faces (layer0→0, layer1→1, …).
     * <p>
     * Side quads are handled automatically by the bakeSideFaces loop in
     * {@link VanillaModelManager} based on texture key names, so none need to be generated here.
     */
    private static void expandBuiltinGeneratedLayers(ModelJson model) {
        if (model.textures == null || model.elements == null || model.elements.isEmpty()) return;

        // Collect the existing layerN entries (N >= 1)
        List<Integer> extraLayers = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            if (model.textures.containsKey("layer" + i)) {
                extraLayers.add(i);
            }
        }
        if (extraLayers.isEmpty()) return;

        // Copy the elements list defensively to avoid polluting the parent model's reference
        model.elements = new ArrayList<>(model.elements);

        // Deep-copy the original element so modifying tintIndex does not affect the static built-in model
        ModelJson.Element original = deepCopyElement(model.elements.get(0));
        model.elements.set(0, original);
        ensureFaceTintIndex(original, 0);

        // Create an element copy for each extra layer
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
     * Ensures the tintIndex of every face of the element matches the given layer number.
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
     * Deep-copies an Element (only geometry and face data are copied; no references are shared).
     */
    private static ModelJson.Element deepCopyElement(ModelJson.Element src) {
        ModelJson.Element dst = new ModelJson.Element();
        dst.from = src.from != null ? src.from.clone() : null;
        dst.to   = src.to   != null ? src.to.clone()   : null;
        dst.ambientocclusion = src.ambientocclusion;
        dst.shade = src.shade;
        dst.shadeDirectionOverride = src.shadeDirectionOverride;
        dst.raw = src.raw;
        if (src.rotation != null) {
            dst.rotation = new ModelJson.Rotation();
            dst.rotation.angle = src.rotation.angle;
            dst.rotation.axis  = src.rotation.axis;
            dst.rotation.origin = src.rotation.origin != null ? src.rotation.origin.clone() : null;
            dst.rotation.x = src.rotation.x;
            dst.rotation.y = src.rotation.y;
            dst.rotation.z = src.rotation.z;
            dst.rotation.rescale = src.rotation.rescale;
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
        dst.raw = src.raw;
        return dst;
    }

    /**
     * Texture variables may not map in cycles, e.g. {@code "particle": "#texture"} and
     * {@code "texture": "#particle"} being both present. In that case both texture variables
     * resolve to the invalid texture (Missingno), and a warning is also logged in-game.
     * <p>
     * Resolves texture variable references (e.g. #all → the actual texture path).
     * A texture value starting with '#' references another texture key.
     * <p>
     * Namespace fallback: unqualified texture references default to the
     * {@code minecraft} namespace (ResourceLocation semantics); without this
     * they miss the fully-qualified publication keys and render missingno.
     */
    public static void resolveTextureVariables(ModelJson model) {
        if (model.textures == null) return;

        // Identifier of the Missingno texture
        final String MISSINGNO = "minecraft:missingno";

        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, String> entry : model.textures.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Track the reference chain for cycle detection
            // LinkedHashSet provides O(1) contains and preserves insertion order (for logging the chain)
            Set<String> chain = new LinkedHashSet<>();
            chain.add(key);

            while (value.startsWith("#")) {
                String ref = value.substring(1);

                // Cycle detection: if this reference already appeared in the chain, a cycle exists
                if (chain.contains(ref)) {
                    chain.add(ref);
                    // Build the warning message: chain -> ref1 -> ref2 -> ... -> cycleKey
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
                    // The referenced texture variable does not exist; use missingno
                    value = MISSINGNO;
                    break;
                }
                value = refValue;
            }

            // Unqualified texture path -> default to the minecraft namespace
            if (!value.startsWith("#") && value.indexOf(':') < 0) {
                CatFrame.logger.debug("[ModelResolver] texture '{}' has no namespace, defaulting to minecraft:{}",
                        value, value);
                value = "minecraft:" + value;
            }

            resolved.put(key, value);
        }
        model.textures = resolved;
    }

    /**
     * Load a model JSON, prioritizing Minecraft's IResourceManager
     * (which sees resource packs) over classpath-only loading.
     *
     * Search order:
     * 1. IResourceManager (vanilla + mod + resource pack resources)
     * 2. Classpath fallback (for server-side or pre-init contexts)
     */
    private static ModelJson loadFromResources(String modelPath) {
        // Try IResourceManager first (has access to resource packs)
        ModelJson fromManager = loadFromResourceManager(modelPath);
        if (fromManager != null) return fromManager;

        // Fall back to the classpath
        return loadFromClasspath(modelPath);
    }

    /**
     * Loads a model through Minecraft's IResourceManager.
     * Resource packs, vanilla assets, and mod assets are all accessible through this path.
     */
    private static ModelJson loadFromResourceManager(String modelPath) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return null;
            IResourceManager rm = mc.getResourceManager();
            if (rm == null) return null;

            if (modelPath.contains(":")) {
                String ns = modelPath.substring(0, modelPath.indexOf(':'));
                String path = modelPath.substring(modelPath.indexOf(':') + 1);
                return tryLoadFromManager(rm, ns, "models/" + path + ".json");
            } else {
                // Search minecraft first by default, then the other registered namespaces
                ModelJson result = tryLoadFromManager(rm, "minecraft", "models/" + modelPath + ".json");
                if (result != null) return result;

                for (String ns : registeredNamespaces) {
                    if (!ns.equals("minecraft")) {
                        result = tryLoadFromManager(rm, ns, "models/" + modelPath + ".json");
                        if (result != null) return result;
                    }
                }
            }
        } catch (Exception e) {
            CatFrame.logger.debug("Resource manager model loading unavailable: {}", e.getMessage());
        }
        return null;
    }

    private static ModelJson tryLoadFromManager(IResourceManager rm, String ns, String resourcePath) {
        try {
            ResourceLocation loc = new ResourceLocation(ns, resourcePath);
            IResource res = rm.getResource(loc);
            if (res != null) {
                InputStreamReader reader = new InputStreamReader(res.getInputStream());
                ModelJson model = GSON.fromJson(reader, ModelJson.class);
                if (model != null) {
                    CatFrame.logger.debug("Loaded model from resource manager: {}:{}", ns, resourcePath);
                    return model;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * Loads a model from the classpath (fallback path).
     * Used when IResourceManager is unavailable (server side / early initialization).
     */
    private static ModelJson loadFromClasspath(String modelPath) {
        List<String> searchPaths = new ArrayList<>();

        if (modelPath.contains(":")) {
            String namespace = modelPath.substring(0, modelPath.indexOf(':'));
            String path = modelPath.substring(modelPath.indexOf(':') + 1);
            searchPaths.add("/assets/" + namespace + "/models/" + path + ".json");
        } else {
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
                        CatFrame.logger.debug("Loaded model from classpath: {}", resourcePath);
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
        copy.raw = source.raw;
        copy.guiLight = source.guiLight;
        copy.ambientocclusion = source.ambientocclusion;
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
     * Returns a snapshot of the dependency graph, used by topological sorting to determine bake order.
     */
    public static Map<String, Set<String>> getDependencyGraph() {
        return Collections.unmodifiableMap(dependencyGraph);
    }

    /**
     * Topological sort: returns model paths ordered by dependency.
     * Models with no dependencies (leaf nodes) are baked first, then the models that depend on them.
     * <p>
     * Uses Kahn's algorithm (BFS topological sort).
     *
     * @param modelPaths the set of model paths to sort
     * @return the model paths in topological order
     */
    public static List<String> topologicalSort(Set<String> modelPaths) {
        // Build the in-degree table and adjacency list (only models within modelPaths are considered)
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

        // BFS: start from nodes with in-degree 0
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

        // If the result is smaller than the input, a cycle exists (should not happen, since resolve already detects cycles)
        if (sorted.size() < modelPaths.size()) {
            Set<String> unsorted = new LinkedHashSet<>(modelPaths);
            unsorted.removeAll(sorted);
            CatFrame.logger.warn("[ModelResolver] topologicalSort: {} models in cycle: {}",
                    unsorted.size(), unsorted);
            // Append the remainder to the end (graceful degradation)
            sorted.addAll(unsorted);
        }

        return sorted;
    }

    /**
     * Builds the circular-dependency path string for the error log.
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
