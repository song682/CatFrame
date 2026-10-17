package decok.dfcdvadstf.catframe.model.state.item;

import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.item.tint.ItemTint;
import decok.dfcdvadstf.catframe.model.state.property.ItemProperties;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import java.util.List;
import java.util.Map;

/**
 * Item model driven by the ItemState decision tree.
 * <p>
 * This is the unified implementation that merges {@code LazyItemModel} (lazy baking of a
 * single model path) and {@code ItemStateItemModel} (decision tree dispatch):
 * <ul>
 *   <li>Without a decision, it degrades internally to a single {@link ItemStateNode.ModelLeaf}, equivalent to the former LazyItemModel</li>
 *   <li>With a decision, it holds the full {@link ItemStateNode} decision tree and evaluates it at runtime from ItemStack data</li>
 * </ul>
 * <p>
 * This is the sole renderable item model on the
 * pipeline side: {@link decok.dfcdvadstf.catframe.model.IItemStateProvider}
 * only delivers tree data; evaluation and quad submission
 * live here.
 *
 * <p>Render flow:
 * <ol>
 *   <li>Call {@link ItemProperties#buildProperties(ItemStack, RenderPhase)} to build the runtime property set</li>
 *   <li>Call {@link ItemStateNode#evaluate(Map)} to evaluate recursively and obtain the final model path</li>
 *   <li>Lazily fetch the selected model from {@link BakedModelCache}</li>
 *   <li>Render via {@link UniformRenderPipeline#renderItemQuads}</li>
 * </ol>
 */
public class ItemStateModel {

    private final ItemStateNode rootNode;

    /**
     * Constructs from a single model path (no decision, degrades to a ModelLeaf).
     *
     * @param modelPath model path
     */
    public ItemStateModel(String modelPath) {
        this(new ItemStateNode.ModelLeaf(modelPath));
    }

    /**
     * Constructs from a decision tree root node.
     *
     * @param rootNode decision tree root node (must not be null)
     */
    public ItemStateModel(ItemStateNode rootNode) {
        if (rootNode == null) {
            throw new IllegalArgumentException("ItemStateModel root node must not be null");
        }
        this.rootNode = rootNode;
    }

    public void render(ItemStack stack, RenderPhase phase) {
        render(stack, phase, null);
    }

    public void render(ItemStack stack, RenderPhase phase,
                       @Nullable Matrix4d preTransform) {
        // 1. Build the runtime property set
        Map<String, Comparable<?>> props = ItemProperties.buildProperties(stack, phase);

        // 2. Recursively evaluate the decision tree (carrying the stack/phase context so special nodes can read NBT and dynamically append layers) to obtain an EvalResult
        EvalResult result = rootNode.evaluate(props, new EvalContext(stack, phase));
        if (result.isEmpty()) {
            // The decision tree found no model → fall back to builtin/missing
            String cacheKey = BakedModelCache.buildKey("builtin/missing", 0, 0);
            BlockStateModelPart missing = BakedModelCache.INSTANCE.get(cacheKey);
            if (missing != null && !missing.isEmpty()) {
                UniformRenderPipeline.renderItemQuads(missing, stack, phase,
                        null, 0, 0, 0, null, preTransform, null, props);
            }
            return;
        }

        // 3. Render layer by layer in order: static model layers use parts baked for this tree, dynamic content layers recursively render the content item's own model
        //    (composite layer order is the render-order contract; the bundle's "front flap - content - back flap" relies on this ordering)
        for (EvalResult.Layer layer : result.getLayers()) {
            if (layer.isDynamic()) {
                renderDynamicLayer(layer.dynamicStack, phase, preTransform);
                continue;
            }
            String path = layer.modelPath;
            if (path == null) continue;
            String cacheKey = BakedModelCache.buildKey(path, 0, 0);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            if (part == null || part.isEmpty()) continue;

            // Tints are applied via the TintRegistry bridge + TintRenderExtension by
            // each quad's tintindex; no direct color computation is needed here.
            // preTransform is passed to the pipeline so the counter-offset matrix is applied correctly to vertices.
            // The matched ModelLeaf's optional per-model transformation is applied by the
            // pipeline after the display transform (identity when absent).
            Matrix4d transformation = findTransformationForModel(rootNode, path);
            UniformRenderPipeline.renderItemQuads(part, stack, phase,
                    null, 0, 0, 0, null, preTransform, transformation, props);
        }
    }

    /**
     * Recursively renders a dynamic content layer: delegates to the official entry
     * {@link UniformRenderPipeline#renderItemStack}, rendering with the content item's own
     * registered model.
     * <p>Aligned with the modern {@code SpecialRenderer#appendItemLayers}: content taken from
     * NBT by a special node at evaluation time (such as the bundle's selected item) must
     * recursively go through its own ItemState decision tree.
     */
    private static void renderDynamicLayer(@Nullable ItemStack content, RenderPhase phase,
                                           @Nullable Matrix4d preTransform) {
        UniformRenderPipeline.renderItemStack(content, phase, preTransform);
    }

    /**
     * Finds the ModelLeaf with the specified model path in the decision tree and extracts its
     * transformation matrix.
     * <p>
     * The traversal strategy matches {@link #findTintsForModel}: the first leaf that matches
     * the path and declares a non-null transform wins; returns {@code null} (identity) when
     * none declares one.
     */
    @Nullable
    private static Matrix4d findTransformationForModel(ItemStateNode node, String path) {
        if (node == null) return null;
        if (node instanceof ItemStateNode.ModelLeaf) {
            ItemStateNode.ModelLeaf leaf = (ItemStateNode.ModelLeaf) node;
            return path.equals(leaf.model) ? leaf.transformation : null;
        }
        if (node instanceof ItemStateNode.ConditionNode) {
            ItemStateNode.ConditionNode cn = (ItemStateNode.ConditionNode) node;
            Matrix4d r = findTransformationForModel(cn.onTrue, path);
            if (r != null) return r;
            return findTransformationForModel(cn.onFalse, path);
        }
        if (node instanceof ItemStateNode.RangeDispatchNode) {
            ItemStateNode.RangeDispatchNode rn = (ItemStateNode.RangeDispatchNode) node;
            Matrix4d r = findTransformationForModel(rn.fallback, path);
            if (r != null) return r;
            for (ItemStateNode.ThresholdEntry e : rn.entries) {
                r = findTransformationForModel(e.node, path);
                if (r != null) return r;
            }
            return null;
        }
        if (node instanceof ItemStateNode.SelectNode) {
            ItemStateNode.SelectNode sn = (ItemStateNode.SelectNode) node;
            Matrix4d r = findTransformationForModel(sn.fallback, path);
            if (r != null) return r;
            for (ItemStateNode.SelectCase sc : sn.cases) {
                r = findTransformationForModel(sc.node, path);
                if (r != null) return r;
            }
            return null;
        }
        if (node instanceof ItemStateNode.CompositeNode) {
            ItemStateNode.CompositeNode cn = (ItemStateNode.CompositeNode) node;
            for (ItemStateNode child : cn.models) {
                Matrix4d r = findTransformationForModel(child, path);
                if (r != null) return r;
            }
            return null;
        }
        return null;
    }

    /**
     * Finds the ModelLeaf with the specified model path in the decision tree and extracts its
     * tints.
     */
    private static List<ItemTint> findTintsForModel(ItemStateNode node, String path,
                                                      Map<String, Comparable<?>> props) {
        if (node instanceof ItemStateNode.ModelLeaf) {
            ItemStateNode.ModelLeaf leaf = (ItemStateNode.ModelLeaf) node;
            if (path.equals(leaf.model)) return leaf.tints;
            return java.util.Collections.emptyList();
        }
        // Recursively search child nodes (for condition/dispatch nodes, only search the current evaluation path)
        if (node instanceof ItemStateNode.ConditionNode) {
            ItemStateNode.ConditionNode cn = (ItemStateNode.ConditionNode) node;
            List<ItemTint> r1 = findTintsForModel(cn.onTrue, path, props);
            if (!r1.isEmpty()) return r1;
            return findTintsForModel(cn.onFalse, path, props);
        }
        if (node instanceof ItemStateNode.RangeDispatchNode) {
            ItemStateNode.RangeDispatchNode rn = (ItemStateNode.RangeDispatchNode) node;
            if (rn.fallback != null) {
                List<ItemTint> r = findTintsForModel(rn.fallback, path, props);
                if (!r.isEmpty()) return r;
            }
            for (ItemStateNode.ThresholdEntry e : rn.entries) {
                List<ItemTint> r = findTintsForModel(e.node, path, props);
                if (!r.isEmpty()) return r;
            }
        }
        if (node instanceof ItemStateNode.SelectNode) {
            ItemStateNode.SelectNode sn = (ItemStateNode.SelectNode) node;
            if (sn.fallback != null) {
                List<ItemTint> r = findTintsForModel(sn.fallback, path, props);
                if (!r.isEmpty()) return r;
            }
            for (ItemStateNode.SelectCase sc : sn.cases) {
                List<ItemTint> r = findTintsForModel(sc.node, path, props);
                if (!r.isEmpty()) return r;
            }
        }
        if (node instanceof ItemStateNode.CompositeNode) {
            ItemStateNode.CompositeNode cn = (ItemStateNode.CompositeNode) node;
            for (ItemStateNode child : cn.models) {
                List<ItemTint> r = findTintsForModel(child, path, props);
                if (!r.isEmpty()) return r;
            }
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Render bridge: resolves the tint color for a given {@code tintIndex} from the
     * {@link ItemStateNode.ModelLeaf} matched by the current {@link ItemStack}; the array
     * position itself is the tintIndex (per-layer semantics).
     * <p>Invoked by {@code ItemStateTintBridge} through the render-side {@code TintRegistry}.
     *
     * @return a 0xRRGGBB color; returns {@code 0xFFFFFF} (no tint) when no tint corresponds
     */
    public int resolveTint(ItemStack stack, RenderPhase phase, int tintIndex) {
        if (tintIndex < 0) return 0xFFFFFF;
        Map<String, Comparable<?>> props = ItemProperties.buildProperties(stack, phase);
        EvalResult result = rootNode.evaluate(props, new EvalContext(stack, phase));
        if (result.isEmpty()) return 0xFFFFFF;
        for (String path : result.getModels()) {
            List<ItemTint> tints = findTintsForModel(rootNode, path, props);
            if (tintIndex < tints.size()) {
                return tints.get(tintIndex).compute(stack, phase) & 0xFFFFFF;
            }
        }
        return 0xFFFFFF;
    }

    /**
     * Whether the decision tree declares any tints, used to decide whether a render bridge
     * needs to be registered (so an empty provider is never registered for a tint-less
     * ItemBlock, which would shadow {@code TintRegistry}'s default color fallback).
     */
    public boolean hasAnyTint() {
        return nodeHasTint(rootNode);
    }

    private static boolean nodeHasTint(ItemStateNode node) {
        if (node == null) return false;
        if (node instanceof ItemStateNode.ModelLeaf) {
            List<ItemTint> t = ((ItemStateNode.ModelLeaf) node).tints;
            return t != null && !t.isEmpty();
        }
        if (node instanceof ItemStateNode.ConditionNode) {
            ItemStateNode.ConditionNode cn = (ItemStateNode.ConditionNode) node;
            return nodeHasTint(cn.onTrue) || nodeHasTint(cn.onFalse);
        }
        if (node instanceof ItemStateNode.RangeDispatchNode) {
            ItemStateNode.RangeDispatchNode rn = (ItemStateNode.RangeDispatchNode) node;
            if (nodeHasTint(rn.fallback)) return true;
            for (ItemStateNode.ThresholdEntry e : rn.entries) if (nodeHasTint(e.node)) return true;
            return false;
        }
        if (node instanceof ItemStateNode.SelectNode) {
            ItemStateNode.SelectNode sn = (ItemStateNode.SelectNode) node;
            if (nodeHasTint(sn.fallback)) return true;
            for (ItemStateNode.SelectCase sc : sn.cases) if (nodeHasTint(sc.node)) return true;
            return false;
        }
        if (node instanceof ItemStateNode.CompositeNode) {
            ItemStateNode.CompositeNode cn = (ItemStateNode.CompositeNode) node;
            for (ItemStateNode child : cn.models) if (nodeHasTint(child)) return true;
            return false;
        }
        return false;
    }

    /**
     * Returns the decision tree root node (used for debugging and texture collection).
     */
    public ItemStateNode getRootNode() {
        return rootNode;
    }

    /**
     * Evaluates the decision tree in the GUI phase and collects the baked model parts for the
     * selected path (for oversized overflow detection).
     */
    public List<BlockStateModelPart> collectGuiModelParts(ItemStack stack) {
        Map<String, Comparable<?>> props = ItemProperties.buildProperties(stack, RenderPhase.ITEM_GUI);
        EvalResult result = rootNode.evaluate(props, new EvalContext(stack, RenderPhase.ITEM_GUI));
        if (result.isEmpty()) return java.util.Collections.emptyList();

        List<BlockStateModelPart> parts = new java.util.ArrayList<>();
        for (String path : result.getModels()) {
            String cacheKey = BakedModelCache.buildKey(path, 0, 0);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            if (part != null && !part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }
}
