package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

import javax.annotation.Nullable;

/**
 * PiP render state abstract base — holds capture-point matrix snapshot, bounds,
 * and optional scissor area for reuse by built-in/extended states.
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
