package decok.dfcdvadstf.catframe.tags;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Tag file - represents a complete tag definition loaded from a JSON file
 * 
 * Similar to 26.1's TagFile, containing:
 * - entries: List of tag entries
 * - replace: Whether to replace existing content   
 * 
 * JSON format example:
 * <pre>
 * {
 *   "replace": false,
 *   "values": [
 *     "minecraft:wool",
 *     "#catframe:carpet"
 *   ]
 * }
 * </pre>
 */
public class TagFile {
    
    private static final Logger LOGGER = LogManager.getLogger(TagFile.class);
    
    /** List of tag entries */
    private final List<TagEntry> entries;
    
    /** Whether to replace existing content */
    private final boolean replace;
    
    public TagFile(List<TagEntry> entries, boolean replace) {
        this.entries = entries;
        this.replace = replace;
    }
    
    /**
     * Parse TagFile from JSON object
     */
    public static TagFile fromJson(JsonObject json) {
        if (json == null) {
            LOGGER.warn("Null JSON provided to TagFile.fromJson");
            return new TagFile(new ArrayList<>(), false);
        }
        
        // Parse replace field
        boolean replace = false;
        if (json.has("replace")) {
            replace = json.get("replace").getAsBoolean();
        }
        
        // Parse values array
        List<TagEntry> entries = new ArrayList<>();
        if (json.has("values")) {
            com.google.gson.JsonArray valuesArray = json.getAsJsonArray("values");
            for (com.google.gson.JsonElement element : valuesArray) {
                String value = element.getAsString();
                TagEntry entry = TagEntry.parse(value);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        
        return new TagFile(entries, replace);
    }
    
    /**
     * Get the list of tag entries
     */
    public List<TagEntry> getEntries() {
        return entries;
    }
    
    /**
     * Whether to replace existing content
     */
    public boolean isReplace() {
        return replace;
    }
    
    @Override
    public String toString() {
        return "TagFile{replace=" + replace + ", entries=" + entries.size() + "}";
    }
}
