package decok.dfcdvadstf.catframe.ui.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * 工具提示背景渲染工具——复刻原版 1.7.10 {@code GuiScreen.drawHoveringText} 的渐变背景与边框。
 * <p>
 * 不使用任何自定义纹理，也不做纹理拉伸：背景为半透明深色渐变、边框为紫色渐变，
 * 像素级对齐原版 tooltip。本类只是把原版画法搬到延迟管线里，属于纯兼容层。
 */
@SideOnly(Side.CLIENT)
public final class TooltipRenderUtil {

    private TooltipRenderUtil() {}

    /** 鼠标偏移量（对标原版 tooltip 相对鼠标的 12px 偏移） */
    public static final int MOUSE_OFFSET = 12;

    /** 背景填充色（对标原版 {@code 0xF0100010}） */
    private static final int BACKGROUND_COLOR = 0xF0100010;
    /** 边框起始色（对标原版 {@code 0x505000FF}） */
    private static final int BORDER_COLOR_TOP = 0x505000FF;
    /** 边框结束色（原版：{@code (top & 0xFEFEFE) >> 1 | (top & 0xFF000000)}） */
    private static final int BORDER_COLOR_BOTTOM = (BORDER_COLOR_TOP & 0xFEFEFE) >> 1 | (BORDER_COLOR_TOP & 0xFF000000);

    /** tooltip 绘制层级——与原版 drawHoveringText 一致 */
    private static final float Z_LEVEL = 300.0F;

    /**
     * 渲染 tooltip 背景（原版渐变的九块矩形：填充 + 上下左右边框）。
     *
     * @param x tooltip 文字区域左上 X
     * @param y tooltip 文字区域左上 Y
     * @param w 文字区域宽度
     * @param h 文字区域高度
     */
    public static void renderTooltipBackground(int x, int y, int w, int h) {
        drawGradientRect(x - 3, y - 4, x + w + 3, y - 3, BACKGROUND_COLOR, BACKGROUND_COLOR);
        drawGradientRect(x - 3, y + h + 3, x + w + 3, y + h + 4, BACKGROUND_COLOR, BACKGROUND_COLOR);
        drawGradientRect(x - 3, y - 3, x + w + 3, y + h + 3, BACKGROUND_COLOR, BACKGROUND_COLOR);
        drawGradientRect(x - 4, y - 3, x - 3, y + h + 3, BACKGROUND_COLOR, BACKGROUND_COLOR);
        drawGradientRect(x + w + 3, y - 3, x + w + 4, y + h + 3, BACKGROUND_COLOR, BACKGROUND_COLOR);
        drawGradientRect(x - 3, y - 3 + 1, x - 3 + 1, y + h + 3 - 1, BORDER_COLOR_TOP, BORDER_COLOR_BOTTOM);
        drawGradientRect(x + w + 2, y - 3 + 1, x + w + 3, y + h + 3 - 1, BORDER_COLOR_TOP, BORDER_COLOR_BOTTOM);
        drawGradientRect(x - 3, y - 3, x + w + 3, y - 3 + 1, BORDER_COLOR_TOP, BORDER_COLOR_TOP);
        drawGradientRect(x - 3, y + h + 2, x + w + 3, y + h + 3, BORDER_COLOR_BOTTOM, BORDER_COLOR_BOTTOM);
    }

    /**
     * 绘制垂直渐变矩形——复刻原版 {@code Gui.drawGradientRect}。
     * <p>结束时恢复为「纹理开启 + FLAT 着色」，以便后续文字正常渲染。</p>
     */
    private static void drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor) {
        float a1 = (startColor >> 24 & 255) / 255.0F;
        float r1 = (startColor >> 16 & 255) / 255.0F;
        float g1 = (startColor >> 8 & 255) / 255.0F;
        float b1 = (startColor & 255) / 255.0F;
        float a2 = (endColor >> 24 & 255) / 255.0F;
        float r2 = (endColor >> 16 & 255) / 255.0F;
        float g2 = (endColor >> 8 & 255) / 255.0F;
        float b2 = (endColor & 255) / 255.0F;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(r1, g1, b1, a1);
        tessellator.addVertex(right, top, Z_LEVEL);
        tessellator.addVertex(left, top, Z_LEVEL);
        tessellator.setColorRGBA_F(r2, g2, b2, a2);
        tessellator.addVertex(left, bottom, Z_LEVEL);
        tessellator.addVertex(right, bottom, Z_LEVEL);
        tessellator.draw();

        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
