package decok.dfcdvadstf.catframe.ui.components.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

/**
 * 菜单内工具提示定位器——在菜单/容器矩形范围内智能定位。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.MenuTooltipPositioner}。
 */
@SideOnly(Side.CLIENT)
public class MenuTooltipPositioner implements ClientTooltipPositioner {

    private static final int MARGIN = 5;
    private static final int MOUSE_OFFSET_X = 12;
    public static final int MAX_OVERLAP_WITH_WIDGET = 3;
    public static final int MAX_DISTANCE_TO_WIDGET = 5;

    private final int widgetX, widgetY, widgetWidth, widgetHeight;

    public MenuTooltipPositioner(int widgetX, int widgetY, int widgetWidth, int widgetHeight) {
        this.widgetX = widgetX;
        this.widgetY = widgetY;
        this.widgetWidth = widgetWidth;
        this.widgetHeight = widgetHeight;
    }

    /**
     * 从 {@link ScreenRectangle} 创建定位器。
     */
    public MenuTooltipPositioner(ScreenRectangle rect) {
        this(rect.x, rect.y, rect.width, rect.height);
    }

    @Override
    public int[] positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight) {
        int x = mouseX + MOUSE_OFFSET_X;
        int y = mouseY;

        // 水平溢出：靠左显示
        if (x + tooltipWidth > screenWidth - MARGIN) {
            x = Math.max(mouseX - MOUSE_OFFSET_X - tooltipWidth, 9);
        }

        // 垂直定位：优先在 widget 下方，否则上方
        y += MAX_OVERLAP_WITH_WIDGET;
        int paddedHeight = tooltipHeight + MAX_OVERLAP_WITH_WIDGET + MAX_OVERLAP_WITH_WIDGET;
        int lowestPossibleY = widgetY + widgetHeight + MAX_OVERLAP_WITH_WIDGET + getOffset(0, 0, widgetHeight);
        int maxY = screenHeight - MARGIN;

        if (lowestPossibleY + paddedHeight <= maxY) {
            y = y + getOffset(y, widgetY, widgetHeight);
        } else {
            y = y - (paddedHeight + getOffset(y, widgetY + widgetHeight, widgetHeight));
        }

        return new int[]{x, y};
    }

    /**
     * 计算基于鼠标在 widget 内位置的垂直偏移量。
     * <p>对标 26.1.2 {@code Mth.lerp((float)distance / widgetHeight, widgetHeight - 3, 5.0F)}。</p>
     */
    private static int getOffset(int mouseY, int widgetEdgeY, int widgetHeight) {
        int distance = Math.min(Math.abs(mouseY - widgetEdgeY), widgetHeight);
        float t = (float) distance / widgetHeight;
        // lerp(t, a, b) = a + t * (b - a)
        return Math.round(lerp(t, widgetHeight - MAX_OVERLAP_WITH_WIDGET, (float) MAX_DISTANCE_TO_WIDGET));
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }
}
