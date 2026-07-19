package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;

/**
 * 渲染作用域门面，对标原版 26w+ 管线中"begin submit → 累积 → end flush"的提交作用域。
 * <p>
 * 以 {@link ThreadLocal} 持有当前活动的 {@link SubmitNodeStorage}（客户端渲染单线程），
 * 支持可重入嵌套（composite / dual 模型多次进入 render）。
 *
 * <h3>三种提交路径</h3>
 * <ul>
 *   <li><b>世界方块</b>（{@link RenderPhase#BLOCK_WORLD}）：永不进缓冲，直接
 *       {@link FeatureRenderDispatcher#flushInline(RenderSubmit)} 写入当前 chunk Tessellator。</li>
 *   <li><b>作用域内</b>：有活动作用域时累积到 {@link SubmitNodeStorage}，
 *       {@link #endScope()} 计数归零时按 RenderType 排序批量 flush。</li>
 *   <li><b>无作用域回退</b>：任何未被 {@link #beginScope()} 包裹的调用路径，
 *       即时以"单项作用域"批量 flush —— 与改造前单次 draw 行为完全一致，零回归。</li>
 * </ul>
 */
public final class RenderCommandBuffers {

    private RenderCommandBuffers() {
    }

    /** 当前线程的活动命令缓冲；null 表示当前无活动作用域。 */
    private static final ThreadLocal<SubmitNodeStorage> ACTIVE = new ThreadLocal<>();
    /** 作用域重入深度（支持嵌套）。 */
    private static final ThreadLocal<int[]> DEPTH = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    /**
     * 进入渲染作用域。首次进入（深度 0→1）时创建活动命令缓冲；嵌套进入仅递增计数。
     */
    public static void beginScope() {
        int[] depth = DEPTH.get();
        if (depth[0] == 0) {
            ACTIVE.set(new SubmitNodeStorage());
        }
        depth[0]++;
    }

    /**
     * 退出渲染作用域。计数归零时批量 flush 并清理活动缓冲。
     * <p>
     * flush 恒发生在调用方已建立的 GL 矩阵上下文内（见 {@link RenderSubmit} 类注释）。
     */
    public static void endScope() {
        int[] depth = DEPTH.get();
        if (depth[0] == 0) {
            // 防御：未配对的 endScope
            return;
        }
        depth[0]--;
        if (depth[0] == 0) {
            SubmitNodeStorage storage = ACTIVE.get();
            ACTIVE.remove();
            if (storage != null && !storage.isEmpty()) {
                FeatureRenderDispatcher.flushBatched(storage);
            }
        }
    }

    /**
     * 提交一条渲染命令。
     *
     * @param s 不可变渲染快照
     */
    public static void submit(RenderSubmit s) {
        // 世界方块渲染：运行于 vanilla chunk 的 Tessellator 大批次内，永不缓冲，直接内联写入
        if (s.phase == RenderPhase.BLOCK_WORLD) {
            FeatureRenderDispatcher.flushInline(s);
            return;
        }

        SubmitNodeStorage active = ACTIVE.get();
        if (active != null) {
            // 作用域内：累积，稍后批量 flush
            active.submit(s);
        } else {
            // 无活动作用域：即时单项批量 flush（等价改造前单次 draw，零回归）
            SubmitNodeStorage single = new SubmitNodeStorage();
            single.submit(s);
            FeatureRenderDispatcher.flushBatched(single);
        }
    }
}
