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
     * Draw styled text at the given position. The whole Text tree is rendered with
     * legacy {@code §} codes resolved per node (nested styles no longer get lost);
     * the {@code style} parameter acts as a base style the tree renders on top of.
     * <p>在指定位置绘制带样式的文本。整棵 Text 树按节点解析旧版 {@code §} 格式码渲染
     * （嵌套样式不再丢失）；{@code style} 参数作为整树渲染所依托的基础样式。</p>
     *
     * @param text  the Text to draw / 要绘制的文本
     * @param x     X position / X 坐标
     * @param y     Y position / Y 坐标
     * @param style base style (may be null) / 基础样式（可为 null）
     * @return the width of the drawn text / 绘制的文本宽度
     */
    public static int draw(Text text, int x, int y, Style style) {
        if (text == null) return 0;

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // The parameter acts as a base style the Text tree renders on top of;
        // every node's own style wins over inherited values (see Text#getFormattedString).
        // 参数作为 Text 树渲染所依托的基础样式；每个节点自身的样式优先于继承值
        // （见 Text#getFormattedString）。
        Style baseStyle = (style != null) ? style : Style.EMPTY;
        String str = text.getFormattedString(baseStyle);
        if (str.isEmpty()) return 0;

        // The colour parameter only applies until the first § colour code inside
        // the string; take it from the root node's effective colour.
        // 颜色参数只在字符串内首个 § 颜色码之前生效；取根节点的有效颜色。
        Style rootStyle = (text.getStyle() != null) ? text.getStyle().applyTo(baseStyle) : baseStyle;
        int color = resolveColor(rootStyle, 0xFFFFFF);

        return font.drawStringWithShadow(str, x, y, color);
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
    public static int drawTranslatable(String key, int x, int y, Style style, Object... args) {
        return draw(Text.translatable(style, key, args), x, y, null);
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
