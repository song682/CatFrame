package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * <p>
 * 字体渲染辅助层 —— 提供带样式的文本绘制方法。<br>
 * 内部委托给 {@link FontRenderer}，同时应用 {@link Style} 的颜色和格式。
 * </p>
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
     * Draw styled text at the given position.
     * <p>在指定位置绘制带样式的文本。</p>
     *
     * @param text  the Text to draw / 要绘制的文本
     * @param x     X position / X 坐标
     * @param y     Y position / Y 坐标
     * @param style the style to apply (may be null) / 要应用的样式（可为 null）
     * @return the width of the drawn text / 绘制的文本宽度
     */
    public static int draw(Text text, int x, int y, Style style) {
        if (text == null) return 0;
        String str = text.getString();
        if (str == null || str.isEmpty()) return 0;

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Resolve effective style: text's own style overridden by parameter
        Style effectiveStyle = style;
        if (text.getStyle() != null) {
            effectiveStyle = (style != null) ? style.applyTo(text.getStyle()) : text.getStyle();
        }

        int color = resolveColor(effectiveStyle, 0xFFFFFF);
        boolean shadow = true;

        // Apply formatting via legacy colour codes
        StringBuilder formatted = new StringBuilder();
        if (effectiveStyle != null) {
            if (effectiveStyle.isBold()) formatted.append("\u00a7l");
            if (effectiveStyle.isItalic()) formatted.append("\u00a7o");
            if (effectiveStyle.isUnderlined()) formatted.append("\u00a7n");
            if (effectiveStyle.isStrikethrough()) formatted.append("\u00a7m");
            if (effectiveStyle.isObfuscated()) formatted.append("\u00a7k");
            if (effectiveStyle.getColor() != null) {
                // Use the colour value directly through the color parameter
                color = effectiveStyle.getColor().getRgb() | 0xFF000000;
            }
        }
        formatted.append(str);

        if (shadow) {
            return font.drawStringWithShadow(formatted.toString(), x, y, color);
        } else {
            return font.drawString(formatted.toString(), x, y, color);
        }
    }

    /**
     * Draw styled text with the Text's own style.
     * <p>使用 Text 自身的样式绘制文本。</p>
     */
    public static int draw(Text text, int x, int y) {
        return draw(text, x, y, null);
    }

    /**
     * Draw a plain string with a style.
     * <p>使用样式绘制纯文本字符串。</p>
     */
    public static int draw(String text, int x, int y, Style style) {
        return draw(Text.literal(text, style), x, y, null);
    }

    /**
     * Draw a translatable string with style.
     * <p>使用样式绘制可翻译字符串。</p>
     */
    public static int drawTranslatable(String domain, String key, int x, int y, Style style, Object... args) {
        return draw(Text.translatable(domain, key, style, args), x, y, null);
    }

    /**
     * Draw a translatable string with style (colon-separated key).
     * <p>使用样式绘制可翻译字符串（冒号分隔键）。</p>
     */
    public static int drawTranslatable(Style style, String resourceKey, int x, int y, Object... args) {
        return draw(Text.translatable(style, resourceKey, args), x, y, null);
    }

    /**
     * Get the width of a Text when rendered.
     * <p>获取文本渲染时的宽度。</p>
     */
    public static int width(Text text) {
        if (text == null) return 0;
        return Minecraft.getMinecraft().fontRenderer.getStringWidth(text.getString());
    }

    /**
     * Resolve the ARGB colour from a style, falling back to a default.
     * <p>从样式解析 ARGB 颜色，默认回退。</p>
     */
    private static int resolveColor(Style style, int defaultColor) {
        if (style == null) return defaultColor;
        if (style.getColor() != null) {
            return style.getColor().getRgb() | 0xFF000000;
        }
        return defaultColor;
    }
}
