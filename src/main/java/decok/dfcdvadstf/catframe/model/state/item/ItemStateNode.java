package decok.dfcdvadstf.catframe.model.state.item;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.*;

/**
 * ItemState 决策树节点。
 * <p>
 * 高版本 MC (1.21.2+) 的 items/ JSON 本质是一棵模型决策树：
 * 运行时每帧根据 ItemStack 的实时属性（使用状态、damage、display context 等）
 * 自顶向下求值，最终选定一个具体模型来渲染。
 * <p>
 * 四种节点类型（可任意嵌套组合）：
 * <ul>
 *   <li>{@link ModelLeaf} — 叶子节点，指向具体模型路径</li>
 *   <li>{@link ConditionNode} — 布尔分支，根据 property 的 true/false 走 on_true/on_false 子树</li>
 *   <li>{@link RangeDispatchNode} — 数值阈值分派，根据 property 值落入哪个 threshold 区间选择子树</li>
 *   <li>{@link ExactMatchNode} — 精确值匹配，根据 property 值精确选择子树</li>
 * </ul>
 *
 * <p>对齐高版本 JSON 格式：
 * <pre>{@code
 * {
 *   "type": "minecraft:condition",
 *   "property": "minecraft:using_item",
 *   "on_true": { "type": "minecraft:model", "model": "item/bow_pulling_0" },
 *   "on_false": { "type": "minecraft:model", "model": "item/bow" }
 * }
 * }</pre>
 */
public abstract class ItemStateNode {

    /**
     * 递归求值决策树，返回最终选中的模型路径。
     *
     * @param properties 运行时属性集（property name → value）
     * @return 选中的模型路径，null 表示无匹配
     */
    public abstract String evaluate(Map<String, Comparable<?>> properties);

    /**
     * 收集决策树中所有引用的模型路径（用于纹理收集）。
     *
     * @param out 收集目标集合
     */
    public abstract void collectModelPaths(Set<String> out);

    // ==================== 叶子节点 ====================

    /**
     * 叶子节点：指向具体模型路径。
     * <p>JSON: {@code {"type": "minecraft:model", "model": "item/apple"}}
     */
    public static class ModelLeaf extends ItemStateNode {
        public final String model;

        public ModelLeaf(String model) {
            this.model = model;
        }

        @Override
        public String evaluate(Map<String, Comparable<?>> properties) {
            return model;
        }

        @Override
        public void collectModelPaths(Set<String> out) {
            if (model != null) out.add(model);
        }
    }

    // ==================== 条件节点 ====================

    /**
     * 布尔分支节点：根据 property 的布尔值走不同子树。
     * <p>JSON:
     * <pre>{@code
     * {
     *   "type": "minecraft:condition",
     *   "property": "minecraft:using_item",
     *   "on_true": { ... },
     *   "on_false": { ... }
     * }
     * }</pre>
     */
    public static class ConditionNode extends ItemStateNode {
        public final String property;
        public final ItemStateNode onTrue;
        public final ItemStateNode onFalse;

        public ConditionNode(String property, ItemStateNode onTrue, ItemStateNode onFalse) {
            this.property = property;
            this.onTrue = onTrue;
            this.onFalse = onFalse;
        }

        @Override
        public String evaluate(Map<String, Comparable<?>> properties) {
            Comparable<?> value = properties.get(property);
            if (value instanceof Boolean) {
                return ((Boolean) value)
                        ? (onTrue != null ? onTrue.evaluate(properties) : null)
                        : (onFalse != null ? onFalse.evaluate(properties) : null);
            }
            // 属性不存在或非布尔 → 走 on_false
            return onFalse != null ? onFalse.evaluate(properties) : null;
        }

        @Override
        public void collectModelPaths(Set<String> out) {
            if (onTrue != null) onTrue.collectModelPaths(out);
            if (onFalse != null) onFalse.collectModelPaths(out);
        }
    }

    // ==================== 数值阈值分派节点 ====================

    /**
     * 数值阈值分派节点：根据 property 的数值（乘以 scale 后）落入哪个 threshold 区间选择子树。
     * <p>
     * entries 按 threshold 升序排列。求值时找到最后一个 threshold &lt;= scaledValue 的条目。
     * 如果没有条目匹配，使用 fallback。
     * <p>JSON:
     * <pre>{@code
     * {
     *   "type": "minecraft:range_dispatch",
     *   "property": "minecraft:use_duration",
     *   "scale": 0.05,
     *   "fallback": { ... },
     *   "entries": [
     *     { "threshold": 0.65, "model": { ... } },
     *     { "threshold": 0.9,  "model": { ... } }
     *   ]
     * }
     * }</pre>
     */
    public static class RangeDispatchNode extends ItemStateNode {
        public final String property;
        public final float scale;
        public final ItemStateNode fallback;
        public final List<ThresholdEntry> entries;

        public RangeDispatchNode(String property, float scale, ItemStateNode fallback,
                                  List<ThresholdEntry> entries) {
            this.property = property;
            this.scale = scale;
            this.fallback = fallback;
            this.entries = entries;
        }

        @Override
        public String evaluate(Map<String, Comparable<?>> properties) {
            Comparable<?> raw = properties.get(property);
            float value;
            if (raw instanceof Number) {
                value = ((Number) raw).floatValue() * scale;
            } else {
                return fallback != null ? fallback.evaluate(properties) : null;
            }

            // 找到最后一个 threshold <= value 的条目
            ItemStateNode matched = null;
            for (ThresholdEntry entry : entries) {
                if (value >= entry.threshold) {
                    matched = entry.node;
                } else {
                    break; // entries 按 threshold 升序，后续都不会匹配
                }
            }

            if (matched != null) return matched.evaluate(properties);
            return fallback != null ? fallback.evaluate(properties) : null;
        }

        @Override
        public void collectModelPaths(Set<String> out) {
            if (fallback != null) fallback.collectModelPaths(out);
            for (ThresholdEntry entry : entries) {
                if (entry.node != null) entry.node.collectModelPaths(out);
            }
        }
    }

    /**
     * range_dispatch 的阈值条目。
     */
    public static class ThresholdEntry {
        public final float threshold;
        public final ItemStateNode node;

        public ThresholdEntry(float threshold, ItemStateNode node) {
            this.threshold = threshold;
            this.node = node;
        }
    }

    // ==================== 精确值匹配节点 ====================

    /**
     * 精确值匹配节点：根据 property 值的字符串表示精确选择子树。
     * <p>
     * 如果当前属性值在 cases 中有对应条目，使用该条目的子树；否则使用 fallback。
     * <p>JSON:
     * <pre>{@code
     * {
     *   "type": "minecraft:exact_match",
     *   "property": "minecraft:damage",
     *   "fallback": { ... },
     *   "cases": {
     *     "0": { "type": "minecraft:model", "model": "item/dye_black" },
     *     "1": { "type": "minecraft:model", "model": "item/dye_red" }
     *   }
     * }
     * }</pre>
     */
    public static class ExactMatchNode extends ItemStateNode {
        public final String property;
        public final Map<String, ItemStateNode> cases;
        public final ItemStateNode fallback;

        public ExactMatchNode(String property, Map<String, ItemStateNode> cases,
                               ItemStateNode fallback) {
            this.property = property;
            this.cases = cases;
            this.fallback = fallback;
        }

        @Override
        public String evaluate(Map<String, Comparable<?>> properties) {
            Comparable<?> value = properties.get(property);
            if (value == null) {
                return fallback != null ? fallback.evaluate(properties) : null;
            }
            String key = value.toString();
            ItemStateNode matched = cases.get(key);
            if (matched != null) return matched.evaluate(properties);
            return fallback != null ? fallback.evaluate(properties) : null;
        }

        @Override
        public void collectModelPaths(Set<String> out) {
            if (fallback != null) fallback.collectModelPaths(out);
            for (ItemStateNode node : cases.values()) {
                if (node != null) node.collectModelPaths(out);
            }
        }
    }

    // ==================== Gson 反序列化 ====================

    /**
     * 创建能解析 ItemStateNode 决策树 JSON 的 Gson。
     */
    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(ItemStateNode.class, new ItemStateNodeDeserializer())
                .create();
    }

    /**
     * 解析单个 ItemState JSON 文件。
     * <p>
     * 文件格式：{@code {"model": <ItemStateNode>}}
     * 返回决策树的根节点。
     *
     * @param json JSON 对象
     * @return 根节点，或 null 如果解析失败
     */
    public static ItemStateNode parseRoot(JsonObject json) {
        if (json == null) return null;
        JsonElement modelElement = json.get("model");
        if (modelElement == null) return null;
        Gson gson = createGson();
        return gson.fromJson(modelElement, ItemStateNode.class);
    }

    /**
     * 自定义反序列化器：根据 "type" 字段分发到不同的节点类型。
     */
    private static class ItemStateNodeDeserializer implements JsonDeserializer<ItemStateNode> {
        @Override
        public ItemStateNode deserialize(JsonElement json, Type typeOfT,
                                          JsonDeserializationContext context) throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("ItemStateNode must be a JSON object");
            }
            JsonObject obj = json.getAsJsonObject();
            String type = obj.has("type") ? obj.get("type").getAsString() : null;

            if (type == null) {
                throw new JsonParseException("ItemStateNode missing 'type' field");
            }

            switch (type) {
                case "minecraft:model":
                    return deserializeModel(obj);
                case "minecraft:condition":
                    return deserializeCondition(obj, context);
                case "minecraft:range_dispatch":
                    return deserializeRangeDispatch(obj, context);
                case "minecraft:exact_match":
                    return deserializeExactMatch(obj, context);
                default:
                    throw new JsonParseException("Unknown ItemStateNode type: " + type);
            }
        }

        private ModelLeaf deserializeModel(JsonObject obj) {
            String model = obj.has("model") ? obj.get("model").getAsString() : null;
            return new ModelLeaf(model);
        }

        private ConditionNode deserializeCondition(JsonObject obj, JsonDeserializationContext ctx) {
            String property = obj.has("property") ? obj.get("property").getAsString() : null;
            ItemStateNode onTrue = obj.has("on_true")
                    ? ctx.deserialize(obj.get("on_true"), ItemStateNode.class) : null;
            ItemStateNode onFalse = obj.has("on_false")
                    ? ctx.deserialize(obj.get("on_false"), ItemStateNode.class) : null;
            return new ConditionNode(property, onTrue, onFalse);
        }

        private RangeDispatchNode deserializeRangeDispatch(JsonObject obj, JsonDeserializationContext ctx) {
            String property = obj.has("property") ? obj.get("property").getAsString() : null;
            float scale = obj.has("scale") ? obj.get("scale").getAsFloat() : 1.0f;
            ItemStateNode fallback = obj.has("fallback")
                    ? ctx.deserialize(obj.get("fallback"), ItemStateNode.class) : null;

            List<ThresholdEntry> entries = new ArrayList<>();
            if (obj.has("entries") && obj.get("entries").isJsonArray()) {
                for (JsonElement elem : obj.getAsJsonArray("entries")) {
                    JsonObject entryObj = elem.getAsJsonObject();
                    float threshold = entryObj.get("threshold").getAsFloat();
                    ItemStateNode node = ctx.deserialize(entryObj.get("model"), ItemStateNode.class);
                    entries.add(new ThresholdEntry(threshold, node));
                }
            }
            // 确保 entries 按 threshold 升序排列
            entries.sort(Comparator.comparingDouble(e -> e.threshold));

            return new RangeDispatchNode(property, scale, fallback, entries);
        }

        private ExactMatchNode deserializeExactMatch(JsonObject obj, JsonDeserializationContext ctx) {
            String property = obj.has("property") ? obj.get("property").getAsString() : null;
            ItemStateNode fallback = obj.has("fallback")
                    ? ctx.deserialize(obj.get("fallback"), ItemStateNode.class) : null;

            Map<String, ItemStateNode> cases = new LinkedHashMap<>();
            if (obj.has("cases") && obj.get("cases").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("cases").entrySet()) {
                    ItemStateNode node = ctx.deserialize(entry.getValue(), ItemStateNode.class);
                    cases.put(entry.getKey(), node);
                }
            }

            return new ExactMatchNode(property, cases, fallback);
        }
    }
}
