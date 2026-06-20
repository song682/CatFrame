package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * <p>
 * 按钮组件实现。<br>
 * 对标高版本 Minecraft 的 {@code Button}。
 * </p>
 * <p>
 * Button component implementation.<br>
 * Counterpart of the high-version Minecraft {@code Button}.
 * </p>
 */
public class Button extends AbstractButton {

    /** Standard widths / 标准宽度 */
    public static final int SMALL_WIDTH = 120;
    public static final int DEFAULT_WIDTH = 150;
    public static final int BIG_WIDTH = 200;
    public static final int DEFAULT_HEIGHT = 20;

    private Text message;
    private final OnPress onPress;

    protected Button(int x, int y, int width, int height, Text message, OnPress onPress) {
        super(x, y, width, height);
        this.message = message;
        this.onPress = onPress;
    }

    /**
     * Creates a new Button builder.
     * <p>创建新的 Button 构建器。</p>
     */
    public static Builder builder(Text message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    public void setMessage(Text message) {
        this.message = message;
    }

    public Text getMessage() {
        return message;
    }

    @Override
    public void onPress() {
        if (onPress != null) {
            onPress.onPress(this);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        updateHoverState(mouseX, mouseY);
        renderBackground(mouseX, mouseY, partialTicks);

        // Draw the button text
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String text = message != null ? message.getString() : "";
        int textColor;
        if (!active) {
            textColor = TEXT_COLOR_DISABLED;
        } else if (isHovered) {
            textColor = TEXT_COLOR_HOVER;
        } else {
            textColor = TEXT_COLOR_ENABLED;
        }

        int textX = x + (width - font.getStringWidth(text)) / 2;
        int textY = y + (height - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(text, textX, textY, textColor);
    }

    // ──── OnPress callback ────

    @FunctionalInterface
    public interface OnPress {
        void onPress(Button button);
    }

    // ──── Builder ────

    /**
     * Builder for constructing a {@link Button}.
     * <p>用于构建 {@link Button} 的构建器。</p>
     */
    public static class Builder {
        private final Text message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = DEFAULT_WIDTH;
        private int height = DEFAULT_HEIGHT;

        public Builder(Text message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Button build() {
            return new Button(x, y, width, height, message, onPress);
        }
    }
}
