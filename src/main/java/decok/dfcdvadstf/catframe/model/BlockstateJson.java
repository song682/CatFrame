package decok.dfcdvadstf.catframe.model;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data class for blockstate JSON files.
 * Supports both "variants" and "multipart" formats like Minecraft 1.8+.
 *
 * === Variants format ===
 * {<br>
 *   "variants": {<br>
 *     "normal": { "model": "block/stone" },<br>
 *     "facing=north": { "model": "block/furnace", "y": 0 },<br>
 *     "facing=east":  { "model": "block/furnace", "y": 90 },<br>
 *     "facing=south": { "model": "block/furnace", "y": 180 },<br>
 *     "facing=west":  { "model": "block/furnace", "y": 270 }<br>
 *   }
 * }
 *
 * Variant value can also be an array for weighted random selection:
 * {<br>
 *   "variants": {
 *     "normal": [<br>
 *       { "model": "block/stone", "weight": 1 },
 *       { "model": "block/stone_mirrored", "weight": 1 }<br>
 *     ]<br>
 *   }<br>
 * }<br>
 *
 * === Multipart format ===
 * {<br>
 *   "multipart": [
 *     { "apply": { "model": "block/fence_post" } },<br>
 *     { "when": { "north": "true" }, "apply": { "model": "block/fence_side" } },<br>
 *     { "when": { "south": "true" }, "apply": { "model": "block/fence_side", "y": 180 } }<br>
 *   ]<br>
 * }<br>
 */
public class BlockstateJson {
  /** Variants-based model mapping. Key is property combination (e.g., "facing=north,half=upper") */
  public Map<String, VariantEntry> variants;

  /** Multipart model composition */
  public List<MultipartCase> multipart;

  // ==================== Variant ====================

  /**
   * A variant entry can be either a single Variant or an array of Variants (for weighted random).
   * Custom deserialization handles both cases.
   */
  public static class VariantEntry {
    /** Single variant (if not an array) */
    public Variant single;
    /** Multiple variants with weights (if array) */
    public List<Variant> list;

    public boolean isArray() {
      return list != null && !list.isEmpty();
    }

    /** Get the effective variant (first or weighted random) */
    public Variant getVariant(int seed) {
      if (!isArray()) return single;
      if (list.isEmpty()) return null;

      // Weighted random selection
      int totalWeight = 0;
      for (Variant v : list) {
        totalWeight += Math.max(v.weight, 1);
      }
      int roll = Math.abs(seed) % totalWeight;
      int acc = 0;
      for (Variant v : list) {
        acc += Math.max(v.weight, 1);
        if (roll < acc) return v;
      }
      return list.get(0);
    }
  }

  public static class Variant {
    /** Model path (e.g., "block/stone") */
    public String model;
    /** Y-axis rotation in degrees (0, 90, 180, 270) */
    public int y = 0;
    /** X-axis rotation in degrees (0, 90, 180, 270) */
    public int x = 0;
    /** Whether to apply UV lock when rotating */
    public boolean uvlock = false;
    /** Weight for random selection (default 1) */
    public int weight = 1;
  }

  // ==================== Multipart ====================

  public static class MultipartCase {
    /** Condition for this part to apply. Null means always apply. */
    @SerializedName("when")
    public MultipartWhen when;
    /** The model(s) to apply when condition is met */
    public Variant apply;
  }

  /**
   * Condition for a multipart case.
   * Each key is a block property name, value is the required property value.
   * Supports OR logic with nested conditions.
   */
  public static class MultipartWhen {
    /** Simple property conditions: key=property name, value=required value */
    public Map<String, String> conditions;
    /** OR conditions: at least one must match */
    @SerializedName("OR")
    public List<Map<String, String>> or;

    /**
     * Check if this condition matches the given block properties.
     */
    public boolean matches(Map<String, String> blockProperties) {
      if (or != null && !or.isEmpty()) {
        // OR mode: at least one group must fully match
        for (Map<String, String> group : or) {
          if (matchesAll(group, blockProperties)) return true;
        }
        return false;
      }
      if (conditions != null) {
        return matchesAll(conditions, blockProperties);
      }
      return true; // No condition = always matches
    }

    private static boolean matchesAll(Map<String, String> required, Map<String, String> actual) {
      for (Map.Entry<String, String> entry : required.entrySet()) {
        String actualValue = actual.get(entry.getKey());
        if (actualValue == null) return false;
        // Support pipe-separated values (e.g., "north|south")
        String[] allowedValues = entry.getValue().split("\\|");
        boolean found = false;
        for (String allowed : allowedValues) {
          if (allowed.equals(actualValue)) {
            found = true;
            break;
          }
        }
        if (!found) return false;
      }
      return true;
    }
  }

  // ==================== Custom Deserializer ====================

  /**
   * Custom deserializer for VariantEntry that handles both single object and array formats.
   */
  public static class VariantEntryDeserializer implements JsonDeserializer<VariantEntry> {
    @Override
    public VariantEntry deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      VariantEntry entry = new VariantEntry();
      if (json.isJsonArray()) {
        JsonArray arr = json.getAsJsonArray();
        entry.list = new ArrayList<>();
        for (JsonElement elem : arr) {
          entry.list.add(context.deserialize(elem, Variant.class));
        }
      } else if (json.isJsonObject()) {
        entry.single = context.deserialize(json, Variant.class);
      }
      return entry;
    }
  }

  /**
   * Custom deserializer for MultipartWhen to handle the flexible condition format.
   */
  public static class MultipartWhenDeserializer implements JsonDeserializer<MultipartWhen> {
    @Override
    public MultipartWhen deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      MultipartWhen when = new MultipartWhen();
      if (!json.isJsonObject()) return when;

      JsonObject obj = json.getAsJsonObject();
      if (obj.has("OR")) {
        when.or = new ArrayList<>();
        JsonArray orArray = obj.getAsJsonArray("OR");
        for (JsonElement elem : orArray) {
          Map<String, String> group = new HashMap<>();
          for (Map.Entry<String, JsonElement> e : elem.getAsJsonObject().entrySet()) {
            group.put(e.getKey(), e.getValue().getAsString());
          }
          when.or.add(group);
        }
      } else {
        when.conditions = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
          when.conditions.put(e.getKey(), e.getValue().getAsString());
        }
      }
      return when;
    }
  }

  /**
   * Create a Gson instance configured with custom deserializers for blockstate parsing.
   */
  public static Gson createGson() {
    return new GsonBuilder()
        .registerTypeAdapter(VariantEntry.class, new VariantEntryDeserializer())
        .registerTypeAdapter(MultipartWhen.class, new MultipartWhenDeserializer())
        .create();
  }
}
