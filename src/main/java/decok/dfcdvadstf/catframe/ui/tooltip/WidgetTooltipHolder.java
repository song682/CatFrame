package decok.dfcdvadstf.catframe.ui.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.render.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Widget 级工具提示管理器——为单个 Widget 提供延迟显示与焦点/悬停切换。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.client.gui.components.WidgetTooltipHolder}。
 * 使用 UI 包的 {@link ScreenRectangle} 描述 Widget 区域。
 */
@SideOnly(Side.CLIENT)
public class WidgetTooltipHolder {

    @Nullable
    private Tooltip tooltip;
    private long delayMs = 0;
    private long displayStartTime;
    private boolean wasDisplayed;

    /**
     * 设置 tooltip 延迟时间（毫秒）。
     */
    public void setDelay(long delayMs) {
        this.delayMs = delayMs;
    }

    /**
     * 设置 tooltip 内容。
     */
    public void set(@Nullable Tooltip tooltip) {
        this.tooltip = tooltip;
    }

    /**
     * 获取当前 tooltip。
     */
    @Nullable
    public Tooltip get() {
        return this.tooltip;
    }

    /**
     * 在渲染阶段调用——根据悬停/焦点状态决定是否显示 tooltip。
     *
     * @param mouseX     鼠标 X
     * @param mouseY     鼠标 Y
     * @param isHovered  是否悬停
     * @param isFocused  是否获得焦点
     * @param widgetRect Widget 的屏幕区域
     */
    public void refreshTooltipForNextRenderPass(
            int mouseX,
            int mouseY,
            boolean isHovered,
            boolean isFocused,
            ScreenRectangle widgetRect
    ) {
        if (tooltip == null) {
            wasDisplayed = false;
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        boolean shouldDisplay = isHovered || (isFocused && mc.currentScreen != null);

        if (shouldDisplay != wasDisplayed) {
            if (shouldDisplay) {
                displayStartTime = System.currentTimeMillis();
            }
            wasDisplayed = shouldDisplay;
        }

        if (shouldDisplay && System.currentTimeMillis() - displayStartTime > delayMs) {
            // 根据焦点/悬停选择定位器
            ClientTooltipPositioner positioner;
            if (!isHovered && isFocused && mc.currentScreen != null) {
                positioner = new BelowOrAboveWidgetTooltipPositioner(widgetRect);
            } else {
                positioner = new MenuTooltipPositioner(widgetRect);
            }

            List<String> lines = tooltip.getLines(mc);
            GuiGraphicsExtractor.getInstance().setTooltipForNextFrame(
                    mc.fontRenderer,
                    lines,
                    tooltip.getComponent(),
                    positioner,
                    mouseX, mouseY,
                    false,
                    tooltip.getStyle()
            );
        }
    }
}
