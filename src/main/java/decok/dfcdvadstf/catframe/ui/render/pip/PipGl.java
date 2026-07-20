package decok.dfcdvadstf.catframe.ui.render.pip;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;

/**
 * PiP 通道的 GL 辅助 — 恢复采集点的 modelview 矩阵快照。
 * <p>
 * 帧末统一 flush 时 GL 上下文已切换，故 PiP 渲染器绘制前需据快照
 * {@code glLoadMatrix} 重建调用点的变换。与 {@code GuiGraphicsExtractor.MATRIX_BUFFER}
 * 独立以避免跨类可见性耦合（客户端单线程，各自缓冲互不干扰）。
 */
public final class PipGl {

    /** modelview 矩阵恢复缓冲（客户端单线程复用）。 */
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    private PipGl() {
    }

    /**
     * 恢复采集时的 modelview 矩阵。{@code pose} 为 null 时不做任何操作（沿用当前 GL 状态）。
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
