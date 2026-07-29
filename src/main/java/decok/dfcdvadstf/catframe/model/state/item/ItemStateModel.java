package decok.dfcdvadstf.catframe.model.state.item;

import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
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
 * ItemState 决策树驱动的物品模型。
 * <p>
 * 这是 {@code LazyItemModel}（单模型路径懒烘焙）与
 * {@code ItemStateItemModel}（决策树分派）合并后的统一实现：
 * <ul>
 *   <li>无抉择时，内部退化为单个 {@link ItemStateNode.ModelLeaf}，等价于原 LazyItemModel</li>
 *   <li>有抉择时，持有完整 {@link ItemStateNode} 决策树，运行时根据 ItemStack 数据求值</li>
 * </ul>
 *
 * <p>渲染流程：
 * <ol>
 *   <li>调用 {@link ItemProperties#buildProperties(ItemStack, RenderPhase)} 构建运行时属性集</li>
 *   <li>调用 {@link ItemStateNode#evaluate(Map)} 递归求值，得到最终模型路径</li>
 *   <li>从 {@link BakedModelCache} 懒获取选中模型</li>
 *   <li>通过 {@link UniformRenderPipeline#renderItemQuads} 渲染</li>
 * </ol>
 */
public class ItemStateModel implements IItemStateProvider {

    private final ItemStateNode rootNode;

    /**
     * 从单模型路径构造（无抉择，退化为 ModelLeaf）。
     *
     * @param modelPath 模型路径
     */
    public ItemStateModel(String modelPath) {
        this(new ItemStateNode.ModelLeaf(modelPath));
    }

    /**
     * 从决策树根节点构造。
     *
     * @param rootNode 决策树根节点（不可为 null）
     */
    public ItemStateModel(ItemStateNode rootNode) {
        if (rootNode == null) {
            throw new IllegalArgumentException("ItemStateModel root node must not be null");
        }
        this.rootNode = rootNode;
    }

    @Override
    public void render(ItemStack stack, RenderPhase phase) {
        render(stack, phase, null);
    }

    @Override
    public void render(ItemStack stack, RenderPhase phase,
                       @Nullable Matrix4d preTransform) {
        // 1. 构建运行时属性集
        Map<String, Comparable<?>> props = ItemProperties.buildProperties(stack, phase);

        // 2. 递归求值决策树，得到 EvalResult
        EvalResult result = rootNode.evaluate(props);
        if (result.isEmpty()) {
            // 决策树未找到任何模型 → fallback 到 builtin/missing
            String cacheKey = BakedModelCache.buildKey("builtin/missing", 0, 0);
            BlockStateModelPart missing = BakedModelCache.INSTANCE.get(cacheKey);
            if (missing != null && !missing.isEmpty()) {
                UniformRenderPipeline.renderItemQuads(missing, stack, phase,
                        null, 0, 0, 0, null, preTransform);
            }
            return;
        }

        // 3. 遍历所有选中的模型路径，逐个渲染（composite 时多模型分层）
        for (String path : result.getModels()) {
            String cacheKey = BakedModelCache.buildKey(path, 0, 0);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            if (part == null || part.isEmpty()) continue;

            // tint 已通过 TintRegistry 桥接（ItemStateTintBridge）+ TintRenderExtension
            // 按 quad 的 tintindex 应用，此处不再直接计算颜色。
            // preTransform 传递到管线，使反抵消矩阵正确作用于顶点。
            // Tints are applied via the TintRegistry bridge + TintRenderExtension by
            // each quad's tintindex; no direct color computation is needed here.
            // transformation：命中 ModelLeaf 声明的物品模型渲染变换（可选，默认单位变换），
            // 由管线在 display 变换之后逐顶点应用。
            // The matched ModelLeaf's optional per-model transformation is applied by the
            // pipeline after the display transform (identity when absent).
            Matrix4d transformation = findTransformationForModel(rootNode, path);
            UniformRenderPipeline.renderItemQuads(part, stack, phase,
                    null, 0, 0, 0, null, preTransform, transformation);
        }
    }

    /**
     * 在决策树中查找指定模型路径的 ModelLeaf，提取其 transformation 矩阵。
     * <p>
     * 遍历策略与 {@link #findTintsForModel} 一致：命中路径且声明了非空变换的
     * 第一个叶子胜出；未声明时返回 {@code null}（单位变换）。
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
     * 在决策树中查找指定模型路径的 ModelLeaf，提取其 tints。
     */
    private static List<ItemTint> findTintsForModel(ItemStateNode node, String path,
                                                      Map<String, Comparable<?>> props) {
        if (node instanceof ItemStateNode.ModelLeaf) {
            ItemStateNode.ModelLeaf leaf = (ItemStateNode.ModelLeaf) node;
            if (path.equals(leaf.model)) return leaf.tints;
            return java.util.Collections.emptyList();
        }
        // 递归搜索子节点（对于条件/分派节点，只搜索当前求值路径）
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
     * 渲染桥接：解析当前 {@link ItemStack} 命中的 {@link ItemStateNode.ModelLeaf} 的 tints，
     * 返回指定 {@code tintIndex} 的颜色。数组位置即 tintIndex（per-layer 语义）。
     * <p>由 {@code ItemStateTintBridge} 转交给渲染侧 {@code TintRegistry} 调用。
     *
     * <p>Render bridge: resolves the tint color for a given {@code tintIndex} from the
     * {@link ItemStateNode.ModelLeaf} matched by the current {@link ItemStack}; the array
     * position itself is the tintIndex (per-layer semantics). Invoked by
     * {@code ItemStateTintBridge} through the render-side {@code TintRegistry}.
     *
     * @return 0xRRGGBB 颜色；无对应 tint 时返回 {@code 0xFFFFFF}（不染色）
     */
    public int resolveTint(ItemStack stack, RenderPhase phase, int tintIndex) {
        if (tintIndex < 0) return 0xFFFFFF;
        Map<String, Comparable<?>> props = ItemProperties.buildProperties(stack, phase);
        EvalResult result = rootNode.evaluate(props);
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
     * 决策树是否声明了任何 tints，用于决定是否需要注册渲染桥接（避免为无 tints 的
     * ItemBlock 注册空 provider，从而遮蔽 {@code TintRegistry} 的默认染色回退）。
     *
     * <p>Whether the decision tree declares any tints; gates bridge registration so we
     * never shadow {@code TintRegistry}'s default ItemBlock color fallback with an
     * empty provider.
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
     * 返回决策树根节点（用于调试和纹理收集）。
     */
    public ItemStateNode getRootNode() {
        return rootNode;
    }

    /**
     * GUI 阶段求值决策树，收集选中路径对应的烘焙模型部件（供 oversized 溢出检测）。
     */
    @Override
    public List<BlockStateModelPart> getGuiModelParts(ItemStack stack) {
        Map<String, Comparable<?>> props = ItemProperties.buildProperties(stack, RenderPhase.ITEM_GUI);
        EvalResult result = rootNode.evaluate(props);
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
