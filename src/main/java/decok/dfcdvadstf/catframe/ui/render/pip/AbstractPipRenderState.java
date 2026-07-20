package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

import javax.annotation.Nullable;

/**
 * PiP 渲染状态抽象基类 — 持有采集点矩阵快照、边界与可选裁剪区域，供内置/扩展状态复用。
 */
public abstract class AbstractPipRenderState implements PictureInPictureRenderState {

    @Nullable
    protected final float[] poseMatrix;

    protected final ScreenRectangle bounds;

    @Nullable
    protected final ScreenRectangle scissorArea;

    protected AbstractPipRenderState(@Nullable float[] poseMatrix,
                                     ScreenRectangle bounds,
                                     @Nullable ScreenRectangle scissorArea) {
        this.poseMatrix = poseMatrix;
        this.bounds = bounds;
        this.scissorArea = scissorArea;
    }

    @Nullable
    @Override
    public float[] poseMatrix() {
        return poseMatrix;
    }

    @Nullable
    @Override
    public ScreenRectangle scissorArea() {
        return scissorArea;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }
}
