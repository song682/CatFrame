package decok.dfcdvadstf.catframe.model.render.pipeline;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.render.api.IRenderGroupHandler;
import decok.dfcdvadstf.catframe.model.render.api.RenderTypeKey;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 分组认领 handler 注册表（内部实现）。
 * <p>
 * 镜像 {@link decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry} 的并发/优先级模式：
 * {@link CopyOnWriteArrayList} 存储（注册/注销与渲染遍历并发安全）、优先级越小越先仲裁
 * （{@link #DEFAULT_PRIORITY}=0）、同优先级注册序稳定、重复注册 = 更新优先级并重新定位。
 * <p>
 * <b>槽位仲裁（注册表变更时一次性）</b>：注册/注销 handler 时，对注册表排序快照的每个
 * 分组依次询问各 handler 的 {@link IRenderGroupHandler#claim(RenderTypeKey)}（按优先级升序，
 * 首个认领者胜出），写入 {@code volatile} 槽位数组（与排序快照同索引）；flush 路径只读
 * 槽位 —— 零排序零 claim 调用。单个 handler 的 claim 异常被隔离（视为不认领，记日志）。
 * 注册表新增分组但 handler 未重注册时，新槽位无认领者 → 走内建 flush。
 */
@SideOnly(Side.CLIENT)
public final class RenderGroupHandlerRegistry {

    /** handler 默认优先级：0 级按注册顺序仲裁。 */
    public static final int DEFAULT_PRIORITY = 0;

    private static final List<HandlerEntry> HANDLERS = new CopyOnWriteArrayList<>();
    /** 槽位仲裁快照（volatile 发布）：与注册表排序快照同索引，同槽取 priority 最小者。 */
    private static volatile IRenderGroupHandler[] slotHandlers = new IRenderGroupHandler[0];

    private RenderGroupHandlerRegistry() {
    }

    /**
     * 注册一个分组认领 handler（默认优先级 0）。同一实例重复注册 = 重新定位并重新仲裁。
     */
    public static void register(IRenderGroupHandler handler) {
        register(handler, DEFAULT_PRIORITY);
    }

    /**
     * 以显式优先级注册分组认领 handler。优先级越小越先仲裁，同优先级按注册顺序稳定排列。
     * 同一实例重复注册 = 更新优先级并重新定位（触发全量仲裁，可借此刷新 claim 判定）。
     *
     * @param handler  认领 handler（null 忽略）
     * @param priority 优先级
     */
    public static void register(IRenderGroupHandler handler, int priority) {
        if (handler == null) return;
        HANDLERS.removeIf(e -> e.handler == handler);
        insertSorted(handler, priority);
        rebuildSlots();
    }

    /**
     * 取消注册一个 handler（触发全量仲裁，空出的槽位回退内建 flush）。
     */
    public static void unregister(IRenderGroupHandler handler) {
        if (handler != null) {
            HANDLERS.removeIf(e -> e.handler == handler);
            rebuildSlots();
        }
    }

    /**
     * 当前已注册的 handler 数。
     */
    public static int size() {
        return HANDLERS.size();
    }

    /**
     * 渲染器内部使用：取某分组仲裁出的认领 handler（无认领者返回 null → 内建 flush）。
     * 只读槽位快照，零 claim 调用，O(分组数) 线性扫描。
     */
    static IRenderGroupHandler handlerFor(RenderTypeKey type) {
        RenderTypeKey[] snapshot = RenderTypeRegistry.orderedSnapshot();
        IRenderGroupHandler[] slots = slotHandlers;
        int len = Math.min(snapshot.length, slots.length);
        for (int i = 0; i < len; i++) {
            if (snapshot[i] == type) {
                return slots[i];
            }
        }
        return null;
    }

    /** 全量仲裁：对排序快照每个分组取 priority 最小的认领者（claim 异常 = 不认领）。 */
    private static void rebuildSlots() {
        RenderTypeKey[] snapshot = RenderTypeRegistry.orderedSnapshot();
        IRenderGroupHandler[] slots = new IRenderGroupHandler[snapshot.length];
        for (int i = 0; i < snapshot.length; i++) {
            final RenderTypeKey type = snapshot[i];
            for (HandlerEntry e : HANDLERS) { // 已按优先级升序，首个认领者胜出
                if (safeClaim(e, type)) {
                    slots[i] = e.handler;
                    break;
                }
            }
        }
        slotHandlers = slots;
    }

    /** 错误隔离：单个 handler 的 claim 异常记日志并视为不认领（不影响其他 handler 与仲裁）。 */
    private static boolean safeClaim(HandlerEntry e, RenderTypeKey type) {
        try {
            return e.handler.claim(type);
        } catch (Throwable t) {
            CatFrame.logger.warn("[RenderGroupHandlerRegistry] handler {} failed in claim({}): {}",
                    e.handler.getClass().getName(), type.id(), t.toString(), t);
            return false;
        }
    }

    /** 稳定排序插入：与 ModelRenderRegistry.insertSorted 同语义（同优先级追加到同组末尾）。 */
    private static void insertSorted(IRenderGroupHandler handler, int priority) {
        int insertAt = HANDLERS.size();
        for (int i = 0; i < HANDLERS.size(); i++) {
            if (HANDLERS.get(i).priority > priority) {
                insertAt = i;
                break;
            }
        }
        HANDLERS.add(insertAt, new HandlerEntry(handler, priority));
    }

    /** handler 条目：实例 + 注册优先级。 */
    private static final class HandlerEntry {
        final IRenderGroupHandler handler;
        final int priority;

        HandlerEntry(IRenderGroupHandler handler, int priority) {
            this.handler = handler;
            this.priority = priority;
        }
    }
}
