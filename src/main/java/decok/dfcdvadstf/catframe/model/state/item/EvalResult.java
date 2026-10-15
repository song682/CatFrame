package decok.dfcdvadstf.catframe.model.state.item;

import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ItemState 决策树求值结果。
 * <p>
 * 封装三种结果形态：
 * <ul>
 *   <li>{@link #single(String)} — 单模型（{@link ItemStateNode.ModelLeaf} 返回）</li>
 *   <li>{@link #composite(List)} — 多模型分层（{@link ItemStateNode.CompositeNode} 返回）</li>
 *   <li>{@link #empty()} — 空结果（{@link ItemStateNode.EmptyNode} 或 fallback 耗尽）</li>
 * </ul>
 * <p>
 * 内部统一用有序的 {@link Layer} 列表表示：single = 1 图层，composite = 多图层，
 * empty = 空列表。每个图层携带一个静态模型路径（{@link Layer#modelPath}），或一个
 * 动态内容标记（{@link Layer#dynamicStack}，对齐高版本 {@code appendItemLayers}：
 * 特殊节点在求值时读 NBT 追加的"渲染这个物品栈自身模型"图层）。
 * <p>
 * 既有 {@link #getModels()} 语义保持不变（返回静态模型路径列表）；动态图层经
 * {@link #getLayers()} 交付给渲染管线递归处理。Existing {@link #getModels()} keeps its
 * original meaning (static model paths); dynamic layers surface through {@link #getLayers()}.
 */
public final class EvalResult {

    /**
     * 单个渲染图层：静态模型路径或动态内容栈（二者取其一）。
     * <p>A single render layer: either a static model path or a dynamic content stack.
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

        /** 静态模型图层。A static model layer. */
        public static Layer ofModel(String path) {
            return new Layer(path, null);
        }

        /** 动态内容图层：渲染该物品栈自身注册的模型。A dynamic layer rendering the stack's own model. */
        public static Layer ofDynamic(ItemStack stack) {
            return new Layer(null, stack);
        }

        /** 是否为动态内容图层。Whether this layer carries a dynamic stack rather than a fixed model. */
        public boolean isDynamic() {
            return dynamicStack != null;
        }
    }

    /** 空结果单例 */
    private static final EvalResult EMPTY = new EvalResult(Collections.<Layer>emptyList());

    /** 有序图层列表（single=1个, composite=多个, empty=0个） */
    private final List<Layer> layers;

    /** 静态模型路径列表缓存（惰性从 layers 派生，保持 getModels() 旧语义） */
    private List<String> modelsCache;

    private EvalResult(List<Layer> layers) {
        this.layers = layers;
    }

    // ==================== 工厂方法 ====================

    /**
     * 单模型结果。
     *
     * @param modelPath 模型路径，null 等价于 empty()
     * @return 求值结果
     */
    public static EvalResult single(String modelPath) {
        if (modelPath == null) return EMPTY;
        return new EvalResult(Collections.singletonList(Layer.ofModel(modelPath)));
    }

    /**
     * 多模型分层结果（composite 节点，全静态模型）。
     *
     * @param modelPaths 按渲染顺序排列的模型路径列表
     * @return 求值结果
     */
    public static EvalResult composite(List<String> modelPaths) {
        if (modelPaths == null || modelPaths.isEmpty()) return EMPTY;
        List<Layer> ls = new ArrayList<>(modelPaths.size());
        for (String p : modelPaths) ls.add(Layer.ofModel(p));
        return new EvalResult(ls);
    }

    /**
     * 多图层结果（可混合静态模型与动态内容），供 composite 合并子树保序使用。
     * <p>Multi-layer result (mixing static models and dynamic content); used by composite
     * nodes to merge child layers in order.
     *
     * @param layers 按渲染顺序排列的图层列表
     * @return 求值结果，空列表等价于 empty()
     */
    public static EvalResult compositeLayers(List<Layer> layers) {
        if (layers == null || layers.isEmpty()) return EMPTY;
        return new EvalResult(new ArrayList<>(layers));
    }

    /**
     * 动态内容单图层结果（特殊节点追加"渲染该栈自身模型"）。
     * <p>Single dynamic-content layer (special node appends "render this stack's own model").
     *
     * @param stack 内容物品栈，null 等价于 empty()
     * @return 求值结果
     */
    public static EvalResult dynamic(ItemStack stack) {
        if (stack == null) return EMPTY;
        return new EvalResult(Collections.singletonList(Layer.ofDynamic(stack)));
    }

    /**
     * 空结果：不渲染任何模型。
     *
     * @return 空结果单例
     */
    public static EvalResult empty() {
        return EMPTY;
    }

    // ==================== 查询方法 ====================

    /**
     * @return true 表示无任何图层可渲染
     */
    public boolean isEmpty() {
        return layers.isEmpty();
    }

    /**
     * @return true 表示单图层结果
     */
    public boolean isSingle() {
        return layers.size() == 1;
    }

    /**
     * @return true 表示多图层分层结果
     */
    public boolean isComposite() {
        return layers.size() > 1;
    }

    /**
     * 获取有序图层列表（不可变），含静态模型与动态内容图层。
     * <p>Ordered layer list (unmodifiable), including both static and dynamic layers.
     *
     * @return 图层列表
     */
    public List<Layer> getLayers() {
        return layers;
    }

    /**
     * 获取静态模型路径列表（不可变），忽略动态内容图层。
     * <p>
     * single 返回 1 元素列表，composite 返回多元素列表，empty 返回空列表。
     * 语义与扩展前一致，既有仅关心静态模型的调用点（tint 解析、GUI 溢出部件收集）无需改动。
     *
     * @return 静态模型路径列表（不可变）
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
     * 获取第一个静态模型路径（向后兼容单模型场景）。
     *
     * @return 第一个静态模型路径，无静态模型时返回 null
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
