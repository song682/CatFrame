package decok.dfcdvadstf.catframe.ui;

import decok.dfcdvadstf.catframe.langguage.LocalizationManager;

import javax.annotation.Nullable;

/**
 * A text wrapper supporting literal strings and namespace-based translatable
 * keys — similar to higher Minecraft versions' {@code Component} system.
 * <p>
 * 文本包装类，支持字面字符串和基于命名空间的可翻译键，
 * 类似高版本 Minecraft 的 {@code Component} 系统。
 * <p>
 * Two translatable formats / 两种可翻译格式:
 * <pre>{@code
 *   // ResourceLocation colon-style
 *   Text.translatable("catframe:menu.paused");
 *
 *   // Explicit domain + key
 *   Text.translatable("catframe", "menu.paused");
 *
 *   // With format arguments
 *   Text.translatable("catframe:item.count", 5, 10);
 *   Text.translatable("catframe", "item.count", 5, 10);
 *
 *   // static final pattern
 *   private static final Text PAUSED_TEXT = Text.translatable("catframe:menu.paused");
 * }</pre>
 */
public class Text {

    @Nullable
    private String domain = "";
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

    private Text(String domain, String key, boolean translatable, Object... args) {
        this.domain = domain;
        this.key = key;
        this.translatable = translatable;
        this.args = args;
    }

    private Text(String domain, String key, boolean translatable, @Nullable Style style, Object... args) {
        this.domain = domain;
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
        return new Text("", text, false);
    }

    /**
     * Creates a literal Text with the given style.
     * <p>使用指定样式创建字面文本。</p>
     */
    public static Text literal(String text, Style style) {
        Text t = new Text("", text, false);
        t.style = style;
        return t;
    }

    /**
     * Creates a translatable Text from a {@code domain:key} string.
     * If no colon is present, treated as a vanilla I18n key (domain=null).
     * <pre>{@code
     *   Text.translatable("catframe:menu.paused");
     *   Text.translatable("catframe:item.count", 5, 10);
     *   Text.translatable("menu.paused");  // vanilla I18n key
     * }</pre>
     */
    public static Text translatable(String resourceKey, Object... args) {
        int sep = resourceKey.indexOf(LocalizationManager.DOMAIN_SEPARATOR);
        if (sep > 0) {
            String domain = resourceKey.substring(0, sep);
            String key = resourceKey.substring(sep + 1);
            return new Text(domain, key, true, args);
        }
        // No colon: treat as vanilla I18n key (domain=null)
        return new Text(null, resourceKey, true, args);
    }

    /**
     * Creates a translatable Text from a {@code domain:key} string with style.
     * If no colon is present, treated as a vanilla I18n key (domain=null).
     * <p>使用样式从 {@code domain:key} 字符串创建可翻译文本。无冒号时作为原版 I18n 键处理。</p>
     */
    public static Text translatable(Style style, String resourceKey, Object... args) {
        int sep = resourceKey.indexOf(LocalizationManager.DOMAIN_SEPARATOR);
        if (sep > 0) {
            String domain = resourceKey.substring(0, sep);
            String key = resourceKey.substring(sep + 1);
            return new Text(domain, key, true, style, args);
        }
        return new Text(null, resourceKey, true, style, args);
    }

    /**
     * Creates a translatable Text with explicit domain and key — similar to
     * {@code new ResourceLocation(domain, path)}.
     * <pre>{@code
     *   Text.translatable("catframe", "menu.paused");
     *   Text.translatable("catframe", "item.count", 5, 10);
     * }</pre>
     */
    public static Text translatable(String domain, String key, Object... args) {
        return new Text(domain, key, true, args);
    }

    /**
     * Creates a translatable Text with explicit domain, key, and style.
     * <p>使用显式域名、键和样式创建可翻译文本。</p>
     */
    public static Text translatable(String domain, String key, Style style, Object... args) {
        return new Text(domain, key, true, style, args);
    }

    // ──── Instance: setTranslatable ────

    /**
     * Marks this Text as translatable using a {@code domain:key} string.
     * <pre>{@code
     *   text.setTranslatable("catframe:menu.paused");
     *   text.setTranslatable("catframe:item.count", 5, 10);
     * }</pre>
     */
    public void setTranslatable(String resourceKey, Object... args) {
        int sep = resourceKey.indexOf(LocalizationManager.DOMAIN_SEPARATOR);
        if (sep <= 0) {
            throw new IllegalArgumentException(
                    "Translatable key must contain domain separator ':', got: " + resourceKey);
        }
        this.domain = resourceKey.substring(0, sep);
        this.key = resourceKey.substring(sep + 1);
        this.translatable = true;
        this.args = args;
        LocalizationManager.KeyTracking.mark(domain, key);
    }

    /**
     * Marks this Text as translatable with explicit domain and key.
     * <pre>{@code
     *   text.setTranslatable("catframe", "menu.paused");
     *   text.setTranslatable("catframe", "item.count", 5, 10);
     * }</pre>
     */
    public void setTranslatable(String domain, String key, Object... args) {
        this.domain = domain;
        this.key = key;
        this.translatable = true;
        this.args = args;
        LocalizationManager.KeyTracking.mark(domain, key);
    }

    // ──── Instance: setLiteral ────

    /**
     * Sets this Text to a literal (non-translatable) string.
     * <p>
     * 将此 Text 设置为字面（不可翻译）字符串。
     */
    public void setLiteral(String text) {
        this.domain = "";
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
            return LocalizationManager.Translation.translate(domain, key, args);
        }
        return key;
    }

    /**
     * Returns the domain (empty for literal texts). / 返回域名（字面文本为空）。
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Returns the raw key or literal text. / 返回原始键或字面文本。
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the full resource key in {@code domain:key} format,
     * or just the literal text if not translatable.
     * <p>
     * 返回 {@code domain:key} 格式的完整资源键，若非可翻译文本则仅返回字面文本。
     */
    public String getRaw() {
        return translatable ? (domain + LocalizationManager.DOMAIN_SEPARATOR + key) : key;
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
     * Translates a {@code domain:key} string directly, returning the translated string.
     * <p>直接翻译 {@code domain:key} 字符串，返回翻译结果。</p>
     *
     * <pre>{@code
     *   Text.translatableString("catframe:gui.no");
     *   Text.translatableString("catframe:item.count", 5, 10);
     * }</pre>
     */
    public static String translatableString(String resourceKey, Object... args) {
        int sep = resourceKey.indexOf(LocalizationManager.DOMAIN_SEPARATOR);
        if (sep <= 0) {
            return LocalizationManager.Translation.translate(resourceKey, args);
        }
        String domain = resourceKey.substring(0, sep);
        String key = resourceKey.substring(sep + 1);
        return LocalizationManager.Translation.translate(domain, key, args);
    }

    /**
     * Translates a domain:key pair directly, returning the translated string.
     * <p>直接翻译 domain:key 对，返回翻译后的字符串。</p>
     *
     * <pre>{@code
     *   Text.translatableString("catframe", "gui.no");
     *   Text.translatableString("catframe", "item.count", 5, 10);
     * }</pre>
     */
    public static String translatableString(String domain, String key, Object... args) {
        return LocalizationManager.Translation.translate(domain, key, args);
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
        Text result = new Text(this.domain, this.key, this.translatable, this.args);
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
        Text result = new Text(this.domain, this.key, this.translatable, this.args);
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
                && domain.equals(other.domain)
                && key.equals(other.key)
                && java.util.Arrays.equals(args, other.args)
                && java.util.Objects.equals(style, other.style);
    }

    @Override
    public int hashCode() {
        int result = domain.hashCode();
        result = 31 * result + key.hashCode();
        result = 31 * result + (translatable ? 1 : 0);
        result = 31 * result + java.util.Arrays.hashCode(args);
        result = 31 * result + (style != null ? style.hashCode() : 0);
        return result;
    }
}
