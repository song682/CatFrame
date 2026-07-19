package decok.dfcdvadstf.catframe.model.render.pipeline;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 命令缓冲：按 {@link RenderType} 分组累积 {@link RenderSubmit}，对标原版 26w+ 管线中的
 * {@code SubmitNodeStorage}（每 render type 一条命令链）。
 * <p>
 * 使用 {@link EnumMap} 以 {@link RenderType} 声明顺序（solid 先于 translucent）遍历，
 * {@link FeatureRenderDispatcher#flushBatched(SubmitNodeStorage)} 据此实现排序批量绘制。
 * <p>
 * <b>线程模型</b>：客户端渲染单线程；本类不做同步，由
 * {@link RenderCommandBuffers} 以 {@link ThreadLocal} 持有当前活动作用域。
 */
public final class SubmitNodeStorage {

    private final EnumMap<RenderType, List<RenderSubmit>> groups =
            new EnumMap<>(RenderType.class);

    /** 追加一条提交到其 RenderType 分组。 */
    public void submit(RenderSubmit s) {
        List<RenderSubmit> list = groups.get(s.type);
        if (list == null) {
            list = new ArrayList<>();
            groups.put(s.type, list);
        }
        list.add(s);
    }

    /** 是否没有任何提交。 */
    public boolean isEmpty() {
        if (groups.isEmpty()) return true;
        for (List<RenderSubmit> list : groups.values()) {
            if (!list.isEmpty()) return false;
        }
        return true;
    }

    /** 清空所有分组（复用缓冲）。 */
    public void clear() {
        groups.clear();
    }

    /**
     * 按 {@link RenderType} 声明顺序遍历的非空分组视图。
     * EnumMap 天然按枚举序（solid → translucent）迭代。
     */
    public Iterable<Map.Entry<RenderType, List<RenderSubmit>>> groups() {
        return groups.entrySet();
    }
}
