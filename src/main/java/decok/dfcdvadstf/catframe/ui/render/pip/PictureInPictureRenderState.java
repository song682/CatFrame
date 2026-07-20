package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.ui.navigation.ScreenArea;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

import javax.annotation.Nullable;

/**
 * 画中画（Picture-in-Picture）渲染状态 — 对标 26.1.2 {@code PictureInPictureRenderState}。
 * <p>
 * 描述一段需要<b>独立 3D 视口</b>的 GUI 内容（3D 方块模型、GUI 实体、oversized 物品等）。
 * 与普通 2D 元素不同，PiP 内容在帧末统一 flush 时需要恢复采集点的 modelview 矩阵，
 * 并可选地设置裁剪区域（scissor）。
 *
 * <h3>与 26.1.2 的对应关系</h3>
 * <ul>
 *   <li>26.1.2 的 {@code PictureInPictureRenderState}（bounds/scale/pose）→ 本接口</li>
 *   <li>26.1.2 的 {@code GuiRenderState.submitPicturesInPictureState()} 收集 →
 *       {@code GuiGraphicsExtractor.deferredPip} 队列</li>
 *   <li>26.1.2 的 {@code PictureInPictureRenderer} 分派绘制 →
 *       {@link PictureInPictureRenderer}</li>
 * </ul>
 *
 * <p>实现 {@link ScreenArea} 以便未来接入 {@code GuiRenderState} 的自动分层。</p>
 */
public interface PictureInPictureRenderState extends ScreenArea {

    /** 采集时的 modelview 矩阵快照（16 元素），可为 {@code null}（无快照则按当前 GL 状态绘制）。 */
    @Nullable
    float[] poseMatrix();

    /** 裁剪区域，可为 {@code null}（不裁剪，允许内容溢出）。 */
    @Nullable
    ScreenRectangle scissorArea();

    /** 屏幕边界，用于定位/分层判定。 */
    @Override
    ScreenRectangle bounds();
}
