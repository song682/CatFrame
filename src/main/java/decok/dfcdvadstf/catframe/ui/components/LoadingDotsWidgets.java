package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.LoadingDotsText;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.events.ComponentPath;
import decok.dfcdvadstf.catframe.ui.navigation.FocusNavigationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import javax.annotation.Nullable;

/**
 * <p>
 * Loading-dots indicator widget — draws the message centred 9px above the centre
 * point and the flowing dots 9px below it. Counterpart of the high-version
 * {@code LoadingDotsWidget} (keeps the existing pluralised class name).
 * </p>
 */
public class LoadingDotsWidgets extends AbstractComponent.WithInactiveMessage {

    /**
     * Creates the widget sized to its message ({@code 9 * 3} pixels tall).
     */
    public LoadingDotsWidgets(Text message) {
        super(0, 0, FontHelper.width(message), 9 * 3, message);
    }

    /**
     * Draws the message and the flowing dots — visibility and hover state
     * are already handled by {@link #extractRenderState} before this is called.
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        int centerX = this.getX() + this.getWidth() / 2;
        int centerY = this.getY() + this.getHeight() / 2;
        Text message = this.getMessage();
        FontHelper.draw(message, centerX - FontHelper.width(message) / 2, centerY - 9);

        String dots = LoadingDotsText.get(Minecraft.getSystemTime());
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        font.drawStringWithShadow(dots, centerX - font.getStringWidth(dots) / 2, centerY + 9, -8355712);
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
        return null;
    }
}
