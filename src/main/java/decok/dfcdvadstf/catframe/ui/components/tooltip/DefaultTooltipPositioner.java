package decok.dfcdvadstf.catframe.ui.components.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 默认工具提示定位器——在鼠标光标右下方向偏移，溢出时自动修正。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner}。
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

        // 水平溢出：翻转到左侧
        if (x + tooltipWidth > screenWidth) {
            x = Math.max(x - TooltipRenderUtil.MOUSE_OFFSET * 2 - tooltipWidth, 4);
        }

        // 垂直溢出：上移
        int paddedHeight = tooltipHeight + 3;
        if (y + paddedHeight > screenHeight) {
            y = screenHeight - paddedHeight;
        }

        return new int[]{x, y};
    }
}
