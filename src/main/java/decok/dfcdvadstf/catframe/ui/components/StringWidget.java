package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;

/**
 * <p>
 * 文本显示组件 — 对标高版本 Minecraft 的 {@code StringWidget}。<br>
 * 继承 {@link AbstractStringWidget}，接受 {@link Text} 对象，只渲染一行文字，
 * 目前不响应交互。可直接加入布局系统。
 * </p>
 * <p>
 * String display widget — counterpart of the high-version Minecraft
 * {@code StringWidget}. Extends {@link AbstractStringWidget}; accepts
 * {@link Text}
 * objects and renders a single line of text with no interaction currently.
 * Can be added directly to the layout system.
 * </p>
 */
public class StringWidget extends AbstractStringWidget {

    private int color;
    private boolean shadow = true;

    /**
     * Creates a StringWidget from a {@link Text} and a colour.
     * <p>
     * 从 {@link Text} 和颜色创建。
     * </p>
     */
    public StringWidget(Text text, int color) {
        super(0, 0, Minecraft.getMinecraft().fontRenderer.getStringWidth(text.getString()),
                Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT,
                text, Minecraft.getMinecraft().fontRenderer);
        this.color = color;
    }

    /**
     * Convenience constructor from a raw string.
     * <p>
     * 从裸字符串创建的便捷构造器。
     * </p>
     */
    public StringWidget(String string, int color) {
        this(Text.literal(string), color);
    }

    public StringWidget setText(Text text) {
        setMessage(text);
        this.height = getFont().FONT_HEIGHT;
        return this;
    }

    public Text getText() {
        return getMessage();
    }

    public StringWidget setColor(int color) {
        this.color = color;
        return this;
    }

    public int getColor() {
        return color;
    }

    public StringWidget setShadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        String display = getMessage() != null ? getMessage().getString() : "";
        if (shadow) {
            getFont().drawStringWithShadow(display, x, y, color);
        } else {
            getFont().drawString(display, x, y, color);
        }
    }
}
