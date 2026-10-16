package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.ui.navigation.ScreenArea;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

import javax.annotation.Nullable;

/**
 * Picture-in-Picture (PiP) render state — counterpart of 26.1.2 {@code PictureInPictureRenderState}.
 * <p>
 * Describes GUI content that needs its own <b>independent 3D viewport</b> (3D block models, GUI entities,
 * oversized items, etc.). Unlike ordinary 2D elements, PiP content must restore the capture point's modelview
 * matrix when everything is flushed together at the end of the frame, and may optionally set a scissor region.
 *
 * <h3>Correspondence with 26.1.2</h3>
 * <ul>
 *   <li>26.1.2's {@code PictureInPictureRenderState} (bounds/scale/pose) → this interface</li>
 *   <li>26.1.2's {@code GuiRenderState.submitPicturesInPictureState()} collection →
 *       {@code GuiGraphicsExtractor.deferredPip} queue</li>
 *   <li>26.1.2's {@code PictureInPictureRenderer} dispatching draws →
 *       {@link PictureInPictureRenderer}</li>
 * </ul>
 *
 * <p>Implements {@link ScreenArea} so it can later plug into {@code GuiRenderState}'s automatic layering.</p>
 */
public interface PictureInPictureRenderState extends ScreenArea {

    /** Modelview matrix snapshot at capture time (16 elements); may be {@code null} (draw with the current GL state if there is no snapshot). */
    @Nullable
    float[] poseMatrix();

    /** Scissor region; may be {@code null} (no scissoring, content allowed to overflow). */
    @Nullable
    ScreenRectangle scissorArea();

    /** Screen bounds, used for positioning and layering decisions. */
    @Override
    ScreenRectangle bounds();
}
