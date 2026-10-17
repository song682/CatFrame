package decok.dfcdvadstf.catframe.model.state.item;

import com.google.gson.*;
import decok.dfcdvadstf.catframe.model.state.item.tint.*;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import java.lang.reflect.Type;
import java.util.*;

/**
 * ItemState decision-tree node.
 * <p>
 * The items/ JSON of modern Minecraft (1.21.2+) is essentially a model decision tree:
 * every frame at runtime it is evaluated top-down against the live attributes of the
 * ItemStack (use state, damage, display context, etc.), ultimately selecting one concrete
 * model to render.
 * <p>
 * Six node types (nestable and composable in any combination):
 * <ul>
 *   <li>{@link ModelLeaf} — leaf node pointing at a concrete model path</li>
 *   <li>{@link ConditionNode} — boolean branch; follows the on_true/on_false subtree according to the property's true/false</li>
 *   <li>{@link RangeDispatchNode} — numeric threshold dispatch; selects a subtree by which threshold range the property value falls into</li>
 *   <li>{@link SelectNode} — enumeration select (wiki-spec select), cases carry a when array</li>
 *   <li>{@link CompositeNode} — composite layering; renders multiple subtrees in order</li>
 *   <li>{@link EmptyNode} — empty node; renders no model at all</li>
 * </ul>
 *
 * <p>Aligned with the modern JSON format:
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
     * Recursively evaluates the decision tree and returns the result.
     * <p>
     * The result may be a single model ({@link EvalResult#single}), layered multiple models
     * ({@link EvalResult#composite}), or an empty result ({@link EvalResult#empty}).
     *
     * @param properties runtime property set (property name → value)
     * @return evaluation result, never null
     */
    public abstract EvalResult evaluate(Map<String, Comparable<?>> properties);

    /**
     * Context-carrying overload threaded down the tree so special nodes can read the live
     * stack at evaluation time. The base default ignores the context and delegates to
     * {@link #evaluate(Map)}; leaves inherit it, containers override to propagate it.
     *
     * @param properties runtime property set
     * @param ctx        evaluation context; may be {@code null} (non-rendering evaluation paths)
     * @return evaluation result, never null
     */
    public EvalResult evaluate(Map<String, Comparable<?>> properties, @Nullable EvalContext ctx) {
        return evaluate(properties);
    }

    /**
     * Collects all model paths referenced in the decision tree (used for texture collection).
     *
     * @param out target collection to collect into
     */
    public abstract void collectModelPaths(Set<String> out);

    // ==================== Leaf node ====================

    /**
     * Leaf node: points to a concrete model path, may carry tints.
     * <p>JSON: {@code {"type": "minecraft:model", "model": "item/apple", "tints": [...]}}
     * <p>Optional {@code transformation} tag (identity transform by default): the item model
     * render transform, applied per-vertex after the display transform; parsing rules see
     * {@link ItemTransformation}.
     */
    public static class ModelLeaf extends ItemStateNode {
        public final String model;
        public final List<ItemTint> tints;
        /** Optional item model render transform (applied after display), null when there is no transform / optional post-display transform */
        @Nullable
        public final Matrix4d transformation;

        public ModelLeaf(String model) {
            this(model, Collections.<ItemTint>emptyList());
        }

        public ModelLeaf(String model, List<ItemTint> tints) {
            this(model, tints, null);
        }

        public ModelLeaf(String model, List<ItemTint> tints, @Nullable Matrix4d transformation) {
            this.model = model;
            this.tints = tints != null ? tints : Collections.<ItemTint>emptyList();
            this.transformation = transformation;
        }

        @Override
        public EvalResult evaluate(Map<String, Comparable<?>> properties) {
            return EvalResult.single(model);
        }

        @Override
        public void collectModelPaths(Set<String> out) {
            if (model != null) out.add(model);
        }
    }

    // ==================== Condition node ====================

    /**
     * Boolean branch node: follows different subtrees based on the property's boolean value.
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
        public EvalResult evaluate(Map<String, Comparable<?>> properties) {
            return evaluate(properties, null);
        }

        @Override
        public EvalResult evaluate(Map<String, Comparable<?>> properties, @Nullable EvalContext ctx) {
            Comparable<?> value = properties.get(property);
            if (value instanceof Boolean) {
                return ((Boolean) value)
                        ? (onTrue != null ? onTrue.evaluate(properties, ctx) : EvalResult.empty())
                        : (onFalse != null ? onFalse.evaluate(properties, ctx) : EvalResult.empty());
            }
            // Property missing or non-boolean → take on_false
            return onFalse != null ? onFalse.evaluate(properties, ctx) : EvalResult.empty();
        }

        @Override
        public void collectModelPaths(Set<String> out) {
            if (onTrue != null) onTrue.collectModelPaths(out);
            if (onFalse != null) onFalse.collectModelPaths(out);
        }
    }

    // ==================== Numeric threshold dispatch node ====================

    /**
     * Numeric threshold dispatch node: selects a subtree by which threshold range the
     * property's numeric value (after multiplication by scale) falls into.
     * <p>
     * entries are sorted ascending by threshold. During evaluation, the last entry whose
     * threshold &lt;= scaledValue is found. If no entry matches, fallback is used.
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
        public EvalResult evaluate(Map<String, Comparable<?>> properties) {
            return evaluate(properties, null);
        }

        @Override
        public EvalResult evaluate(Map<String, Comparable<?>> properties, @Nullable EvalContext ctx) {
            Comparable<?> raw = properties.get(property);
            float value;
            if (raw instanceof Number) {
                value = ((Number) raw).floatValue() * scale;
            } else {
                return fallback != null ? fallback.evaluate(properties, ctx) : EvalResult.empty();
            }

            // Find the last entry with threshold <= value
            ItemStateNode matched = null;
            for (ThresholdEntry entry : entries) {
                if (value >= entry.threshold) {
                    matched = entry.node;
                } else {
                    break; // entries are ascending by threshold, so none of the rest will match
                }
            }

            if (matched != null) return matched.evaluate(properties, ctx);
            return fallback != null ? fallback.evaluate(properties, ctx) : EvalResult.empty();
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
     * Threshold entry of a range_dispatch.
     */
    public static class ThresholdEntry {
        public final float threshold;
        public final ItemStateNode node;

        public ThresholdEntry(float threshold, ItemStateNode node) {
            this.threshold = threshold;
            this.node = node;
        }
    }

    // ==================== Empty node ====================

    /**
     * Empty node: renders no model.
     * <p>JSON: {@code {"type": "minecraft:empty"}}
     */
    public static class EmptyNode extends ItemStateNode {
        @Override
        public EvalResult evaluate(Map<String, Comparable<?>> properties) {
            return EvalResult.empty();
        }

        @Override
        public void collectModelPaths(Set<String> out) {
            // Empty node references no model
        }
    }

    // ==================== Composite layering node ====================

    /**
     * Composite layering node: renders multiple subtrees in order.
     * <p>
     * Each child can be any node type (including a nested composite).
     * During evaluation the model paths of all subtrees are collected recursively and merged
     * into a single {@link EvalResult#composite} result.
     * <p>JSON:
     * <pre>{@code
     * {
     *   "type": "minecraft:composite",
     *   "models": [
     *     { "type": "minecraft:model", "model": "item/base" },
     *     { "type": "minecraft:model", "model": "item/overlay" }
     *   ]
     * }
     * }</pre>
     */
    public static class CompositeNode extends ItemStateNode {
        public final List<ItemStateNode> models;

        public CompositeNode(List<ItemStateNode> models) {
            this.models = models != null ? models : Collections.<ItemStateNode>emptyList();
        }

        @Override
        public EvalResult evaluate(Map<String, Comparable<?>> properties) {
            return evaluate(properties, null);
        }

        @Override
        public EvalResult evaluate(Map<String, Comparable<?>> properties, @Nullable EvalContext ctx) {
            // Merge child layers (static models + dynamic content); composite order is the render-order contract.
            List<EvalResult.Layer> allLayers = new ArrayList<>();
            for (ItemStateNode child : models) {
                EvalResult childResult = child.evaluate(properties, ctx);
                allLayers.addAll(childResult.getLayers());
            }
            return allLayers.isEmpty() ? EvalResult.empty() : EvalResult.compositeLayers(allLayers);
        }

        @Override
        public void collectModelPaths(Set<String> out) {
            for (ItemStateNode child : models) {
                if (child != null) child.collectModelPaths(out);
            }
        }
    }

    // ==================== Enumeration select node ====================

    /**
     * Enumeration select node: selects a subtree by matching the property value against the
     * when sets of the cases.
     * <p>
     * cases is expressed as a list, and each case's {@code when} can be a string array
     * (matching multiple values). fallback is used when nothing matches.
     * <p>JSON:
     * <pre>{@code
     * {
     *   "type": "minecraft:select",
     *   "property": "minecraft:display_context",
     *   "fallback": { ... },
     *   "cases": [
     *     { "when": ["gui", "ground"], "model": { "type": "minecraft:model", "model": "item/flat" } },
     *     { "when": "hand", "model": { "type": "minecraft:model", "model": "item/3d" } }
     *   ]
     * }
     * }</pre>
     */
    public static class SelectNode extends ItemStateNode {
        public final String property;
        public final List<SelectCase> cases;
        public final ItemStateNode fallback;

        public SelectNode(String property, List<SelectCase> cases, ItemStateNode fallback) {
            this.property = property;
            this.cases = cases != null ? cases : Collections.<SelectCase>emptyList();
            this.fallback = fallback;
        }

        @Override
        public EvalResult evaluate(Map<String, Comparable<?>> properties) {
            return evaluate(properties, null);
        }

        @Override
        public EvalResult evaluate(Map<String, Comparable<?>> properties, @Nullable EvalContext ctx) {
            Comparable<?> value = properties.get(property);
            if (value == null) {
                return fallback != null ? fallback.evaluate(properties, ctx) : EvalResult.empty();
            }
            String key = value.toString();
            for (SelectCase sc : cases) {
                if (sc.when.contains(key)) {
                    return sc.node.evaluate(properties, ctx);
                }
            }
            return fallback != null ? fallback.evaluate(properties, ctx) : EvalResult.empty();
        }

        @Override
        public void collectModelPaths(Set<String> out) {
            if (fallback != null) fallback.collectModelPaths(out);
            for (SelectCase sc : cases) {
                if (sc.node != null) sc.node.collectModelPaths(out);
            }
        }
    }

    /**
     * A single case entry of a select node.
     */
    public static class SelectCase {
        public final Set<String> when;
        public final ItemStateNode node;

        public SelectCase(Set<String> when, ItemStateNode node) {
            this.when = when;
            this.node = node;
        }
    }

    // ==================== Special node (dynamic layers) ====================

    /**
     * Special node: reads the current {@link ItemStack} (NBT etc.) at evaluation time to
     * dynamically append model layers.
     * <p>
     * Aligned with the modern {@code SpecialRenderer#appendItemLayers}—a custom node type
     * in the decision tree that can be registered by third parties, producing extra layers
     * at evaluation time based on the context (e.g. the bundle's
     * {@code dmod:bundle_selected_item} takes the selected item out of the bundle and
     * appends its own model layer). The returned layer may be {@link EvalResult#dynamic}
     * (recursively rendering the content item's own model) or
     * {@link EvalResult#compositeLayers} (mixing static/dynamic).
     * <p>
     * Registration: {@link #registerNodeType(String, NodeTypeDeserializer)} binds a
     * namespaced type (e.g. {@code "dmod:bundle_selected_item"}) to a factory that returns
     * a concrete {@code SpecialNode} subclass; on the deserialization side it is materialized
     * through the node type registry, and built-in {@code minecraft:} types keep their
     * original behavior.
     * <p>
     * For context-free evaluation (e.g. texture collection, tint resolution),
     * {@link #evaluate(Map)} returns empty—dynamic layers are produced only in render
     * evaluation that carries an {@link EvalContext}.
     */
    public abstract static class SpecialNode extends ItemStateNode {

        /** Context-free evaluation produces no dynamic layers (dynamic content depends on the current {@link ItemStack}). */
        @Override
        public EvalResult evaluate(Map<String, Comparable<?>> properties) {
            return EvalResult.empty();
        }

        /** Subclasses read the NBT of {@code ctx.getStack()} here to append layers (aligned with {@code appendItemLayers}). */
        @Override
        public abstract EvalResult evaluate(Map<String, Comparable<?>> properties, @Nullable EvalContext ctx);

        /** Dynamic layers are determined only at evaluation time; texture collection has no static path to add. */
        @Override
        public void collectModelPaths(Set<String> out) {
            // Dynamic content path is unknown; do not collect
        }
    }

    // ==================== Node type registry ====================

    private static final Map<String, NodeTypeDeserializer> NODE_TYPE_REGISTRY = new HashMap<>();
    private static boolean nodeTypesInitialized = false;

    /**
     * Register a custom node type for items/ decision tree JSON.
     * <p>
     * Built-in types ({@code minecraft:model}, {@code minecraft:condition}, etc.) are
     * registered automatically on first call.
     *
     * @param type         the "type" string (e.g. "{@code mymod:my_node}")
     * @param deserializer factory that parses a JsonObject into an ItemStateNode
     */
    public static void registerNodeType(String type, NodeTypeDeserializer deserializer) {
        ensureDefaultNodeTypes();
        NODE_TYPE_REGISTRY.put(type, deserializer);
    }

    static synchronized void ensureDefaultNodeTypes() {
        if (nodeTypesInitialized) return;
        nodeTypesInitialized = true;
        registerNodeType("minecraft:model", (obj, ctx) -> deserializeModel(obj));
        registerNodeType("minecraft:condition", (obj, ctx) -> deserializeCondition(obj, ctx));
        registerNodeType("minecraft:range_dispatch", (obj, ctx) -> deserializeRangeDispatch(obj, ctx));
        registerNodeType("minecraft:composite", (obj, ctx) -> deserializeComposite(obj, ctx));
        registerNodeType("minecraft:select", (obj, ctx) -> deserializeSelect(obj, ctx));
        registerNodeType("minecraft:empty", (obj, ctx) -> new EmptyNode());
    }

    // ==================== Tint type registry ====================

    private static final Map<String, TintTypeDeserializer> TINT_TYPE_REGISTRY = new HashMap<>();
    private static boolean tintTypesInitialized = false;

    /**
     * Register a custom tint type for items/ decision tree JSON.
     * <p>
     * Built-in types ({@code minecraft:constant}, {@code minecraft:grass}, etc.) are
     * registered automatically on first call.
     *
     * @param type         the tint "type" string (e.g. "{@code mymod:my_tint}")
     * @param deserializer factory that parses a tint JsonObject into an ItemTint
     */
    public static void registerTintType(String type, TintTypeDeserializer deserializer) {
        ensureDefaultTintTypes();
        TINT_TYPE_REGISTRY.put(type, deserializer);
    }

    static synchronized void ensureDefaultTintTypes() {
        if (tintTypesInitialized) return;
        tintTypesInitialized = true;
        registerTintType("minecraft:constant", obj -> new ConstantTint(
                obj.has("value") ? obj.get("value").getAsInt() : 0xFFFFFF));
        registerTintType("minecraft:custom_model_data", obj -> new CustomModelDataTint(
                obj.has("index") ? obj.get("index").getAsInt() : 0));
        registerTintType("minecraft:grass", obj -> new GrassTint());
        registerTintType("minecraft:firework", obj -> new FireworkTint());
        registerTintType("minecraft:dye", obj -> new DyeTint(
                obj.has("default") ? obj.get("default").getAsInt() : DyeTint.DEFAULT_LEATHER_COLOR));
        registerTintType("minecraft:potion", obj -> new PotionTint());
        registerTintType("minecraft:map", obj -> new MapColorTint());
        registerTintType("minecraft:spawn_egg", obj -> new SpawnEggTint(
                obj.has("index") ? obj.get("index").getAsInt() : 0));
    }

    // ==================== Static deserialization helpers ====================

    private static ModelLeaf deserializeModel(JsonObject obj) {
        String model = obj.has("model") ? obj.get("model").getAsString() : null;
        List<ItemTint> tints = deserializeTints(obj);
        // Optional transformation: 16-float row-major matrix array or decomposed object
        Matrix4d transformation = ItemTransformation.parse(obj.get("transformation"));
        return new ModelLeaf(model, tints, transformation);
    }

    /**
     * Parse tints array using the tint type registry.
     * <p>JSON: {@code "tints": [{"type": "minecraft:constant", "value": 0xFF0000}, ...]}
     */
    private static List<ItemTint> deserializeTints(JsonObject obj) {
        if (!obj.has("tints") || !obj.get("tints").isJsonArray()) {
            return Collections.emptyList();
        }
        ensureDefaultTintTypes();
        List<ItemTint> tints = new ArrayList<>();
        for (JsonElement elem : obj.getAsJsonArray("tints")) {
            if (!elem.isJsonObject()) continue;
            JsonObject tintObj = elem.getAsJsonObject();
            String type = tintObj.has("type") ? tintObj.get("type").getAsString() : "";
            TintTypeDeserializer factory = TINT_TYPE_REGISTRY.get(type);
            if (factory != null) {
                tints.add(factory.deserialize(tintObj));
            }
            // Unknown tint type, skip
        }
        return tints;
    }

    private static ConditionNode deserializeCondition(JsonObject obj, JsonDeserializationContext ctx) {
        String property = obj.has("property") ? obj.get("property").getAsString() : null;
        ItemStateNode onTrue = obj.has("on_true")
                ? ctx.deserialize(obj.get("on_true"), ItemStateNode.class) : null;
        ItemStateNode onFalse = obj.has("on_false")
                ? ctx.deserialize(obj.get("on_false"), ItemStateNode.class) : null;
        return new ConditionNode(property, onTrue, onFalse);
    }

    private static RangeDispatchNode deserializeRangeDispatch(JsonObject obj, JsonDeserializationContext ctx) {
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
        // Ensure entries are sorted ascending by threshold
        entries.sort(Comparator.comparingDouble(e -> e.threshold));

        return new RangeDispatchNode(property, scale, fallback, entries);
    }

    private static CompositeNode deserializeComposite(JsonObject obj, JsonDeserializationContext ctx) {
        List<ItemStateNode> models = new ArrayList<>();
        if (obj.has("models") && obj.get("models").isJsonArray()) {
            for (JsonElement elem : obj.getAsJsonArray("models")) {
                models.add(ctx.deserialize(elem, ItemStateNode.class));
            }
        }
        return new CompositeNode(models);
    }

    private static SelectNode deserializeSelect(JsonObject obj, JsonDeserializationContext ctx) {
        String property = obj.has("property") ? obj.get("property").getAsString() : null;
        ItemStateNode fallback = obj.has("fallback")
                ? ctx.deserialize(obj.get("fallback"), ItemStateNode.class) : null;

        List<SelectCase> cases = new ArrayList<>();
        if (obj.has("cases") && obj.get("cases").isJsonArray()) {
            for (JsonElement elem : obj.getAsJsonArray("cases")) {
                JsonObject caseObj = elem.getAsJsonObject();
                Set<String> when = new LinkedHashSet<>();
                JsonElement whenElem = caseObj.get("when");
                if (whenElem != null) {
                    if (whenElem.isJsonArray()) {
                        for (JsonElement w : whenElem.getAsJsonArray()) {
                            when.add(w.getAsString());
                        }
                    } else if (whenElem.isJsonPrimitive()) {
                        when.add(whenElem.getAsString());
                    }
                }
                ItemStateNode node = ctx.deserialize(caseObj.get("model"), ItemStateNode.class);
                cases.add(new SelectCase(when, node));
            }
        }
        return new SelectNode(property, cases, fallback);
    }

    // ==================== Gson deserialization ====================

    /**
     * Creates a Gson that can parse ItemStateNode decision tree JSON.
     */
    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(ItemStateNode.class, new ItemStateNodeDeserializer())
                .create();
    }

    /**
     * Parses a single ItemState JSON file.
     * <p>
     * File format: {@code {"model": <ItemStateNode>}}
     * Returns the root node of the decision tree.
     *
     * @param json JSON object
     * @return the root node, or null if parsing fails
     */
    public static ItemStateNode parseRoot(JsonObject json) {
        if (json == null) return null;
        JsonElement modelElement = json.get("model");
        if (modelElement == null) return null;
        Gson gson = createGson();
        return gson.fromJson(modelElement, ItemStateNode.class);
    }

    /**
     * Fully parses an ItemState JSON file, including root-level fields.
     * <p>
     * Parses root-level fields such as {@code hand_animation_on_swap}, {@code oversized_in_gui},
     * and {@code swap_animation_scale}, returning an {@link ItemStateRoot}.
     *
     * @param json JSON object
     * @return root-level parse result, or null if parsing fails
     */
    public static ItemStateRoot parseRootFull(JsonObject json) {
        if (json == null) return null;
        ItemStateNode model = parseRoot(json);
        if (model == null) return null;

        boolean handAnimationOnSwap = !json.has("hand_animation_on_swap")
                || json.get("hand_animation_on_swap").getAsBoolean();
        boolean oversizedInGui = json.has("oversized_in_gui")
                && json.get("oversized_in_gui").getAsBoolean();
        float swapAnimationScale = json.has("swap_animation_scale")
                ? json.get("swap_animation_scale").getAsFloat() : 1.0f;

        return new ItemStateRoot(model, handAnimationOnSwap, oversizedInGui, swapAnimationScale);
    }

    /**
     * Custom deserializer: dispatches to different node types through the registry based on
     * the "type" field.
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

            ensureDefaultNodeTypes();
            NodeTypeDeserializer factory = NODE_TYPE_REGISTRY.get(type);
            if (factory != null) {
                return factory.deserialize(obj, context);
            }
            if ("minecraft:special".equals(type)) {
                throw new JsonParseException(
                        "minecraft:special is not yet supported (requires special renderer infrastructure)");
            }
            throw new JsonParseException("Unknown ItemStateNode type: " + type);
        }
    }
}
