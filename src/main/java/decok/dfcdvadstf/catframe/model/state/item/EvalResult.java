package decok.dfcdvadstf.catframe.model.state.item;

import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ItemState decision tree evaluation result.
 * <p>
 * Encapsulates three result forms:
 * <ul>
 *   <li>{@link #single(String)} — single model (returned by {@link ItemStateNode.ModelLeaf})</li>
 *   <li>{@link #composite(List)} — layered multiple models (returned by {@link ItemStateNode.CompositeNode})</li>
 *   <li>{@link #empty()} — empty result ({@link ItemStateNode.EmptyNode} or fallback exhausted)</li>
 * </ul>
 * <p>
 * Internally it is always represented as an ordered {@link Layer} list: single = 1 layer,
 * composite = multiple layers, empty = empty list. Each layer carries either a static model
 * path ({@link Layer#modelPath}) or a dynamic content marker ({@link Layer#dynamicStack},
 * aligned with the modern {@code appendItemLayers}: a "render this item stack's own model"
 * layer appended by a special node reading NBT during evaluation).
 * <p>
 * The existing {@link #getModels()} keeps its
 * original meaning (static model paths); dynamic layers surface through {@link #getLayers()}.
 */
public final class EvalResult {

    /**
     * A single render layer: either a static model path or a dynamic content stack.
     */
    public static final class Layer {
        @Nullable
        public final String modelPath;
        @Nullable
        public final ItemStack dynamicStack;

        private Layer(@Nullable String modelPath, @Nullable ItemStack dynamicStack) {
            this.modelPath = modelPath;
            this.dynamicStack = dynamicStack;
        }

        /** A static model layer. */
        public static Layer ofModel(String path) {
            return new Layer(path, null);
        }

        /** A dynamic layer rendering the stack's own model. */
        public static Layer ofDynamic(ItemStack stack) {
            return new Layer(null, stack);
        }

        /** Whether this layer carries a dynamic stack rather than a fixed model. */
        public boolean isDynamic() {
            return dynamicStack != null;
        }
    }

    /** Empty result singleton */
    private static final EvalResult EMPTY = new EvalResult(Collections.<Layer>emptyList());

    /** Ordered layer list (single=1, composite=many, empty=0) */
    private final List<Layer> layers;

    /** Cache of the static model path list (lazily derived from layers, preserving the old getModels() semantics) */
    private List<String> modelsCache;

    private EvalResult(List<Layer> layers) {
        this.layers = layers;
    }

    // ==================== Factory methods ====================

    /**
     * Single-model result.
     *
     * @param modelPath model path; null is equivalent to empty()
     * @return evaluation result
     */
    public static EvalResult single(String modelPath) {
        if (modelPath == null) return EMPTY;
        return new EvalResult(Collections.singletonList(Layer.ofModel(modelPath)));
    }

    /**
     * Layered multiple-models result (composite node, all static models).
     *
     * @param modelPaths model path list ordered by render order
     * @return evaluation result
     */
    public static EvalResult composite(List<String> modelPaths) {
        if (modelPaths == null || modelPaths.isEmpty()) return EMPTY;
        List<Layer> ls = new ArrayList<>(modelPaths.size());
        for (String p : modelPaths) ls.add(Layer.ofModel(p));
        return new EvalResult(ls);
    }

    /**
     * Multi-layer result (mixing static models and dynamic content); used by composite
     * nodes to merge child layers in order.
     *
     * @param layers layer list ordered by render order
     * @return evaluation result; an empty list is equivalent to empty()
     */
    public static EvalResult compositeLayers(List<Layer> layers) {
        if (layers == null || layers.isEmpty()) return EMPTY;
        return new EvalResult(new ArrayList<>(layers));
    }

    /**
     * Single dynamic-content layer result (a special node appends "render this stack's own model").
     *
     * @param stack content item stack; null is equivalent to empty()
     * @return evaluation result
     */
    public static EvalResult dynamic(ItemStack stack) {
        if (stack == null) return EMPTY;
        return new EvalResult(Collections.singletonList(Layer.ofDynamic(stack)));
    }

    /**
     * Empty result: renders no model.
     *
     * @return empty result singleton
     */
    public static EvalResult empty() {
        return EMPTY;
    }

    // ==================== Query methods ====================

    /**
     * @return true if there is no layer to render
     */
    public boolean isEmpty() {
        return layers.isEmpty();
    }

    /**
     * @return true for a single-layer result
     */
    public boolean isSingle() {
        return layers.size() == 1;
    }

    /**
     * @return true for a layered multiple-layer result
     */
    public boolean isComposite() {
        return layers.size() > 1;
    }

    /**
     * Gets the ordered layer list (unmodifiable), including both static and dynamic layers.
     *
     * @return layer list
     */
    public List<Layer> getLayers() {
        return layers;
    }

    /**
     * Gets the static model path list (unmodifiable), ignoring dynamic content layers.
     * <p>
     * single returns a 1-element list, composite returns a multi-element list, empty returns an
     * empty list. The semantics are unchanged from before the extension, so existing call sites
     * that only care about static models (tint resolution, GUI overflow part collection) need no
     * changes.
     *
     * @return static model path list (unmodifiable)
     */
    public List<String> getModels() {
        if (modelsCache == null) {
            List<String> out = new ArrayList<>(layers.size());
            for (Layer l : layers) {
                if (l.modelPath != null) out.add(l.modelPath);
            }
            modelsCache = Collections.unmodifiableList(out);
        }
        return modelsCache;
    }

    /**
     * Gets the first static model path (backward compatibility for single-model scenarios).
     *
     * @return the first static model path, or null when there is no static model
     */
    @Nullable
    public String getFirstModel() {
        List<String> m = getModels();
        return m.isEmpty() ? null : m.get(0);
    }

    @Override
    public String toString() {
        if (isEmpty()) return "EvalResult.empty()";
        return "EvalResult(" + layers.size() + " layers, static=" + getModels() + ")";
    }
}
