package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * <p>
 * Font rendering helper — provides styled text rendering methods.<br>
 * Internally delegates to {@link FontRenderer} while applying {@link Style}
 * colour and formatting.
 * </p>
 */
public final class FontHelper {

    private FontHelper() {
    }

    /**
     * Draw styled text at the given position. The whole Text tree is rendered with
     * legacy {@code §} codes resolved per node (nested styles no longer get lost);
     * the {@code style} parameter acts as a base style the tree renders on top of.
     *
     * @param text  the Text to draw
     * @param x     X position
     * @param y     Y position
     * @param style base style (may be null)
     * @return the width of the drawn text
     */
    public static int draw(Text text, int x, int y, Style style) {
        if (text == null) return 0;

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // The parameter acts as a base style the Text tree renders on top of;
        // every node's own style wins over inherited values (see Text#getFormattedString).
        Style baseStyle = (style != null) ? style : Style.EMPTY;
        String str = text.getFormattedString(baseStyle);
        if (str.isEmpty()) return 0;

        // The color parameter only applies until the first § color code inside
        // the string; take it from the root node's effective color.
        Style rootStyle = (text.getStyle() != null) ? text.getStyle().applyTo(baseStyle) : baseStyle;
        int color = resolveColor(rootStyle, 0xFFFFFF);

        return font.drawStringWithShadow(str, x, y, color);
    }

    /**
     * Draw styled text with the Text's own style.
     */
    public static int draw(Text text, int x, int y) {
        return draw(text, x, y, null);
    }

    /**
     * Draw a plain string with a style.
     */
    public static int draw(String text, int x, int y, Style style) {
        return draw(Text.literal(text, style), x, y, null);
    }

    /**
     * Draw a translatable string with style.
     */
    public static int drawTranslatable(String key, int x, int y, Style style, Object... args) {
        return draw(Text.translatable(style, key, args), x, y, null);
    }

    /**
     * Get the width of a Text when rendered.
     */
    public static int width(Text text) {
        if (text == null) return 0;
        return Minecraft.getMinecraft().fontRenderer.getStringWidth(text.getString());
    }

    /**
     * Resolve the ARGB color from a style, falling back to a default.
     */
    private static int resolveColor(Style style, int defaultColor) {
        if (style == null) return defaultColor;
        if (style.getColor() != null) {
            return style.getColor().getRgb() | 0xFF000000;
        }
        return defaultColor;
    }
}
