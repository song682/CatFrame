package decok.dfcdvadstf.catframe.ui.screens.inventory.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Default tooltip positioner — offsets to the lower-right of the mouse cursor and
 * auto-corrects on overflow.
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner}.
 */
@SideOnly(Side.CLIENT)
public class DefaultTooltipPositioner implements ClientTooltipPositioner {

    public static final ClientTooltipPositioner INSTANCE = new DefaultTooltipPositioner();

    private DefaultTooltipPositioner() {
    }

    @Override
    public int[] positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight) {
        int x = mouseX + TooltipRenderUtil.MOUSE_OFFSET;
        int y = mouseY - TooltipRenderUtil.MOUSE_OFFSET;

        // Horizontal overflow: flip to the left
        if (x + tooltipWidth > screenWidth) {
            x = Math.max(x - TooltipRenderUtil.MOUSE_OFFSET * 2 - tooltipWidth, 4);
        }

        // Vertical overflow: shift up
        int paddedHeight = tooltipHeight + 3;
        if (y + paddedHeight > screenHeight) {
            y = screenHeight - paddedHeight;
        }

        return new int[]{x, y};
    }
}
