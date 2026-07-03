package decok.dfcdvadstf.catframe.ui.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.components.GuiDrawing;

/**
 * 工具提示背景渲染工具——绘制 tooltip 的深色背景与边框。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil}。
 * 使用 UI 包的 {@link GuiDrawing} 进行绘制，与 UI 包渲染风格一致。
 */
@SideOnly(Side.CLIENT)
public class TooltipRenderUtil {

    /** tooltip 内边距（文字到背景边缘） */
    public static final int PADDING = 4;
    public static final int PADDING_LEFT = 4;
    public static final int PADDING_RIGHT = 4;
    public static final int PADDING_TOP = 4;
    public static final int PADDING_BOTTOM = 4;

    private static final int BACKGROUND_COLOR = 0xF0100010;
    private static final int BORDER_COLOR_TOP_LEFT = 0x505000FF;
    private static final int BORDER_COLOR_BOTTOM_RIGHT = 0x5028007F;

    /**
     * 渲染 tooltip 背景。
     *
     * @param x tooltip 文字区域左上 X
     * @param y tooltip 文字区域左上 Y
     * @param w 文字区域宽度
     * @param h 文字区域高度
     */
    public static void renderTooltipBackground(int x, int y, int w, int h) {
        int bgX = x - PADDING;
        int bgY = y - PADDING;
        int bgW = w + PADDING + PADDING;
        int bgH = h + PADDING + PADDING;

        // 深色半透明背景
        GuiDrawing.drawRect(bgX, bgY, bgX + bgW, bgY + bgH, BACKGROUND_COLOR);

        // 边框：分别绘制四条边
        GuiDrawing.drawRect(bgX, bgY - 1, bgX + bgW, bgY, BORDER_COLOR_TOP_LEFT);                       // 上边
        GuiDrawing.drawRect(bgX, bgY + bgH, bgX + bgW, bgY + bgH + 1, BORDER_COLOR_BOTTOM_RIGHT);        // 下边
        GuiDrawing.drawRect(bgX - 1, bgY, bgX, bgY + bgH, BORDER_COLOR_TOP_LEFT);                        // 左边
        GuiDrawing.drawRect(bgX + bgW, bgY, bgX + bgW + 1, bgY + bgH, BORDER_COLOR_BOTTOM_RIGHT);        // 右边
    }
}
