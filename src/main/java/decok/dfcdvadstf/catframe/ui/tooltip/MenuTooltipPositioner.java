package decok.dfcdvadstf.catframe.ui.tooltip;

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
        int x = mouseX + 12;
        int y = mouseY + 3;

        // 水平溢出：靠左显示
        if (x + tooltipWidth > screenWidth - 5) {
            x = Math.max(mouseX - 12 - tooltipWidth, 9);
        }

        // 垂直定位：优先在 widget 下方，否则上方
        int paddedHeight = tooltipHeight + 6;
        int lowestPossibleY = widgetY + widgetHeight + 3 + getOffset(0, 0, widgetHeight);
        int maxY = screenHeight - 5;

        if (lowestPossibleY + paddedHeight <= maxY) {
            y = y + getOffset(y, widgetY, widgetHeight);
        } else {
            y = y - (paddedHeight + getOffset(y, widgetY + widgetHeight, widgetHeight));
        }

        return new int[]{x, y};
    }

    /**
     * 计算基于鼠标在 widget 内位置的垂直偏移量。
     * 鼠标靠近 widget 顶部时偏移大（留足空间），靠近底部时偏移小。
     */
    private static int getOffset(int mouseY, int widgetEdgeY, int widgetHeight) {
        int distance = Math.min(Math.abs(mouseY - widgetEdgeY), widgetHeight);
        float t = (float) distance / widgetHeight;
        return Math.round(t * (widgetHeight - 3 - 5) + 5);
    }
}
