package decok.dfcdvadstf.catframe.ui.components;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.InputModeTracker;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import decok.dfcdvadstf.catframe.ui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner;
import decok.dfcdvadstf.catframe.ui.screens.inventory.tooltip.ClientTooltipPositioner;
import decok.dfcdvadstf.catframe.ui.screens.inventory.tooltip.MenuTooltipPositioner;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Widget-level tooltip manager — provides delayed display and focus/hover switching for a single widget.
 * <p>
 * Counterpart of 26.1.2 {@code net.minecraft.client.gui.components.WidgetTooltipHolder}.
 * Uses the UI package's {@link ScreenRectangle} to describe the widget's area.
 */
@SideOnly(Side.CLIENT)
public class WidgetTooltipHolder {

    @Nullable
    private Tooltip tooltip;
    private long delayMs = 0;
    private long displayStartTime;
    private boolean wasDisplayed;

    /**
     * Sets the tooltip delay (milliseconds).
     */
    public void setDelay(long delayMs) {
        this.delayMs = delayMs;
    }

    /**
     * Sets the tooltip contents.
     */
    public void set(@Nullable Tooltip tooltip) {
        this.tooltip = tooltip;
    }

    /**
     * Returns the current tooltip.
     */
    @Nullable
    public Tooltip get() {
        return this.tooltip;
    }

    /**
     * Called during the render pass — decides whether to show the tooltip based on hover/focus state.
     *
     * @param mouseX     mouse X
     * @param mouseY     mouse Y
     * @param isHovered  whether hovered
     * @param isFocused  whether focused
     * @param widgetRect the widget's screen area
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
        // Mirrors 26.1.2: hovered, or focused while the last input was the keyboard.
        // Focus left over from a mouse click does not show the tooltip.
        boolean keyboardFocus = isFocused && InputModeTracker.isKeyboardMode();
        boolean shouldDisplay = isHovered || keyboardFocus;

        if (shouldDisplay != wasDisplayed) {
            if (shouldDisplay) {
                displayStartTime = System.currentTimeMillis();
            }
            wasDisplayed = shouldDisplay;
        }

        if (shouldDisplay && System.currentTimeMillis() - displayStartTime > delayMs) {
            // Hover follows the cursor; keyboard focus anchors to the widget (counterpart of
            // 26.1.2 createTooltipPositioner).
            ClientTooltipPositioner positioner;
            if (!isHovered && keyboardFocus) {
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
                    isFocused,
                    tooltip.getStyle()
            );
        }
    }
}
