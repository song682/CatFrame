package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;

/**
 * 渲染作用域门面，对标原版 26w+ 管线中“begin submit → 累积 → end flush”的提交作用域。
 * <p>
 * 以 {@link ThreadLocal} 持有当前活动的 {@link SubmitNodeStorage}（客户端渲染单线程），
 * 支持可重入嵌套（composite / dual 模型多次进入 render）。
 *
 * <h3>三种提交路径</h3>
 * <ul>
 *   <li><b>世界方块</b>（{@link RenderPhase#BLOCK_WORLD}）：CatAtlas 后端（实验性开关）
 *       收集到 {@link WorldRenderBuffer}，在 {@code RenderWorldEvent.Post} 时绑定 CatAtlas
 *       批量绘制；原版后端（默认）则经 {@link FeatureRenderDispatcher#flushInline} 内联
 *       写入当前 chunk Tessellator（批次绑定原版 blocks 图集，quad 携带原版 UV，
 *       无 GL 调用，天然兼容 Beddium 多线程区块编译）。</li>
 *   <li><b>作用域内</b>：有活动作用域时累积到 {@link SubmitNodeStorage}，
 *       {@link #endScope()} 计数归零时按注册表排序键批量 flush。</li>
 *   <li><b>无作用域回退</b>：任何未被 {@link #beginScope()} 包裹的调用路径，
 *       即时以“单项作用域”批量 flush —— 与改造前单次 draw 行为完全一致，零回归。</li>
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
        // 世界方块渲染：CatAtlas 后端收集到世界缓冲（可能从后台线程进入，缓冲内部加锁），
        // 由 RenderWorldEvent.Post 时 flushWorld 绑定 CatAtlas 批量绘制。
        // [Hot Update 撤回方案] 原版后端（默认）：内联写入当前 chunk Tessellator ——
        // 不绑纹理（vanilla 已绑 blocks 图集）、不改 GL 状态，quad 携带原版图集 UV，
        // 天然兼容 Beddium 后台线程（见 FeatureRenderDispatcher.flushInline）。
        // Vanilla backend routes BLOCK_WORLD straight into the chunk batch.
        if (s.phase == RenderPhase.BLOCK_WORLD) {
            if (CatFrameConfig.catAtlasBackend) {
                WorldRenderBuffer.submit(s);
            } else {
                FeatureRenderDispatcher.flushInline(s);
            }
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
