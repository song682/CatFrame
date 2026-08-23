package decok.dfcdvadstf.catframe.datagen.tag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Builder for block/item tag files (the modern {@code TagsProvider} counterpart).
 * <p>
 * Tags serialize to the exact 1.7.10 layout the loader expects:
 * {@code {"replace": false, "values": [...]}}. Values are plain registry
 * names (e.g. {@code "minecraft:log"}) or tag references with the
 * {@code #} prefix (e.g. {@code "#catframe:logs"}) — see
 * {@link #ref(String)}.
 * <p>
 * 方块/物品 tag 文件构建器（高版本 {@code TagsProvider} 的对应物）。
 * tag 序列化为加载端期望的 1.7.10 布局：{@code {"replace": false, "values": [...]}}。
 * 值为普通注册表名（如 {@code "minecraft:log"}）或带 {@code #} 前缀的 tag 引用
 * （如 {@code "#catframe:logs"}），见 {@link #ref(String)}。
 */
public final class TagBuilder {

    private TagBuilder() {
    }

    /**
     * Build a tag file with {@code replace: false}.
     *
     * @param values registry names and tag references
     * @return {@code {"replace": false, "values": [...]}}
     */
    public static JsonObject tag(String... values) {
        return tag(false, values);
    }

    /**
     * Build a tag file.
     *
     * @param replace whether this tag replaces lower-priority pack entries
     * @param values  registry names and tag references
     * @return {@code {"replace": <replace>, "values": [...]}}
     */
    public static JsonObject tag(boolean replace, String... values) {
        JsonObject obj = new JsonObject();
        obj.addProperty("replace", replace);
        JsonArray arr = new JsonArray();
        for (String v : values) {
            arr.add(new JsonPrimitive(v));
        }
        obj.add("values", arr);
        return obj;
    }

    /**
     * Build a tag reference value (points at another tag).
     *
     * @param tagPath tag path without namespace or {@code #} prefix
     *                (e.g. {@code "catframe:logs"} → {@code "#catframe:logs"})
     * @return the {@code #}-prefixed reference
     */
    public static String ref(String tagPath) {
        return "#" + tagPath;
    }
}
