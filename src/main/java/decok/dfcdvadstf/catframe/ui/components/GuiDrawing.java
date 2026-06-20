package decok.dfcdvadstf.catframe.ui.components;

import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * GUI 绘制工具类 —— 提供公共的矩形绘制等方法。<br>
 * 统一管理 GL 状态和 Tessellator 使用模式。
 * </p>
 * <p>
 * GUI drawing utility — provides common rectangle drawing methods.<br>
 * Centralises GL state management and Tessellator usage patterns.
 * </p>
 */
public final class GuiDrawing {

    private GuiDrawing() {
    }

    /**
     * Draw a filled rectangle with a solid colour.
     * <p>使用纯色绘制填充矩形。</p>
     *
     * @param left   left X coordinate / 左 X 坐标
     * @param top    top Y coordinate / 上 Y 坐标
     * @param right  right X coordinate / 右 X 坐标
     * @param bottom bottom Y coordinate / 下 Y 坐标
     * @param color  ARGB colour / ARGB 颜色
     */
    public static void drawRect(int left, int top, int right, int bottom, int color) {
        if (left > right) { int tmp = left; left = right; right = tmp; }
        if (top > bottom) { int tmp = top; top = bottom; bottom = tmp; }

        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red   = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8  & 255) / 255.0F;
        float blue  = (float) (color       & 255) / 255.0F;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(red, green, blue, alpha);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.draw();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }
}
