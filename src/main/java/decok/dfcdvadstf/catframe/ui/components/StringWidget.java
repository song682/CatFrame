package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;

/**
 * <p>
 * String display widget — counterpart of the high-version Minecraft
 * {@code StringWidget}. Extends {@link AbstractStringWidget}; accepts
 * {@link Text} objects and renders a single line of text with no interaction currently.
 * Can be added directly to the layout system.
 * </p>
 */
public class StringWidget extends AbstractStringWidget {

    private int color;
    private boolean shadow = true;

    /**
     * Creates a StringWidget from a {@link Text} and a colour.
     */
    public StringWidget(Text text, int color) {
        super(0, 0, Minecraft.getMinecraft().fontRenderer.getStringWidth(text.getString()),
                Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT,
                text, Minecraft.getMinecraft().fontRenderer);
        this.color = color;
    }

    /**
     * Convenience constructor from a raw string.
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
