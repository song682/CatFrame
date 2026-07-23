package decok.dfcdvadstf.catframe.ui.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 工具提示定位器接口——计算 tooltip 在屏幕上的渲染位置。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner}。
 *
 * @see DefaultTooltipPositioner
 * @see MenuTooltipPositioner
 * @see BelowOrAboveWidgetTooltipPositioner
 */
@SideOnly(Side.CLIENT)
public interface ClientTooltipPositioner {

    /**
     * 计算 tooltip 的位置。
     *
     * @param screenWidth    屏幕宽度（缩放后）
     * @param screenHeight   屏幕高度（缩放后）
     * @param mouseX         鼠标 X
     * @param mouseY         鼠标 Y
     * @param tooltipWidth   tooltip 内容宽度
     * @param tooltipHeight  tooltip 内容高度
     * @return [x, y] 坐标
     */
    int[] positionTooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int tooltipWidth, int tooltipHeight);
}
