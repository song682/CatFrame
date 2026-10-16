package decok.dfcdvadstf.catframe.ui.render.pip;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;

/**
 * GL helpers for the PiP pipeline — restores the modelview matrix snapshot from the capture point.
 * <p>
 * By the time the end-of-frame flush runs, the GL context has already changed, so PiP
 * renderers must rebuild the capture-point transform from the snapshot via
 * {@code glLoadMatrix} before drawing. Kept independent of {@code GuiGraphicsExtractor.MATRIX_BUFFER}
 * to avoid cross-class visibility coupling (the client is single-threaded, so the buffers never interfere).
 */
public final class PipGl {

    /** Modelview matrix restore buffer (reused on the single client thread). */
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    private PipGl() {
    }

    /**
     * Restores the modelview matrix captured at extraction time. Does nothing when
     * {@code pose} is null (the current GL state is kept).
     */
    public static void restore(@Nullable float[] pose) {
        if (pose == null) return;
        MATRIX_BUFFER.clear();
        MATRIX_BUFFER.put(pose);
        MATRIX_BUFFER.flip();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadMatrix(MATRIX_BUFFER);
    }
}
