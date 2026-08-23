package decok.dfcdvadstf.catframe.datagen.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import decok.dfcdvadstf.catframe.datagen.CachedOutput;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Unified JSON serialization for datagen output.
 * <p>
 * All generated files share one writer so the formatting stays stable:
 * two-space indent, null fields omitted, and object keys sorted with
 * {@code "parent"} first (mirroring the modern Minecraft
 * {@code FIXED_ORDER_FIELDS} comparator) for deterministic git diffs.
 * <p>
 * The load-side contract classes need custom serializers wherever the loader
 * uses a custom deserializer: {@link BlockstateJson.VariantEntry} (single vs
 * array unwrapping), {@link BlockstateJson.MultipartWhen} (condition map
 * flattening) and {@link ModelJson.Face} ({@code tintindex} omitted when -1).
 * Without these, Gson would emit the internal wrapper fields and the
 * generated files would drift from the format the loader expects.
 * <p>
 * 统一的 datagen JSON 序列化器。所有生成文件共用同一写出配置以保证格式稳定：
 * 两空格缩进、省略 null 字段、键排序时 {@code "parent"} 置首
 * （仿高版本 {@code FIXED_ORDER_FIELDS} 比较器），保证 git diff 确定性。
 * 加载端契约类凡有自定义反序列化器的地方，此处注册对应的自定义序列化器，
 * 确保生成文件的字段形态与加载端期望完全一致。
 */
public final class JsonWriter {

    private static final Gson COMPACT = createGson();
    private static final Gson PRETTY = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(BlockstateJson.VariantEntry.class, new VariantEntrySerializer())
            .registerTypeAdapter(BlockstateJson.Variant.class, new VariantSerializer())
            .registerTypeAdapter(BlockstateJson.MultipartWhen.class, new MultipartWhenSerializer())
            .registerTypeAdapter(ModelJson.Face.class, new FaceSerializer())
            .create();

    /** Keys that sort before the lexicographic order (higher priority = earlier). */
    private static final List<String> FIXED_ORDER_FIELDS = new ArrayList<>();
    static {
        FIXED_ORDER_FIELDS.add("parent");
        FIXED_ORDER_FIELDS.add("type");
    }

    private JsonWriter() {
    }

    private static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(BlockstateJson.VariantEntry.class, new VariantEntrySerializer())
                .registerTypeAdapter(BlockstateJson.Variant.class, new VariantSerializer())
                .registerTypeAdapter(BlockstateJson.MultipartWhen.class, new MultipartWhenSerializer())
                .registerTypeAdapter(ModelJson.Face.class, new FaceSerializer())
                .create();
    }

    /**
     * Serialize a POJO to a JSON tree (compact, no formatting).
     *
     * @param pojo any Gson-serializable object (or {@link JsonElement})
     * @return JSON tree
     */
    public static JsonElement toJsonElement(Object pojo) {
        return COMPACT.toJsonTree(pojo);
    }
    /**
     * Serialize a POJO to a pretty-printed string with sorted keys.
     *
     * @param pojo any Gson-serializable object (or {@link JsonElement})
     * @return formatted JSON text (no trailing newline)
     */
    public static String toJson(Object pojo) {
        return PRETTY.toJson(sortKeys(toJsonElement(pojo)));
    }

    /**
     * Serialize and write a POJO through the output cache.
     *
     * @param path  target file path
     * @param pojo  any Gson-serializable object
     * @param cache output cache (may skip identical content)
     */
    public static void write(Path path, Object pojo, CachedOutput cache) {
        cache.writeIfNeeded(path, toJson(pojo));
    }

    // ==================== Load-contract serializers ====================

    /**
     * Unwrap {@link BlockstateJson.VariantEntry}: a single variant serializes
     * as a plain object, a weighted list serializes as an array.
     */
    private static final class VariantEntrySerializer implements JsonSerializer<BlockstateJson.VariantEntry> {
        @Override
        public JsonElement serialize(BlockstateJson.VariantEntry entry, Type typeOfSrc,
                                     JsonSerializationContext context) {
            if (entry == null) {
                return JsonNull.INSTANCE;
            }
            if (entry.isArray()) {
                JsonArray arr = new JsonArray();
                for (BlockstateJson.Variant v : entry.list) {
                    arr.add(context.serialize(v));
                }
                return arr;
            }
            return context.serialize(entry.single);
        }
    }

    /**
     * Omit variant defaults: the hand-written files only spell out non-default
     * rotations / uvlock / weight, and the loader treats the omitted fields as
     * their defaults — emitting {@code "x":0,"y":0,"uvlock":false,"weight":1}
     * would break the zero-diff acceptance gate.
     */
    private static final class VariantSerializer implements JsonSerializer<BlockstateJson.Variant> {
        @Override
        public JsonElement serialize(BlockstateJson.Variant variant, Type typeOfSrc,
                                     JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            if (variant == null) {
                return obj;
            }
            if (variant.model != null) {
                obj.addProperty("model", variant.model);
            }
            if (variant.x != 0) {
                obj.addProperty("x", variant.x);
            }
            if (variant.y != 0 || variant.forceY) {
                obj.addProperty("y", variant.y);
            }
            if (variant.uvlock) {
                obj.addProperty("uvlock", true);
            }
            if (variant.weight != 1) {
                obj.addProperty("weight", variant.weight);
            }
            return obj;
        }
    }

    /**
     * Flatten {@link BlockstateJson.MultipartWhen}: the {@code conditions} map
     * becomes the plain object body, {@code or} becomes an {@code "OR"} array
     * (matching the loader's {@code MultipartWhenDeserializer}).
     */
    private static final class MultipartWhenSerializer implements JsonSerializer<BlockstateJson.MultipartWhen> {
        @Override
        public JsonElement serialize(BlockstateJson.MultipartWhen when, Type typeOfSrc,
                                     JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            if (when == null) {
                return obj;
            }
            if (when.or != null && !when.or.isEmpty()) {
                JsonArray or = new JsonArray();
                for (Map<String, String> group : when.or) {
                    JsonObject g = new JsonObject();
                    for (Map.Entry<String, String> e : group.entrySet()) {
                        g.addProperty(e.getKey(), e.getValue());
                    }
                    or.add(g);
                }
                obj.add("OR", or);
            } else if (when.conditions != null) {
                for (Map.Entry<String, String> e : when.conditions.entrySet()) {
                    obj.addProperty(e.getKey(), e.getValue());
                }
            }
            return obj;
        }
    }

    /**
     * Omit {@code tintindex} when it is the default {@code -1}; hand-written
     * models only ever spell out non-negative tint indices.
     */
    private static final class FaceSerializer implements JsonSerializer<ModelJson.Face> {
        @Override
        public JsonElement serialize(ModelJson.Face face, Type typeOfSrc,
                                     JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            if (face == null) {
                return obj;
            }
            if (face.uv != null) {
                obj.add("uv", context.serialize(face.uv));
            }
            if (face.texture != null) {
                obj.addProperty("texture", face.texture);
            }
            if (face.rotation != null) {
                obj.addProperty("rotation", face.rotation);
            }
            if (face.cullface != null) {
                obj.addProperty("cullface", face.cullface);
            }
            if (face.tintIndex != -1) {
                obj.addProperty("tintindex", face.tintIndex);
            }
            return obj;
        }
    }

    /**
     * Write an already-built JSON tree through the output cache.
     *
     * @param path    target file path
     * @param element JSON tree
     * @param cache   output cache (may skip identical content)
     */
    public static void write(Path path, JsonElement element, CachedOutput cache) {
        cache.writeIfNeeded(path, PRETTY.toJson(sortKeys(element)));
    }

    /**
     * Recursively sort object keys: {@code parent}/{@code type} first (in that
     * order), then lexicographic. Array order is preserved.
     *
     * @param element input JSON tree
     * @return a new tree with sorted keys (input is not mutated)
     */
    public static JsonElement sortKeys(JsonElement element) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
            return element;
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            JsonArray sorted = new JsonArray();
            for (JsonElement child : arr) {
                sorted.add(sortKeys(child));
            }
            return sorted;
        }
        JsonObject obj = element.getAsJsonObject();
        JsonObject sorted = new JsonObject();
        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(obj.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, JsonElement> e) -> {
            int idx = FIXED_ORDER_FIELDS.indexOf(e.getKey());
            return idx >= 0 ? idx : FIXED_ORDER_FIELDS.size();
        }).thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, JsonElement> entry : entries) {
            sorted.add(entry.getKey(), sortKeys(entry.getValue()));
        }
        return sorted;
    }
}
