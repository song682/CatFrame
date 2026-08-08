package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.model.render.api.RenderTypeKey;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令缓冲：按 {@link RenderTypeKey} 分组累积 {@link RenderSubmit}，对标原版 26w+ 管线中的
 * {@code SubmitNodeStorage}（每 render type 一条命令链）。
 * <p>
 * 使用 {@link HashMap} 以注册表条目为键（submit 路径 O(1)）；遍历顺序由
 * {@link RenderTypeRegistry#forEachInOrder} 的排序快照决定（sortKey 升序，solid 先于
 * translucent），{@link FeatureRenderDispatcher#flushBatched(SubmitNodeStorage)} 据此
 * 实现排序批量绘制。注册表运行时变更（第三方新增分组）会立即反映到 flush 顺序。
 * <p>
 * <b>线程模型</b>：客户端渲染单线程；本类不做同步，由
 * {@link RenderCommandBuffers} 以 {@link ThreadLocal} 持有当前活动作用域。
 */
public final class SubmitNodeStorage {

    private final Map<RenderTypeKey, List<RenderSubmit>> groups = new HashMap<>();

    /** 追加一条提交到其渲染分组。 */
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
     * 按注册表排序快照顺序（sortKey 升序）产出的非空分组视图。
     * <p>
     * 分组内容（submit 列表）零排序零拷贝；仅收集少量非空分组的只读 entry
     * （数量 = 实际有提交的分组数，内建 4 个，分配可忽略）。
     */
    public List<Map.Entry<RenderTypeKey, List<RenderSubmit>>> groups() {
        List<Map.Entry<RenderTypeKey, List<RenderSubmit>>> result = new ArrayList<>(4);
        RenderTypeRegistry.forEachInOrder(type -> {
            List<RenderSubmit> list = groups.get(type);
            if (list != null && !list.isEmpty()) {
                result.add(new AbstractMap.SimpleImmutableEntry<>(type, list));
            }
        });
        return result;
    }
}
