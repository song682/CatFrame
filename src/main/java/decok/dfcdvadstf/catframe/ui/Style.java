package decok.dfcdvadstf.catframe.ui;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * <p>
 * 文本样式 —— 控制文本的颜色、格式化、交互事件等。<br>
 * 对标高版本 Minecraft 的 {@code net.minecraft.network.chat.Style}。
 * </p>
 * <p>
 * Text style — controls text colour, formatting, interaction events, etc.<br>
 * Counterpart of the high-version {@code net.minecraft.network.chat.Style}.
 * </p>
 */
public final class Style {

    /** Empty / default style. / 空/默认样式。 */
    public static final Style EMPTY = new Style(null, null, null, null, null, null, null, null, null, null, null);

    // 16 legacy colour index -> actual RGB
    private static final int[] LEGACY_COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
        0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    // ──── Fields ────

    @Nullable
    private final TextColor color;
    @Nullable
    private final Integer shadowColor;
    @Nullable
    private final Boolean bold;
    @Nullable
    private final Boolean italic;
    @Nullable
    private final Boolean underlined;
    @Nullable
    private final Boolean strikethrough;
    @Nullable
    private final Boolean obfuscated;
    @Nullable
    private final ClickEvent clickEvent;
    @Nullable
    private final HoverEvent hoverEvent;
    @Nullable
    private final String insertion;
    @Nullable
    private final ResourceLocation font;

    // ──── Private constructor ────

    private Style(
        @Nullable TextColor color,
        @Nullable Integer shadowColor,
        @Nullable Boolean bold,
        @Nullable Boolean italic,
        @Nullable Boolean underlined,
        @Nullable Boolean strikethrough,
        @Nullable Boolean obfuscated,
        @Nullable ClickEvent clickEvent,
        @Nullable HoverEvent hoverEvent,
        @Nullable String insertion,
        @Nullable ResourceLocation font
    ) {
        this.color = color;
        this.shadowColor = shadowColor;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
        this.clickEvent = clickEvent;
        this.hoverEvent = hoverEvent;
        this.insertion = insertion;
        this.font = font;
    }

    /**
     * Factory: returns EMPTY if all fields are null, otherwise a new Style.
     * <p>工厂方法：如果所有字段为 null 则返回 EMPTY，否则返回新 Style。</p>
     */
    private static Style create(
        @Nullable TextColor color,
        @Nullable Integer shadowColor,
        @Nullable Boolean bold,
        @Nullable Boolean italic,
        @Nullable Boolean underlined,
        @Nullable Boolean strikethrough,
        @Nullable Boolean obfuscated,
        @Nullable ClickEvent clickEvent,
        @Nullable HoverEvent hoverEvent,
        @Nullable String insertion,
        @Nullable ResourceLocation font
    ) {
        Style result = new Style(color, shadowColor, bold, italic, underlined,
            strikethrough, obfuscated, clickEvent, hoverEvent, insertion, font);
        return result.equals(EMPTY) ? EMPTY : result;
    }

    // ──── Getters ────

    @Nullable
    public TextColor getColor() {
        return color;
    }

    @Nullable
    public Integer getShadowColor() {
        return shadowColor;
    }

    public boolean isBold() {
        return bold == Boolean.TRUE;
    }

    public boolean isItalic() {
        return italic == Boolean.TRUE;
    }

    public boolean isStrikethrough() {
        return strikethrough == Boolean.TRUE;
    }

    public boolean isUnderlined() {
        return underlined == Boolean.TRUE;
    }

    public boolean isObfuscated() {
        return obfuscated == Boolean.TRUE;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    @Nullable
    public ClickEvent getClickEvent() {
        return clickEvent;
    }

    @Nullable
    public HoverEvent getHoverEvent() {
        return hoverEvent;
    }

    @Nullable
    public String getInsertion() {
        return insertion;
    }

    @Nullable
    public ResourceLocation getFont() {
        return font;
    }

    // ──── withX() helper ────

    private static <T> Style checkEmptyAfterChange(Style newStyle, @Nullable T previous, @Nullable T next) {
        return previous != null && next == null && newStyle.equals(EMPTY) ? EMPTY : newStyle;
    }

    // ──── withX() methods ────

    public Style withColor(@Nullable TextColor color) {
        return Objects.equals(this.color, color)
            ? this
            : checkEmptyAfterChange(
            create(color, this.shadowColor, this.bold, this.italic, this.underlined,
                this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font),
            this.color, color
        );
    }

    public Style withColor(int rgb) {
        return withColor(TextColor.fromRgb(rgb));
    }

    public Style withShadowColor(@Nullable Integer shadowColor) {
        return Objects.equals(this.shadowColor, shadowColor)
            ? this
            : checkEmptyAfterChange(
            create(this.color, shadowColor, this.bold, this.italic, this.underlined,
                this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font),
            this.shadowColor, shadowColor
        );
    }

    public Style withoutShadow() {
        return withShadowColor(null);
    }

    public Style withBold(@Nullable Boolean bold) {
        return Objects.equals(this.bold, bold)
            ? this
            : checkEmptyAfterChange(
            create(this.color, this.shadowColor, bold, this.italic, this.underlined,
                this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font),
            this.bold, bold
        );
    }

    public Style withItalic(@Nullable Boolean italic) {
        return Objects.equals(this.italic, italic)
            ? this
            : checkEmptyAfterChange(
            create(this.color, this.shadowColor, this.bold, italic, this.underlined,
                this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font),
            this.italic, italic
        );
    }

    public Style withUnderlined(@Nullable Boolean underlined) {
        return Objects.equals(this.underlined, underlined)
            ? this
            : checkEmptyAfterChange(
            create(this.color, this.shadowColor, this.bold, this.italic, underlined,
                this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font),
            this.underlined, underlined
        );
    }

    public Style withStrikethrough(@Nullable Boolean strikethrough) {
        return Objects.equals(this.strikethrough, strikethrough)
            ? this
            : checkEmptyAfterChange(
            create(this.color, this.shadowColor, this.bold, this.italic, this.underlined,
                strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font),
            this.strikethrough, strikethrough
        );
    }

    public Style withObfuscated(@Nullable Boolean obfuscated) {
        return Objects.equals(this.obfuscated, obfuscated)
            ? this
            : checkEmptyAfterChange(
            create(this.color, this.shadowColor, this.bold, this.italic, this.underlined,
                this.strikethrough, obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font),
            this.obfuscated, obfuscated
        );
    }

    public Style withClickEvent(@Nullable ClickEvent clickEvent) {
        return Objects.equals(this.clickEvent, clickEvent)
            ? this
            : checkEmptyAfterChange(
            create(this.color, this.shadowColor, this.bold, this.italic, this.underlined,
                this.strikethrough, this.obfuscated, clickEvent, this.hoverEvent, this.insertion, this.font),
            this.clickEvent, clickEvent
        );
    }

    public Style withHoverEvent(@Nullable HoverEvent hoverEvent) {
        return Objects.equals(this.hoverEvent, hoverEvent)
            ? this
            : checkEmptyAfterChange(
            create(this.color, this.shadowColor, this.bold, this.italic, this.underlined,
                this.strikethrough, this.obfuscated, this.clickEvent, hoverEvent, this.insertion, this.font),
            this.hoverEvent, hoverEvent
        );
    }

    public Style withInsertion(@Nullable String insertion) {
        return Objects.equals(this.insertion, insertion)
            ? this
            : checkEmptyAfterChange(
            create(this.color, this.shadowColor, this.bold, this.italic, this.underlined,
                this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, insertion, this.font),
            this.insertion, insertion
        );
    }

    public Style withFont(@Nullable ResourceLocation font) {
        return Objects.equals(this.font, font)
            ? this
            : checkEmptyAfterChange(
            create(this.color, this.shadowColor, this.bold, this.italic, this.underlined,
                this.strikethrough, this.obfuscated, this.clickEvent, this.hoverEvent, this.insertion, font),
            this.font, font
        );
    }

    // ──── applyFormat ────

    /**
     * Apply a single formatting code (colour or style flag).
     * <p>应用单个格式化代码（颜色或样式标记）。</p>
     */
    public Style applyFormat(TextFormat format) {
        TextColor color = this.color;
        Boolean bold = this.bold;
        Boolean italic = this.italic;
        Boolean strikethrough = this.strikethrough;
        Boolean underlined = this.underlined;
        Boolean obfuscated = this.obfuscated;

        switch (format) {
            case OBFUSCATED: obfuscated = true; break;
            case BOLD: bold = true; break;
            case STRIKETHROUGH: strikethrough = true; break;
            case UNDERLINE: underlined = true; break;
            case ITALIC: italic = true; break;
            case RESET: return EMPTY;
            default:
                if (format.getColorCode() >= 0) {
                    color = TextColor.fromLegacyFormat(format);
                }
                break;
        }

        return create(color, this.shadowColor, bold, italic, underlined,
            strikethrough, obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    /**
     * Apply a legacy formatting code (reset style flags for colour changes).
     * <p>应用旧版格式化代码（颜色变更时重置样式标记）。</p>
     */
    public Style applyLegacyFormat(TextFormat format) {
        TextColor color = this.color;
        Boolean bold = this.bold;
        Boolean italic = this.italic;
        Boolean strikethrough = this.strikethrough;
        Boolean underlined = this.underlined;
        Boolean obfuscated = this.obfuscated;

        switch (format) {
            case OBFUSCATED: obfuscated = true; break;
            case BOLD: bold = true; break;
            case STRIKETHROUGH: strikethrough = true; break;
            case UNDERLINE: underlined = true; break;
            case ITALIC: italic = true; break;
            case RESET: return EMPTY;
            default:
                obfuscated = false;
                bold = false;
                strikethrough = false;
                underlined = false;
                italic = false;
                if (format.getColorCode() >= 0) {
                    color = TextColor.fromLegacyFormat(format);
                }
                break;
        }

        return create(color, this.shadowColor, bold, italic, underlined,
            strikethrough, obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    /**
     * Apply multiple formatting codes.
     * <p>应用多个格式化代码。</p>
     */
    public Style applyFormats(TextFormat... formats) {
        TextColor color = this.color;
        Boolean bold = this.bold;
        Boolean italic = this.italic;
        Boolean strikethrough = this.strikethrough;
        Boolean underlined = this.underlined;
        Boolean obfuscated = this.obfuscated;

        for (TextFormat format : formats) {
            switch (format) {
                case OBFUSCATED: obfuscated = true; break;
                case BOLD: bold = true; break;
                case STRIKETHROUGH: strikethrough = true; break;
                case UNDERLINE: underlined = true; break;
                case ITALIC: italic = true; break;
                case RESET: return EMPTY;
                default:
                    if (format.getColorCode() >= 0) {
                        color = TextColor.fromLegacyFormat(format);
                    }
                    break;
            }
        }

        return create(color, this.shadowColor, bold, italic, underlined,
            strikethrough, obfuscated, this.clickEvent, this.hoverEvent, this.insertion, this.font);
    }

    /**
     * Merge this style onto another — non-null fields in this override corresponding
     * fields in the other style.
     * <p>将此样式合并到另一个样式上 —— 此样式的非 null 字段覆盖另一样式对应字段。</p>
     */
    public Style applyTo(Style other) {
        if (this == EMPTY) return other;
        if (other == EMPTY) return this;

        return create(
            this.color != null ? this.color : other.color,
            this.shadowColor != null ? this.shadowColor : other.shadowColor,
            this.bold != null ? this.bold : other.bold,
            this.italic != null ? this.italic : other.italic,
            this.underlined != null ? this.underlined : other.underlined,
            this.strikethrough != null ? this.strikethrough : other.strikethrough,
            this.obfuscated != null ? this.obfuscated : other.obfuscated,
            this.clickEvent != null ? this.clickEvent : other.clickEvent,
            this.hoverEvent != null ? this.hoverEvent : other.hoverEvent,
            this.insertion != null ? this.insertion : other.insertion,
            this.font != null ? this.font : other.font
        );
    }

    // ──── Object overrides ────

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        appendValue(sb, "color", this.color);
        appendValue(sb, "shadowColor", this.shadowColor);
        appendFlag(sb, "bold", this.bold);
        appendFlag(sb, "italic", this.italic);
        appendFlag(sb, "underlined", this.underlined);
        appendFlag(sb, "strikethrough", this.strikethrough);
        appendFlag(sb, "obfuscated", this.obfuscated);
        appendValue(sb, "clickEvent", this.clickEvent);
        appendValue(sb, "hoverEvent", this.hoverEvent);
        appendValue(sb, "insertion", this.insertion);
        appendValue(sb, "font", this.font);
        sb.append('}');
        return sb.toString();
    }

    private static void appendValue(StringBuilder sb, String name, @Nullable Object value) {
        if (value != null) {
            if (sb.length() > 1) sb.append(',');
            sb.append(name).append('=').append(value);
        }
    }

    private static void appendFlag(StringBuilder sb, String name, @Nullable Boolean value) {
        if (value != null) {
            if (sb.length() > 1) sb.append(',');
            if (!value) sb.append('!');
            sb.append(name);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Style)) return false;
        Style style = (Style) o;
        return Objects.equals(bold, style.bold)
            && Objects.equals(color, style.color)
            && Objects.equals(shadowColor, style.shadowColor)
            && Objects.equals(italic, style.italic)
            && Objects.equals(obfuscated, style.obfuscated)
            && Objects.equals(strikethrough, style.strikethrough)
            && Objects.equals(underlined, style.underlined)
            && Objects.equals(clickEvent, style.clickEvent)
            && Objects.equals(hoverEvent, style.hoverEvent)
            && Objects.equals(insertion, style.insertion)
            && Objects.equals(font, style.font);
    }

    @Override
    public int hashCode() {
        return Objects.hash(color, shadowColor, bold, italic, underlined,
            strikethrough, obfuscated, clickEvent, hoverEvent, insertion);
    }

    // ──── Inner classes ────

    /**
     * Text colour — holds an ARGB integer and optional legacy format mapping.
     * <p>文本颜色 —— 持有 ARGB 整数和可选的旧版格式映射。</p>
     */
    public static final class TextColor {
        private final int rgb;
        @Nullable
        private final String name;

        private TextColor(int rgb, @Nullable String name) {
            this.rgb = rgb;
            this.name = name;
        }

        public static TextColor fromRgb(int rgb) {
            return new TextColor(rgb, null);
        }

        public static TextColor fromLegacyFormat(TextFormat format) {
            int idx = format.getColorCode();
            if (idx < 0 || idx >= LEGACY_COLORS.length) {
                return new TextColor(0xFFFFFF, null);
            }
            return new TextColor(LEGACY_COLORS[idx], format.getFriendlyName());
        }

        public int getRgb() {
            return rgb;
        }

        @Nullable
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TextColor)) return false;
            TextColor textColor = (TextColor) o;
            return rgb == textColor.rgb;
        }

        @Override
        public int hashCode() {
            return rgb;
        }

        @Override
        public String toString() {
            return name != null ? name : String.format("#%06X", rgb);
        }
    }

    /**
     * Click event — action to perform when text is clicked.
     * <p>点击事件 —— 点击文本时执行的操作。</p>
     */
    public static final class ClickEvent {
        private final Action action;
        private final String value;

        public ClickEvent(Action action, String value) {
            this.action = action;
            this.value = value;
        }

        public Action getAction() {
            return action;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClickEvent)) return false;
            ClickEvent that = (ClickEvent) o;
            return action == that.action && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(action, value);
        }

        @Override
        public String toString() {
            return action.name().toLowerCase() + ":" + value;
        }

        public enum Action {
            OPEN_URL,
            OPEN_FILE,
            RUN_COMMAND,
            SUGGEST_COMMAND,
            CHANGE_PAGE,
            COPY_TO_CLIPBOARD
        }
    }

    /**
     * Hover event — tooltip or entity/item preview when hovering text.
     * <p>悬停事件 —— 悬停文本时的提示或实体/物品预览。</p>
     */
    public static final class HoverEvent {
        private final Action action;
        private final Object value;

        public HoverEvent(Action action, Object value) {
            this.action = action;
            this.value = value;
        }

        public Action getAction() {
            return action;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HoverEvent)) return false;
            HoverEvent that = (HoverEvent) o;
            return action == that.action && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(action, value);
        }

        @Override
        public String toString() {
            return action.name().toLowerCase() + ":" + value;
        }

        public enum Action {
            SHOW_TEXT,
            SHOW_ITEM,
            SHOW_ENTITY
        }
    }

    // ──── Serializer ────

    /**
     * Provides Codec-like serialization for this Style.
     * <p>为该样式提供类似 Codec 的序列化支持。</p>
     */
    public static final class Serializer {

        private Serializer() {
        }

        /**
         * Serialize this style to a simple string representation.
         * <p>将此样式序列化为简单的字符串表示。</p>
         */
        public static String serialize(Style style) {
            return style != null ? style.toString() : "{}";
        }

        /**
         * Parse a string representation back to a Style.
         * <p>将字符串表示解析为 Style。</p>
         */
        public static Style deserialize(String str) {
            return Style.EMPTY;
        }
    }

    // ──── Legacy TextFormat enum (subset) ────

    /**
     * Legacy colour/formatting codes.
     * <p>旧版颜色/格式化代码。</p>
     */
    public enum TextFormat {
        BLACK(0),
        DARK_BLUE(1),
        DARK_GREEN(2),
        DARK_AQUA(3),
        DARK_RED(4),
        DARK_PURPLE(5),
        GOLD(6),
        GRAY(7),
        DARK_GRAY(8),
        BLUE(9),
        GREEN(10),
        AQUA(11),
        RED(12),
        LIGHT_PURPLE(13),
        YELLOW(14),
        WHITE(15),
        OBFUSCATED(-1),
        BOLD(-1),
        STRIKETHROUGH(-1),
        UNDERLINE(-1),
        ITALIC(-1),
        RESET(-1);

        private final int colorCode;

        TextFormat(int colorCode) {
            this.colorCode = colorCode;
        }

        public int getColorCode() {
            return colorCode;
        }

        public String getFriendlyName() {
            return name().toLowerCase();
        }
    }
}
