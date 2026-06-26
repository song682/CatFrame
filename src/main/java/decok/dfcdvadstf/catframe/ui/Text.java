package decok.dfcdvadstf.catframe.ui;

import net.minecraft.client.resources.I18n;

import javax.annotation.Nullable;

/**
 * A text wrapper supporting literal strings and translatable keys —
 * similar to higher Minecraft versions' {@code Component} system.
 * <p>
 * 文本包装类，支持字面字符串和可翻译键，
 * 类似高版本 Minecraft 的 {@code Component} 系统。
 * <p>
 * Usage / 用法:
 * <pre>{@code
 *   // Literal
 *   Text.literal("Hello");
 *
 *   // Translatable flat key (via I18n / StatCollector)
 *   Text.translatable("menu.paused");
 *   Text.translatable("item.count", 5, 10);
 * }</pre>
 */
public class Text {

    private String key = "";
    private boolean translatable = false;
    private Object[] args = new Object[0];
    @Nullable
    private Style style;

    // ──── Constructors ────

    /**
     * Creates an empty Text. / 创建一个空的 Text。
     */
    public Text() {
    }

    /**
     * Creates a literal Text. / 创建一个字面 Text。
     */
    public Text(String literal) {
        this.key = literal;
        this.translatable = false;
    }

    private Text(String key, boolean translatable, Object... args) {
        this.key = key;
        this.translatable = translatable;
        this.args = args;
    }

    private Text(String key, boolean translatable, @Nullable Style style, Object... args) {
        this.key = key;
        this.translatable = translatable;
        this.style = style;
        this.args = args;
    }

    // ──── Static factories ────

    /**
     * Creates a literal (non-translatable) Text.
     * <p>
     * 创建一个字面（不可翻译）文本。
     */
    public static Text literal(String text) {
        return new Text(text, false);
    }

    /**
     * Creates a literal Text with the given style.
     * <p>使用指定样式创建字面文本。</p>
     */
    public static Text literal(String text, Style style) {
        Text t = new Text(text, false);
        t.style = style;
        return t;
    }

    /**
     * Creates a translatable Text with a flat key (via {@link I18n#format}).
     * <p>
     * 使用扁平键创建可翻译文本（通过 {@link I18n#format}）。
     * <pre>{@code
     *   Text.translatable("menu.paused");
     *   Text.translatable("item.count", 5, 10);
     * }</pre>
     */
    public static Text translatable(String key, Object... args) {
        return new Text(key, true, args);
    }

    /**
     * Creates a translatable Text with a flat key and style.
     * <p>使用扁平键和样式创建可翻译文本。</p>
     */
    public static Text translatable(@Nullable Style style, String key, Object... args) {
        return new Text(key, true, style, args);
    }

    // ──── Instance: setTranslatable ────

    /**
     * Marks this Text as translatable using a flat key.
     * <p>将此 Text 标记为使用扁平键的可翻译文本。</p>
     */
    public void setTranslatable(String key, Object... args) {
        this.key = key;
        this.translatable = true;
        this.args = args;
    }

    // ──── Instance: setLiteral ────

    /**
     * Sets this Text to a literal (non-translatable) string.
     * <p>
     * 将此 Text 设置为字面（不可翻译）字符串。
     */
    public void setLiteral(String text) {
        this.key = text;
        this.translatable = false;
        this.args = new Object[0];
    }

    // ──── Getters ────

    /**
     * Returns the resolved string — translated if translatable, otherwise literal.
     * <p>
     * 返回解析后的字符串 —— 可翻译则翻译，否则返回字面内容。
     */
    public String getString() {
        if (translatable) {
            return I18n.format(key, args);
        }
        return key;
    }

    /**
     * Returns the raw key or literal text. / 返回原始键或字面文本。
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the raw key (same as {@link #getKey()}).
     * <p>
     * 返回原始键（同 {@link #getKey()}）。
     */
    public String getRaw() {
        return key;
    }

    /**
     * Returns whether this Text is translatable. / 返回此 Text 是否可翻译。
     */
    public boolean isTranslatable() {
        return translatable;
    }

    /**
     * Returns the format arguments. / 返回格式化参数。
     */
    public Object[] getArgs() {
        return args;
    }

    // ──── Convenience static String methods ────

    /**
     * Translates a key directly via {@link I18n#format}, returning the translated string.
     * <p>直接通过 {@link I18n#format} 翻译键，返回翻译后的字符串。</p>
     *
     * <pre>{@code
     *   Text.translatableString("gui.no");
     *   Text.translatableString("item.count", 5, 10);
     * }</pre>
     */
    public static String translatableString(String key, Object... args) {
        return I18n.format(key, args);
    }

    /**
     * Returns the text as-is (identity helper for API consistency).
     * <p>直接返回文本本身（API一致性辅助方法）。</p>
     */
    public static String literalString(String text) {
        return text;
    }

    // ──── Style ────

    /**
     * Returns the style associated with this Text, or {@code null}.
     * <p>返回与此 Text 关联的样式，或 {@code null}。</p>
     */
    @Nullable
    public Style getStyle() {
        return style;
    }

    /**
     * Sets the style for this Text.
     * <p>为此 Text 设置样式。</p>
     */
    public void setStyle(@Nullable Style style) {
        this.style = style;
    }

    /**
     * Returns a new Text with the same content but the specified style applied.
     * <p>返回内容相同但应用了指定样式的新 Text。</p>
     */
    public Text withStyle(Style style) {
        Text result = new Text(this.key, this.translatable, this.args);
        result.style = style;
        return result;
    }

    /**
     * Returns a new Text with the same content but the specified style applied
     * on top of any existing style.
     * <p>返回内容相同但在现有样式之上应用了指定样式的新 Text。</p>
     */
    public Text withStyleApplied(Style style) {
        Style merged = (this.style != null) ? style.applyTo(this.style) : style;
        Text result = new Text(this.key, this.translatable, this.args);
        result.style = merged;
        return result;
    }

    // ──── Object overrides ────

    @Override
    public String toString() {
        return getString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Text)) return false;
        Text other = (Text) o;
        return translatable == other.translatable
                && key.equals(other.key)
                && java.util.Arrays.equals(args, other.args)
                && java.util.Objects.equals(style, other.style);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + (translatable ? 1 : 0);
        result = 31 * result + java.util.Arrays.hashCode(args);
        result = 31 * result + (style != null ? style.hashCode() : 0);
        return result;
    }
}
