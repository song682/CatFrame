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

  static {
    registeredNamespaces.add("minecraft");
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

    return merged;
  }

  /**
   * Resolve texture variable references (e.g., #all -> actual texture path).
   * A texture value starting with '#' references another texture key.
   */
  private static void resolveTextureVariables(ModelJson model) {
    if (model.textures == null) return;

    // Resolve texture indirection (max 8 levels to prevent cycles)
    Map<String, String> resolved = new HashMap<>();
    for (Map.Entry<String, String> entry : model.textures.entrySet()) {
      String value = entry.getValue();
      int resolveCount = 0;
      while (value.startsWith("#") && resolveCount < 8) {
        String ref = value.substring(1);
        String refValue = model.textures.get(ref);
        if (refValue == null) break;
        value = refValue;
        resolveCount++;
      }
      resolved.put(entry.getKey(), value);
    }
    model.textures = resolved;
  }

  /**
   * Load a model JSON from the classpath resources.
   * Search order:
   *   1. If modelPath contains ':', use it as namespace:path
   *   2. Otherwise search: assets/minecraft/models/{path}.json
   *   3. Fallback: each registered mod namespace
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
