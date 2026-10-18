package decok.dfcdvadstf.catframe.tags;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed content of one tag JSON file, mirroring the modern {@code TagFile}.
 *
 * <pre>
 * {
 *   "replace": false,
 *   "values": [
 *     "minecraft:wool",
 *     "#minecraft:logs",
 *     { "id": "minecraft:stone", "required": false }
 *   ]
 * }
 * </pre>
 */
public final class TagFile {

    private final List<TagEntry> entries;
    private final boolean replace;

    public TagFile(List<TagEntry> entries, boolean replace) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.replace = replace;
    }

    /** Parses a tag file. Malformed entries raise {@link IllegalArgumentException}. */
    public static TagFile fromJson(JsonObject json) {
        if (json == null) {
            throw new IllegalArgumentException("tag file is null");
        }
        boolean replace = false;
        JsonElement replaceElement = json.get("replace");
        if (replaceElement != null && replaceElement.isJsonPrimitive()) {
            replace = replaceElement.getAsBoolean();
        }

        JsonElement valuesElement = json.get("values");
        if (valuesElement == null || !valuesElement.isJsonArray()) {
            throw new IllegalArgumentException("tag file is missing a 'values' array");
        }
        JsonArray values = valuesElement.getAsJsonArray();
        List<TagEntry> entries = new ArrayList<>(values.size());
        for (JsonElement value : values) {
            entries.add(TagEntry.fromJson(value));
        }
        return new TagFile(entries, replace);
    }

    /** Entries in declaration order. */
    public List<TagEntry> entries() {
        return entries;
    }

    /** @return true when this file replaces lower-priority contributions. */
    public boolean replace() {
        return replace;
    }

    /** Compatibility accessor. */
    public List<TagEntry> getEntries() {
        return entries;
    }

    /** Compatibility accessor. */
    public boolean isReplace() {
        return replace;
    }

    @Override
    public String toString() {
        return "TagFile{replace=" + replace + ", entries=" + entries.size() + '}';
    }
}
