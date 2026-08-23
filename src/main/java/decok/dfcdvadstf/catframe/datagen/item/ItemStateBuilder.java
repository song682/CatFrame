package decok.dfcdvadstf.catframe.datagen.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder DSL for {@code items/} decision-tree files (the modern
 * {@code ItemModelGenerators} counterpart).
 * <p>
 * The load-side {@link decok.dfcdvadstf.catframe.model.state.item.ItemStateNode}
 * tree uses final fields and a custom deserializer, so it cannot round-trip
 * through Gson serialization. This builder therefore emits the JSON tree
 * directly, using exactly the field names the deserializer expects
 * ({@code type}/{@code property}/{@code model}/{@code fallback}/{@code cases}/
 * {@code when}/{@code on_true}/{@code on_false}/{@code scale}/{@code entries}/
 * {@code threshold}/{@code tints}/{@code transformation}).
 * <p>
 * Node constructors are static methods returning {@link JsonObject}s so trees
 * nest naturally:
 * <pre>{@code
 * ItemStateBuilder.of(ItemStateBuilder.select("catframe:meta",
 *         ItemStateBuilder.model("minecraft:block/oak_log"),
 *         ItemStateBuilder.Case.when("0").then(ItemStateBuilder.model("minecraft:block/oak_log"))))
 * }</pre>
 * <p>
 * items 决策树文件的流式构建 DSL（高版本 {@code ItemModelGenerators} 的对应物）。
 * 加载端 {@link ItemStateNode} 决策树是 final 字段 + 自定义反序列化器，
 * 无法经 Gson 序列化往返，因此本构建器直接发射 JSON 树，字段名与反序列化器
 * 期望完全一致。节点构造函数为返回 {@link JsonObject} 的静态方法，便于嵌套组合。
 */
public final class ItemStateBuilder {

    private ItemStateBuilder() {
    }

    // ==================== Node constructors ====================

    /**
     * Leaf node pointing at a concrete model.
     *
     * @param model model path (e.g. {@code "minecraft:item/apple"})
     * @return {@code {"type":"minecraft:model","model":<path>}}
     */
    public static JsonObject model(String model) {
        JsonObject node = new JsonObject();
        node.addProperty("type", "minecraft:model");
        node.addProperty("model", model);
        return node;
    }

    /**
     * Leaf node with tints (e.g. {@code [{"type":"minecraft:constant","value":16711680}]}).
     *
     * @param model model path
     * @param tints serialized tints array, or {@code null} to omit
     * @return {@code {"type":"minecraft:model","model":<path>,"tints":[...]}}
     */
    public static JsonObject model(String model, JsonElement tints) {
        JsonObject node = model(model);
        if (tints != null && !tints.isJsonNull()) {
            node.add("tints", tints);
        }
        return node;
    }

    /**
     * Boolean branch: evaluates {@code property} as true/false.
     *
     * @param property property name (e.g. {@code "minecraft:using_item"})
     * @param onTrue   subtree when the property is true
     * @param onFalse  subtree when the property is false or absent
     * @return {@code {"type":"minecraft:condition",...}}
     */
    public static JsonObject condition(String property, JsonObject onTrue, JsonObject onFalse) {
        JsonObject node = new JsonObject();
        node.addProperty("type", "minecraft:condition");
        node.addProperty("property", property);
        if (onTrue != null) {
            node.add("on_true", onTrue);
        }
        if (onFalse != null) {
            node.add("on_false", onFalse);
        }
        return node;
    }

    /**
     * Enum select: matches {@code property} against each case's {@code when}
     * set, falling back to {@code fallback} when nothing matches.
     *
     * @param property property name (e.g. {@code "catframe:meta"})
     * @param fallback subtree for unmatched values (may be {@code null})
     * @param cases    ordered case list
     * @return {@code {"type":"minecraft:select",...}}
     */
    public static JsonObject select(String property, JsonObject fallback, Case... cases) {
        JsonObject node = new JsonObject();
        node.addProperty("type", "minecraft:select");
        node.addProperty("property", property);
        if (fallback != null) {
            node.add("fallback", fallback);
        }
        JsonArray arr = new JsonArray();
        for (Case c : cases) {
            arr.add(c.build());
        }
        node.add("cases", arr);
        return node;
    }

    /**
     * Numeric dispatch: picks the last entry whose {@code threshold} is
     * {@code <= property * scale}; falls back when nothing matches.
     *
     * @param property property name (e.g. {@code "minecraft:use_duration"})
     * @param scale    value multiplier
     * @param fallback subtree for unmatched values (may be {@code null})
     * @param entries  threshold entries, ascending
     * @return {@code {"type":"minecraft:range_dispatch",...}}
     */
    public static JsonObject rangeDispatch(String property, float scale,
                                           JsonObject fallback, Entry... entries) {
        JsonObject node = new JsonObject();
        node.addProperty("type", "minecraft:range_dispatch");
        node.addProperty("property", property);
        node.addProperty("scale", scale);
        if (fallback != null) {
            node.add("fallback", fallback);
        }
        JsonArray arr = new JsonArray();
        for (Entry e : entries) {
            arr.add(e.build());
        }
        node.add("entries", arr);
        return node;
    }

    /**
     * Layered composite: renders all child nodes in order (used for item
     * overlays).
     *
     * @param models child nodes
     * @return {@code {"type":"minecraft:composite","models":[...]}}
     */
    public static JsonObject composite(JsonObject... models) {
        JsonObject node = new JsonObject();
        node.addProperty("type", "minecraft:composite");
        JsonArray arr = new JsonArray();
        for (JsonObject m : models) {
            arr.add(m);
        }
        node.add("models", arr);
        return node;
    }

    /**
     * Empty node: renders nothing.
     *
     * @return {@code {"type":"minecraft:empty"}}
     */
    public static JsonObject empty() {
        JsonObject node = new JsonObject();
        node.addProperty("type", "minecraft:empty");
        return node;
    }

    /**
     * Hand/inventory split used by Blockbench plushies: hands pick
     * {@code handModel}, every other display context (gui/ground/fixed...)
     * picks {@code inventoryModel}.
     *
     * @param handModel      model used in first/third-person hand contexts
     * @param inventoryModel model used everywhere else (fallback)
     * @return a {@code minecraft:display_context} select tree
     */
    public static JsonObject displayContextSelect(String handModel, String inventoryModel) {
        return select("minecraft:display_context", model(inventoryModel),
                Case.when("ITEM_HAND_FIRST_PERSON", "ITEM_HAND_THIRD_PERSON")
                        .then(model(handModel)));
    }

    // ==================== Case / Entry ====================

    /**
     * One {@code select} case: a {@code when} value set plus the subtree.
     * A single {@code when} value serializes as a plain string (the 1.7.10
     * hand-written style), multiple values as an array — both accepted by the
     * loader.
     */
    public static final class Case {

        private final List<String> when = new ArrayList<>();
        private JsonObject model;

        /**
         * Start a case with the values that trigger it.
         *
         * @param values matching property values
         * @return case builder
         */
        public static Case when(String... values) {
            Case c = new Case();
            c.when.addAll(Arrays.asList(values));
            return c;
        }

        /**
         * Bind the subtree rendered when this case matches.
         *
         * @param node subtree (any node type)
         * @return this case
         */
        public Case then(JsonObject node) {
            this.model = node;
            return this;
        }

        private JsonObject build() {
            JsonObject obj = new JsonObject();
            if (when.size() == 1) {
                obj.addProperty("when", when.get(0));
            } else {
                JsonArray arr = new JsonArray();
                for (String w : when) {
                    arr.add(new JsonPrimitive(w));
                }
                obj.add("when", arr);
            }
            if (model != null) {
                obj.add("model", model);
            }
            return obj;
        }
    }

    /**
     * One {@code range_dispatch} entry: a threshold and the subtree rendered
     * once the scaled value reaches it.
     */
    public static final class Entry {

        private final float threshold;
        private final JsonObject model;

        private Entry(float threshold, JsonObject model) {
            this.threshold = threshold;
            this.model = model;
        }

        /**
         * @param threshold minimum scaled value for this entry
         * @param model     subtree rendered at or above the threshold
         * @return entry
         */
        public static Entry threshold(float threshold, JsonObject model) {
            return new Entry(threshold, model);
        }

        private JsonObject build() {
            JsonObject obj = new JsonObject();
            obj.addProperty("threshold", threshold);
            if (model != null) {
                obj.add("model", model);
            }
            return obj;
        }
    }

    // ==================== Root wrapper ====================

    /**
     * Root file wrapper: {@code {"model": <node>, ...root flags}}.
     * <p>
     * Root-level flags ({@code hand_animation_on_swap},
     * {@code oversized_in_gui}, {@code swap_animation_scale}) are optional;
     * omitted keys keep the loader defaults.
     */
    public static final class Root {

        private final JsonObject root = new JsonObject();

        private Root(JsonObject model) {
            root.add("model", model);
        }

        /**
         * @param value whether the item animates in hand on swap (default true)
         * @return this wrapper
         */
        public Root handAnimationOnSwap(boolean value) {
            root.addProperty("hand_animation_on_swap", value);
            return this;
        }

        /**
         * @param value whether the item renders oversized in gui (default false)
         * @return this wrapper
         */
        public Root oversizedInGui(boolean value) {
            root.addProperty("oversized_in_gui", value);
            return this;
        }

        /**
         * @param value hand swap animation scale (default 1.0)
         * @return this wrapper
         */
        public Root swapAnimationScale(float value) {
            root.addProperty("swap_animation_scale", value);
            return this;
        }

        /**
         * Add any other root-level field verbatim (escape hatch for fields
         * this builder does not model yet).
         *
         * @param key   field name
         * @param value JSON value
         * @return this wrapper
         */
        public Root raw(String key, JsonElement value) {
            root.add(key, value);
            return this;
        }

        /**
         * @return the complete file tree
         */
        public JsonObject build() {
            return root;
        }
    }

    /**
     * Wrap a root node into a file tree.
     *
     * @param model root decision-tree node
     * @return root wrapper
     */
    public static Root of(JsonObject model) {
        return new Root(model);
    }
}
