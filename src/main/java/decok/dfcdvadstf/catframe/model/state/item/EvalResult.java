package decok.dfcdvadstf.catframe.model.state.item;

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
 * 内部统一用 {@code List<String>} 表示：
 * single = 1 元素列表，composite = 多元素列表，empty = 空列表。
 */
public final class EvalResult {

    /** 空结果单例 */
    private static final EvalResult EMPTY = new EvalResult(Collections.<String>emptyList());

    /** 选中的模型路径列表（single=1个, composite=多个, empty=0个） */
    private final List<String> models;

    private EvalResult(List<String> models) {
        this.models = models;
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
        return new EvalResult(Collections.singletonList(modelPath));
    }

    /**
     * 多模型分层结果（composite 节点）。
     *
     * @param modelPaths 按渲染顺序排列的模型路径列表
     * @return 求值结果
     */
    public static EvalResult composite(List<String> modelPaths) {
        if (modelPaths == null || modelPaths.isEmpty()) return EMPTY;
        return new EvalResult(new ArrayList<>(modelPaths));
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
     * @return true 表示无模型可渲染
     */
    public boolean isEmpty() {
        return models.isEmpty();
    }

    /**
     * @return true 表示单模型结果
     */
    public boolean isSingle() {
        return models.size() == 1;
    }

    /**
     * @return true 表示多模型分层结果
     */
    public boolean isComposite() {
        return models.size() > 1;
    }

    /**
     * 获取模型路径列表（不可变）。
     * <p>
     * single 返回 1 元素列表，composite 返回多元素列表，empty 返回空列表。
     *
     * @return 模型路径列表（不可变）
     */
    public List<String> getModels() {
        return models;
    }

    /**
     * 获取第一个模型路径（向后兼容单模型场景）。
     *
     * @return 第一个模型路径，无模型时返回 null
     */
    public String getFirstModel() {
        return models.isEmpty() ? null : models.get(0);
    }

    @Override
    public String toString() {
        if (isEmpty()) return "EvalResult.empty()";
        if (isSingle()) return "EvalResult.single(" + models.get(0) + ")";
        return "EvalResult.composite(" + models + ")";
    }
}
