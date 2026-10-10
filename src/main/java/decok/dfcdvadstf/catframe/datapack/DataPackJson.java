package decok.dfcdvadstf.catframe.datapack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.StringReader;

/**
 * Shared Gson policy for the datapack pipeline (worklist B1).
 * <p>
 * One configured {@link Gson} instance for the whole module. Parsing happens on
 * the Gson tree model, which retains every field of the source document
 * (including fields this codebase does not understand yet) — the same
 * raw-retention principle as the model side's {@code rawJson}: an unknown field
 * is never discarded, so later readers can still see it.
 * </p>
 * <p>
 * Gson deliberately does <em>not</em> provide type dispatch, reference
 * resolution, dependency ordering or error paths; those are handled by the
 * repository/loaders and funnel into {@link DataPackErrors}. The helpers here
 * only parse defensively and never throw on malformed input.
 * </p>
 */
public final class DataPackJson {

    private static final Gson GSON = new Gson();

    private DataPackJson() {
    }

    /** The shared Gson instance (also used for writing the state file). */
    public static Gson gson() {
        return GSON;
    }

    /**
     * Parses a document into a JSON object, reporting instead of throwing on
     * malformed input.
     *
     * @param content file content, may be {@code null} when unreadable
     * @param packId  producing pack (for the report)
     * @param path    pack-relative path (for the report)
     * @param errors  consolidated report sink
     * @return the parsed object, or {@code null} when unreadable / not an object
     */
    public static JsonObject parseObject(String content, String packId, String path, DataPackErrors errors) {
        if (content == null) {
            errors.error(packId, path, "file could not be read");
            return null;
        }
        try {
            JsonElement element = new JsonParser().parse(new StringReader(content));
            if (element == null || !element.isJsonObject()) {
                errors.error(packId, path, "root element is not a JSON object");
                return null;
            }
            return element.getAsJsonObject();
        } catch (RuntimeException e) {
            errors.error(packId, path, "malformed JSON: " + e.getMessage());
            return null;
        }
    }

    /** String field with a default; type mismatches degrade silently. */
    public static String getString(JsonObject json, String key, String def) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return def;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** Int field with a default; type mismatches degrade silently. */
    public static int getInt(JsonObject json, String key, int def) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return def;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** Float field with a default; type mismatches degrade silently. */
    public static float getFloat(JsonObject json, String key, float def) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return def;
        }
        try {
            return element.getAsFloat();
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** Boolean field with a default; type mismatches degrade silently. */
    public static boolean getBoolean(JsonObject json, String key, boolean def) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return def;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** Array field, or {@code null} when absent / not an array. */
    public static JsonArray getArray(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        return element.getAsJsonArray();
    }

    /** Object field, or {@code null} when absent / not an object. */
    public static JsonObject getObject(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }
}
