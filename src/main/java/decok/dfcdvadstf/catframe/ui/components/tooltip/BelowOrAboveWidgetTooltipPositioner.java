package decok.dfcdvadstf.catframe.ui.components.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

/**
 * 工具提示定位器——在 Widget 下方显示，空间不足时翻转到上方。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner}。
 */
@SideOnly(Side.CLIENT)
public class BelowOrAboveWidgetTooltipPositioner implements ClientTooltipPositioner {

    private final int widgetX, widgetY, widgetWidth, widgetHeight;

    public BelowOrAboveWidgetTooltipPositioner(int widgetX, int widgetY, int widgetWidth, int widgetHeight) {
        this.widgetX = widgetX;
        this.widgetY = widgetY;
        this.widgetWidth = widgetWidth;
        this.widgetHeight = widgetHeight;
    }

    /**
     * 从 {@link ScreenRectangle} 创建定位器。
     */
    public BelowOrAboveWidgetTooltipPositioner(ScreenRectangle rect) {
        this(rect.x, rect.y, rect.width, rect.height);
    }

    @Override
    public int[] positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight) {
        int x = widgetX + 3;
        int y = widgetY + widgetHeight + 3 + 1;

        // 下方空间不足时翻转到上方
        if (y + tooltipHeight + 3 > screenHeight) {
            y = widgetY - tooltipHeight - 3 - 1;
        }

        // 水平溢出时靠右对齐
        if (x + tooltipWidth > screenWidth) {
            x = Math.max(widgetX + widgetWidth - tooltipWidth - 3, 4);
        }

        return new int[]{x, y};
    }
}
