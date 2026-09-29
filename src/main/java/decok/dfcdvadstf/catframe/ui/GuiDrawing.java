package decok.dfcdvadstf.catframe.ui;

import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * GUI drawing utility — provides common rectangle drawing methods.<br>
 * Centralises GL state management and Tessellator usage patterns.
 * </p>
 */
public final class GuiDrawing {

    /**
     * Draw a filled rectangle with a solid colour.
     *
     * @param left   left X coordinate
     * @param top    top Y coordinate
     * @param right  right X coordinate
     * @param bottom bottom Y coordinate
     * @param color  ARGB colour
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
