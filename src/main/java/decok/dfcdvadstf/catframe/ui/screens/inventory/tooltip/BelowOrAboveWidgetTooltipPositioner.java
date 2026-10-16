package decok.dfcdvadstf.catframe.ui.screens.inventory.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

/**
 * Tooltip positioner — displays below the widget, flipping above when space is insufficient.
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner}.
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
     * Creates a positioner from a {@link ScreenRectangle}.
     */
    public BelowOrAboveWidgetTooltipPositioner(ScreenRectangle rect) {
        this(rect.x, rect.y, rect.width, rect.height);
    }

    @Override
    public int[] positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight) {
        int x = widgetX + 3;
        int y = widgetY + widgetHeight + 3 + 1;

        // Flip above when there is insufficient space below
        if (y + tooltipHeight + 3 > screenHeight) {
            y = widgetY - tooltipHeight - 3 - 1;
        }

        // Right-align on horizontal overflow
        if (x + tooltipWidth > screenWidth) {
            x = Math.max(widgetX + widgetWidth - tooltipWidth - 3, 4);
        }

        return new int[]{x, y};
    }
}
