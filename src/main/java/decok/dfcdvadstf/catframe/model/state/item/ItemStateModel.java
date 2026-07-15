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
        if (result.isEmpty()) return;

        // 3. 遍历所有选中的模型路径，逐个渲染（composite 时多模型分层）
        for (String path : result.getModels()) {
            String cacheKey = BakedModelCache.buildKey(path, 0, 0);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            if (part == null || part.isEmpty()) continue;

            // 查找对应 ModelLeaf 的 tints
            List<ItemTint> tints = findTintsForModel(rootNode, path, props);
            int tintOverride = computeTint(tints, stack, phase);

            // TODO: 将 tintOverride 传递给渲染管线（当前仅计算，未应用）
            // preTransform 传递到管线，使反抵消矩阵正确作用于顶点
            UniformRenderPipeline.renderItemQuads(part, stack, phase,
                    null, 0, 0, 0, null, preTransform);
        }
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
        if (node instanceof ItemStateNode.ExactMatchNode) {
            ItemStateNode.ExactMatchNode en = (ItemStateNode.ExactMatchNode) node;
            if (en.fallback != null) {
                List<ItemTint> r = findTintsForModel(en.fallback, path, props);
                if (!r.isEmpty()) return r;
            }
            for (ItemStateNode child : en.cases.values()) {
                List<ItemTint> r = findTintsForModel(child, path, props);
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
     * 计算 tint 覆盖颜色。多个 tint 按顺序混合（相乘）。
     *
     * @return 混合后的 RGB 颜色，无 tint 时返回 -1（表示不覆盖）
     */
    private static int computeTint(List<ItemTint> tints, ItemStack stack, RenderPhase phase) {
        if (tints == null || tints.isEmpty()) return -1;
        int r = 255, g = 255, b = 255;
        for (ItemTint tint : tints) {
            int c = tint.compute(stack, phase);
            r = r * ((c >> 16) & 0xFF) / 255;
            g = g * ((c >> 8) & 0xFF) / 255;
            b = b * (c & 0xFF) / 255;
        }
        return (r << 16) | (g << 8) | b;
    }

    /**
     * 返回决策树根节点（用于调试和纹理收集）。
     */
    public ItemStateNode getRootNode() {
        return rootNode;
    }
}
