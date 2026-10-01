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
 * 加载点指示组件 —— 中心点上方 9px 居中绘制标题、下方 9px 居中绘制流动 dots。<br>
 * 对标 26.1.2 {@code net.minecraft.client.gui.components.LoadingDotsWidget}，
 * 保留既有类名（复数拼写）。
 * </p>
 * <p>
 * Loading-dots indicator widget — draws the message centred 9px above the centre
 * point and the flowing dots 9px below it. Counterpart of the high-version
 * {@code LoadingDotsWidget} (keeps the existing pluralised class name).
 * </p>
 */
public class LoadingDotsWidgets extends AbstractComponent.WithInactiveMessage {

    /**
     * Creates the widget sized to its message ({@code 9 * 3} pixels tall).
     * <p>按消息宽度与 {@code 9 * 3} 高度创建组件。</p>
     */
    public LoadingDotsWidgets(Text message) {
        super(0, 0, FontHelper.width(message), 9 * 3, message);
    }

    /**
     * 绘制消息与流动 dots —— 可见性与悬停状态已由 {@link #extractRenderState}
     * 在调用前处理。
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
