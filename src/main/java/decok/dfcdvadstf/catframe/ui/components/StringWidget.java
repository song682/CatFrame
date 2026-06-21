package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;

/**
 * <p>
 * 文本显示组件 — 对标高版本 Minecraft 的 {@code StringWidget}。<br>
 * 接受 {@link Text} 对象，只渲染一行文字，目前不响应交互。可直接加入布局系统。
 * </p>
 * <p>
 * String display widget — counterpart of the high-version Minecraft
 * {@code StringWidget}. Accepts {@link Text} objects, renders a single line
 * of text with no interaction currently. Can be added directly to the layout system.
 * </p>
 */
public class StringWidget extends AbstractComponent {

    private Text text;
    private int color;
    private boolean shadow = true;

    /**
     * Creates a StringWidget from a {@link Text} and a colour.
     * <p>从 {@link Text} 和颜色创建。</p>
     */
    public StringWidget(Text text, int color) {
        super(0, 0, Minecraft.getMinecraft().fontRenderer.getStringWidth(text.getString()),
              Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT);
        this.text = text;
        this.color = color;
    }

    /**
     * Convenience constructor from a raw string.
     * <p>从裸字符串创建的便捷构造器。</p>
     */
    public StringWidget(String string, int color) {
        this(Text.literal(string), color);
    }

    public StringWidget setText(Text text) {
        this.text = text;
        this.width = Minecraft.getMinecraft().fontRenderer.getStringWidth(text.getString());
        this.height = Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT;
        return this;
    }

    public Text getText() {
        return text;
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
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        String display = text != null ? text.getString() : "";
        if (shadow) {
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(display, x, y, color);
        } else {
            Minecraft.getMinecraft().fontRenderer.drawString(display, x, y, color);
        }
    }
}
