package decok.dfcdvadstf.catframe.ui.screens.inventory.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Tooltip positioner interface — computes the on-screen render position of a tooltip.
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner}.
 *
 * @see DefaultTooltipPositioner
 * @see MenuTooltipPositioner
 * @see BelowOrAboveWidgetTooltipPositioner
 */
@SideOnly(Side.CLIENT)
public interface ClientTooltipPositioner {

    /**
     * Computes the tooltip position.
     *
     * @param screenWidth    screen width (after scaling)
     * @param screenHeight   screen height (after scaling)
     * @param mouseX         mouse X
     * @param mouseY         mouse Y
     * @param tooltipWidth   tooltip content width
     * @param tooltipHeight  tooltip content height
     * @return the [x, y] coordinates
     */
    int[] positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight);
}
