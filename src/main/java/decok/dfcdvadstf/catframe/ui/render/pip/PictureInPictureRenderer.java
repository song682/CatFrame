package decok.dfcdvadstf.catframe.ui.render.pip;

/**
 * 画中画渲染器 — 对标 26.1.2 {@code PictureInPictureRenderer<T>}。
 * <p>
 * 每种 {@link PictureInPictureRenderState} 类型对应一个渲染器实现。
 * {@code GuiGraphicsExtractor} 维护 {@code Class<?> → PictureInPictureRenderer<?>} 的分派表，
 * 帧末 flush 时按 state 的运行时类型查表并调用 {@link #prepare(PictureInPictureRenderState)}。
 *
 * @param <T> 该渲染器处理的状态类型
 */
public interface PictureInPictureRenderer<T extends PictureInPictureRenderState> {

    /** 该渲染器处理的状态类型 —— 作为分派表的 key。 */
    Class<T> getStateClass();

    /**
     * 绘制一段 PiP 状态。
     * <p>
     * 实现应自行完成：{@code glPushAttrib} → 恢复采集矩阵快照（{@link PipGl#restore}）→
     * 深度/光照等 GL 设置 → 实际绘制 → {@code glPopAttrib}/{@code glPopMatrix}，
     * 保证绘制前后 GL 状态一致（独立 3D 视口语义）。
     */
    void prepare(T state);
}
