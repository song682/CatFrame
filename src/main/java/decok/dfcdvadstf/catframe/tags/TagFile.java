package decok.dfcdvadstf.catframe.tags;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Tag 文件 - 表示从 JSON 文件加载的完整标签定义
 * 
 * 类似 26.1 的 TagFile，包含：
 * - entries: 标签条目列表
 * - replace: 是否替换已有内容
 * 
 * JSON 格式示例：
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
    
    /** 标签条目列表 */
    private final List<TagEntry> entries;
    
    /** 是否替换已有内容 */
    private final boolean replace;
    
    public TagFile(List<TagEntry> entries, boolean replace) {
        this.entries = entries;
        this.replace = replace;
    }
    
    /**
     * 从 JSON 对象解析 TagFile
     */
    public static TagFile fromJson(JsonObject json) {
        if (json == null) {
            LOGGER.warn("Null JSON provided to TagFile.fromJson");
            return new TagFile(new ArrayList<>(), false);
        }
        
        // 解析 replace 字段
        boolean replace = false;
        if (json.has("replace")) {
            replace = json.get("replace").getAsBoolean();
        }
        
        // 解析 values 数组
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
     * 获取标签条目列表
     */
    public List<TagEntry> getEntries() {
        return entries;
    }
    
    /**
     * 是否替换已有内容
     */
    public boolean isReplace() {
        return replace;
    }
    
    @Override
    public String toString() {
        return "TagFile{replace=" + replace + ", entries=" + entries.size() + "}";
    }
}
