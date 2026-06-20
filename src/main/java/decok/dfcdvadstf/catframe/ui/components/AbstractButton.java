package decok.dfcdvadstf.catframe.ui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

/**
 * <p>
 * 按钮抽象基类 —— 提供悬停检测、按下状态和音效播放等通用按钮功能。<br>
 * 对标高版本 Minecraft 的 {@code AbstractButton}。
 * </p>
 * <p>
 * Abstract button base — provides hover detection, press state, and sound playback.<br>
 * Counterpart of the high-version Minecraft {@code AbstractButton}.
 * </p>
 */
public abstract class AbstractButton extends AbstractComponent {

    protected boolean wasHovered;
    protected long lastClickTime;

    /** Default button text colour / 默认按钮文本颜色 */
    protected static final int TEXT_COLOR_ENABLED = 0xFFFFFF;
    protected static final int TEXT_COLOR_DISABLED = 0x888888;
    protected static final int TEXT_COLOR_HOVER = 0xFFFF55;

    public AbstractButton() {
    }

    public AbstractButton(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    /**
     * Called when the button is pressed.
     * <p>按钮被按下时调用。</p>
     */
    public abstract void onPress();

    /**
     * Plays the button click sound.
     * <p>播放按钮点击音效。</p>
     */
    protected void playPressSound() {
        Minecraft.getMinecraft().getSoundHandler().playSound(
            PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    /**
     * Draw the button background. Override for custom styling.
     * <p>绘制按钮背景。重写以自定义样式。</p>
     */
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        // Default: draw a simple coloured rectangle
        int bgColor;
        if (!active) {
            bgColor = 0xFF333333;
        } else if (isHovered) {
            bgColor = 0xFF555555;
        } else {
            bgColor = 0xFF444444;
        }

        drawRect(x, y, x + width, y + height, bgColor);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        updateHoverState(mouseX, mouseY);
        renderBackground(mouseX, mouseY, partialTicks);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!active || !visible || mouseButton != 0) return;
        if (isMouseOver(mouseX, mouseY)) {
            lastClickTime = System.currentTimeMillis();
            playPressSound();
            onPress();
        }
    }

    /**
     * Helper: draw a filled rectangle.
     * <p>辅助：绘制填充矩形。</p>
     */
    protected static void drawRect(int left, int top, int right, int bottom, int color) {
        GuiDrawing.drawRect(left, top, right, bottom, color);
    }
}
