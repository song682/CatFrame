package decok.dfcdvadstf.catframe.model.render.pipeline;

/**
 * 世界方块渲染缓冲：收集 {@link RenderPhase#BLOCK_WORLD} 提交项，在世界渲染后期
 * （{@code RenderWorldEvent.Post}）由 {@link FeatureRenderDispatcher#flushWorld} 按
 * 渲染组一次性批量绘制（绑定 CatAtlas，quad 携带的 CatSprite UV 直接采样）。
 * <p>
 * 世界几何体不再内联写入 vanilla chunk Tessellator —— 该批次绑定原版 blocks 图集，
 * CatSprite 的 CatAtlas 空间 UV 会采样错位；收集-延迟绘制让世界渲染与物品渲染共用
 * 同一纹理表语义：<b>查表未命中 → CatAtlas missing（紫黑格）</b>。
 * <p>
 * <b>线程安全</b>：ISBRH / RenderBlocks mixin 可能从后台线程进入（如 Beddium 多线程
 * 区块编译），submit 与取走以锁互斥；flush 在锁外执行（不持锁做 GL 调用）。
 * World render submissions are buffered here and flushed once per frame after the
 * vanilla world pass, bound to the CatAtlas; table misses show the CatAtlas missing
 * square just like item rendering.
 */
public final class WorldRenderBuffer {

    private static final Object LOCK = new Object();
    private static SubmitNodeStorage storage = new SubmitNodeStorage();

    private WorldRenderBuffer() {
    }

    /** 收集一条世界方块渲染提交（任意线程）。 */
    public static void submit(RenderSubmit s) {
        synchronized (LOCK) {
            storage.submit(s);
        }
    }

    /**
     * 取走当前缓冲并在渲染线程批量绘制（绑定 CatAtlas）。
     * 空缓冲直接返回；缓冲非空时以"交换-再绘制"清空，避免持锁执行 GL 调用。
     */
    public static void flushAndClear() {
        SubmitNodeStorage toFlush;
        synchronized (LOCK) {
            if (storage.isEmpty()) return;
            toFlush = storage;
            storage = new SubmitNodeStorage();
        }
        FeatureRenderDispatcher.flushWorld(toFlush);
    }
}
