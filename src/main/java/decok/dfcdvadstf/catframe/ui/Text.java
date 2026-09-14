package decok.dfcdvadstf.catframe.ui;

import com.google.gson.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *   // Translatable template key (resolved via StatCollector, so it is side-agnostic)
 *   Text.translatable("menu.paused");
 *   Text.translatable("item.count", 5, 10);
 *
 *   // Translatable with a fallback template when the key is missing
 *   Text.translatableWithFallback("mod.greeting", "Hello, %s!", name);
 * }</pre>
 * <p>
 * Traversal follows the high-version {@code FormattedText#visit} contract: the tree
 * is decomposed into styled segments (a translatable node's template is split at its
 * {@code %s} placeholders so embedded components contribute their own segments), and
 * consumers may stop early via {@link #STOP_ITERATION}.
 * <br>遍历遵循高版本 {@code FormattedText#visit} 契约：整树被拆解为带样式的片段
 * （可翻译节点的模板按其 {@code %s} 占位符拆分，使内嵌组件贡献自己的片段），
 * 消费者可通过 {@link #STOP_ITERATION} 提前终止。
 * </p>
 */
public class Text {

    private String key = "";
    private boolean translatable = false;
    private Object[] args = new Object[0];
    @Nullable
    private Style style;
    /**
     * Fallback template used when this Text is translatable but the key is missing
     * from the active language table; {@code null} means "no fallback" (the raw key
     * is shown, mirroring vanilla {@code ChatComponentTranslation}).
     * <p>当本 Text 可翻译但活动语言表中缺少该键时使用的回退模板；{@code null}
     * 表示无回退（显示原始键，对齐原版 {@code ChatComponentTranslation}）。</p>
     */
    @Nullable
    private String fallback;

    /**
     * Matches one format conversion inside a translation template — plain or
     * indexed (e.g. {@code %s}, {@code %1$s}), and {@code %%}, mirroring the
     * vanilla {@code ChatComponentTranslation#stringVariablePattern}.
     * <p>匹配翻译模板中的单次格式转换 —— 普通或索引形式（如 {@code %s}、
     * {@code %1$s}）以及 {@code %%}，对齐原版
     * {@code ChatComponentTranslation#stringVariablePattern}。</p>
     */
    private static final Pattern FORMAT_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    /**
     * Child components appended after this one — counterpart of the raw JSON text
     * format's {@code extra} array / high-version {@code MutableComponent#getSiblings()}.
     * <p>追加在此节点之后的子组件 —— 对标原始 JSON 文本格式的 {@code extra} 数组 /
     * 高版本 {@code MutableComponent#getSiblings()}。</p>
     */
    private final List<Text> siblings = new ArrayList<Text>();

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

    private Text(String key, boolean translatable, @Nullable Style style,
                 @Nullable String fallback, Object... args) {
        this.key = key;
        this.translatable = translatable;
        this.style = style;
        this.fallback = fallback;
        this.args = args;
    }

    // ──── Static factories ────

    /**
     * Creates a literal (non-translatable) Text.
     * <p>
     * 创建一个字面（不可翻译）文本。
     */
    public static Text literal(String text) {
        return new Text(text, false, null, null);
    }

    /**
     * Creates a literal Text with the given style.
     * <p>使用指定样式创建字面文本。</p>
     */
    public static Text literal(String text, Style style) {
        return new Text(text, false, style, null);
    }

    /**
     * Creates a translatable Text with a template key, resolved via
     * {@link StatCollector#translateToLocalFormatted} — side-agnostic, unlike the
     * client-only {@code I18n}.
     * <p>
     * 使用模板键创建可翻译文本，通过
     * {@link StatCollector#translateToLocalFormatted} 解析 —— 与仅客户端的
     * {@code I18n} 不同，服务端同样可用。
     * <pre>{@code
     *   Text.translatable("menu.paused");
     *   Text.translatable("item.count", 5, 10);
     * }</pre>
     */
    public static Text translatable(String key, Object... args) {
        return new Text(key, true, null, null, args);
    }

    /**
     * Creates a translatable Text with a fallback flat template, used when the key
     * is missing from the active language table.
     * <p>创建带回退扁平模板的可翻译文本，当活动语言表中缺少该键时使用。
     *
     * <pre>{@code
     *   Text.translatableWithFallback("mod.greeting", "Hello, %s!", name);
     * }</pre>
     */
    public static Text translatableWithFallback(String key, @Nullable String fallback, Object... args) {
        return new Text(key, true, null, fallback, args);
    }

    /**
     * Creates a translatable Text with a flat key and style.
     * <p>使用扁平键和样式创建可翻译文本。</p>
     */
    public static Text translatable(@Nullable Style style, String key, Object... args) {
        return new Text(key, true, style, null, args);
    }

    /**
     * Parses a raw JSON text component (the format used by {@code /title}, {@code /tellraw}
     * etc. in modern Minecraft) into a Text tree. Delegates to {@link Serializer#fromJson(String)}.
     * <p>解析原始 JSON 文本组件（高版本 {@code /title}、{@code /tellraw} 等命令使用的格式）
     * 为 Text 树。委托给 {@link Serializer#fromJson(String)}。</p>
     *
     * <pre>{@code
     *   Text.fromJson("\"plain string\"");
     *   Text.fromJson("{\"text\":\"Hi\",\"color\":\"red\",\"bold\":true}");
     *   Text.fromJson("[{\"text\":\"A\"},{\"translate\":\"menu.paused\"}]");
     * }</pre>
     */
    public static Text fromJson(String json) {
        return Serializer.fromJson(json);
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
     * Siblings are resolved and concatenated after this node's own content.
     * <p>
     * 返回解析后的字符串 —— 可翻译则翻译，否则返回字面内容。
     * 子组件依次解析并拼接在本节点内容之后。
     */
    public String getString() {
        StringBuilder sb = new StringBuilder();
        visit((style, contents) -> {
            sb.append(contents);
            return Optional.<Void>empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    /**
     * Resolves this node's own template — the active translation when the key is
     * known, otherwise the fallback, otherwise the raw key (mirrors vanilla
     * {@code ChatComponentTranslation}'s translation lookup).
     * <p>解析本节点自身的模板 —— 键已知时取活动翻译，否则取回退模板，最后取原始键
     * （对齐原版 {@code ChatComponentTranslation} 的翻译查找）。</p>
     */
    private String resolvedTemplate() {
        if (!translatable) {
            return key;
        }
        if (fallback != null && !StatCollector.canTranslate(key)) {
            return fallback;
        }
        return StatCollector.translateToLocal(key);
    }

    /**
     * Formats this node's own content into a single flat string with
     * {@link String#format} semantics — the legacy rendering path. Reproduces the
     * vanilla {@code "Format error: "} prefix when the template and arguments do
     * not match.
     * <p>将本节点自身内容按 {@link String#format} 语义格式化为单个扁平字符串 ——
     * 旧版渲染路径。模板与参数不匹配时复现原版 {@code "Format error: "} 前缀。</p>
     */
    private String ownFlatText() {
        if (!translatable) {
            return key;
        }
        String template = resolvedTemplate();
        try {
            return String.format(template, args);
        } catch (IllegalFormatException e) {
            return "Format error: " + template;
        }
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

    /**
     * Returns the fallback template used when the key is missing from the active
     * language table, or {@code null} if none was set.
     * <p>返回活动语言表中缺少该键时使用的回退模板；未设置时返回 {@code null}。</p>
     */
    @Nullable
    public String getFallback() {
        return fallback;
    }

    // ──── Siblings / 子组件 ────

    /**
     * Appends a sibling component after this one and returns {@code this} for chaining.
     * Counterpart of the high-version {@code MutableComponent#append}.
     * <p>在此节点后追加一个子组件，返回 {@code this} 以便链式调用。
     * 对标高版本 {@code MutableComponent#append}。</p>
     */
    public Text append(Text sibling) {
        if (sibling != null) {
            siblings.add(sibling);
        }
        return this;
    }

    /**
     * Convenience overload appending a literal string sibling.
     * <p>追加字面字符串子组件的便捷重载。</p>
     */
    public Text append(String literal) {
        return append(Text.literal(literal));
    }

    /**
     * Returns an unmodifiable view of the sibling list.
     * <p>返回子组件列表的不可变视图。</p>
     */
    public List<Text> getSiblings() {
        return Collections.unmodifiableList(siblings);
    }

    /**
     * Whether this component has any siblings.
     * <p>此组件是否含有子组件。</p>
     */
    public boolean hasSiblings() {
        return !siblings.isEmpty();
    }

    // ──── Traversal / 遍历 ────

    /**
     * Return value telling a traversal to stop early — counterpart of the
     * high-version {@code FormattedText.STOP_ITERATION}.
     * <p>告知遍历提前终止的返回值 —— 对标高版本 {@code FormattedText.STOP_ITERATION}。</p>
     */
    public static final Optional<Boolean> STOP_ITERATION = Optional.of(Boolean.TRUE);

    /**
     * Consumer of plain contents during a traversal.
     * <p>遍历过程中的纯内容消费者。</p>
     */
    public interface ContentConsumer<T> {
        Optional<T> accept(String contents);
    }

    /**
     * Consumer of styled segments during a traversal — receives each segment's
     * <em>effective</em> style (own values merged onto the inherited chain).
     * <p>遍历过程中的带样式片段消费者 —— 接收每个片段的<em>有效</em>样式
     * （自身值合并到继承链之上）。</p>
     */
    public interface StyledContentConsumer<T> {
        Optional<T> accept(Style style, String contents);
    }

    /**
     * Visits the whole tree's plain contents (styles ignored), in render order.
     * <p>按渲染顺序遍历整棵树的纯内容（忽略样式）。</p>
     */
    public <T> Optional<T> visit(ContentConsumer<T> consumer) {
        return visit((style, contents) -> consumer.accept(contents), Style.EMPTY);
    }

    /**
     * Visits the tree in render order, handing each segment its effective style.
     * A translatable node's template is decomposed around its {@code %s} placeholders
     * first, so embedded components contribute their own styled segments. Returning
     * {@link #STOP_ITERATION} from the consumer aborts the traversal.
     * <p>按渲染顺序遍历整棵树，为每个片段传入其有效样式。可翻译节点的模板先按其
     * {@code %s} 占位符拆解，使内嵌组件贡献各自的带样式片段。消费者返回
     * {@link #STOP_ITERATION} 即中止遍历。</p>
     *
     * @param consumer    segment consumer / 片段消费者
     * @param parentStyle style inherited from the enclosing context / 来自外层上下文的继承样式
     */
    public <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style parentStyle) {
        Style selfStyle = (this.style != null) ? this.style.applyTo(parentStyle) : parentStyle;
        for (Object part : decompose()) {
            Optional<T> result;
            if (part instanceof Text) {
                result = ((Text) part).visit(consumer, selfStyle);
            } else {
                result = consumer.accept(selfStyle, (String) part);
            }
            if (result.isPresent()) {
                return result;
            }
        }
        for (Text sibling : siblings) {
            Optional<T> result = sibling.visit(consumer, selfStyle);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    /**
     * Flattens the tree into a list of single-segment Texts, each carrying its
     * effective style — counterpart of the high-version {@code FormattedText#toFlatList}.
     * <p>将整树拍平为单片段 Text 列表，每个片段携带其有效样式 —— 对标高版本
     * {@code FormattedText#toFlatList}。</p>
     */
    public List<Text> toFlatList() {
        return toFlatList(Style.EMPTY);
    }

    /**
     * Flattens the tree on top of the given root style.
     * <p>在给定根样式之上拍平整棵树。</p>
     */
    public List<Text> toFlatList(Style rootStyle) {
        final List<Text> result = new ArrayList<Text>();
        visit((style, contents) -> {
            result.add(Text.literal(contents, style));
            return Optional.<Void>empty();
        }, rootStyle);
        return result;
    }

    /**
     * Decomposes this node's own content into render-ordered parts: plain strings
     * and embedded Text components (from translatable arguments).
     * <p>将本节点自身的内容拆解为渲染顺序的部件：普通字符串与内嵌 Text 组件
     * （来自可翻译参数）。</p>
     * <p>
     * Only {@code %s} placeholders (plain or indexed like {@code %1$s}) and {@code %%}
     * are decomposed; any other conversion, a stray {@code %}, or an out-of-range
     * index degrades the whole node to a single flat segment formatted with
     * {@link String#format} semantics (mirroring the legacy {@code I18n} behaviour,
     * including {@code "Format error: "} on failure).
     * <br>仅拆解 {@code %s} 占位符（含 {@code %1$s} 索引形式）与 {@code %%}；遇到其它
     * 转换符、游离 {@code %} 或越界索引时，整个节点降级为单个扁平片段，按
     * {@link String#format} 语义格式化（对齐旧版 {@code I18n} 行为，失败时含
     * {@code "Format error: "}）。
     * </p>
     */
    private List<Object> decompose() {
        if (!translatable || args.length == 0) {
            return Collections.<Object>singletonList(ownFlatText());
        }
        String template = resolvedTemplate();
        List<Object> parts = new ArrayList<Object>();
        Matcher matcher = FORMAT_PATTERN.matcher(template);
        int lastEnd = 0;
        int nextArg = 0;
        try {
            while (matcher.find()) {
                int start = matcher.start();
                if (start > lastEnd) {
                    parts.add(template.substring(lastEnd, start));
                }
                lastEnd = matcher.end();
                String conversion = matcher.group(2);
                if ("%".equals(conversion)) {
                    parts.add("%");
                    continue;
                }
                if (!"s".equals(conversion)) {
                    throw new IllegalArgumentException("Unsupported conversion: " + conversion);
                }
                String indexGroup = matcher.group(1);
                int index = (indexGroup != null) ? Integer.parseInt(indexGroup) - 1 : nextArg++;
                if (index < 0 || index >= args.length) {
                    throw new IllegalArgumentException("Argument index out of range: " + index);
                }
                Object arg = args[index];
                parts.add((arg instanceof Text) ? arg : String.valueOf(arg));
            }
            if (lastEnd < template.length()) {
                parts.add(template.substring(lastEnd));
            }
            return parts;
        } catch (IllegalArgumentException e) {
            // Degrade to the flat legacy string — e.g. "%d"/"%f" conversions or a
            // malformed template that String.format would reject.
            // 降级为扁平旧版字符串 —— 例如 "%d"/"%f" 转换符或 String.format 会拒绝的坏模板。
            return Collections.<Object>singletonList(ownFlatText());
        }
    }

    // ──── Legacy-formatted output / 旧版格式化输出 ────

    /**
     * Resolves the whole component tree into a single string with legacy {@code §}
     * formatting codes derived from each node's effective {@link Style} (own style merged
     * onto the inherited one). Arbitrary RGB colours degrade to the nearest of the 16
     * legacy colours via {@link Style.TextColor#toLegacyIndex()}, since the 1.7.10
     * FontRenderer only understands {@code §} codes.
     * <p>将整棵组件树解析为带旧版 {@code §} 格式码的单一字符串，格式码由每个节点的
     * 有效 {@link Style}（自身样式合并到继承样式之上）推导。任意 RGB 颜色通过
     * {@link Style.TextColor#toLegacyIndex()} 降级为最接近的 16 色之一，因为 1.7.10 的
     * FontRenderer 只认 {@code §} 码。</p>
     */
    public String getFormattedString() {
        return getFormattedString(Style.EMPTY);
    }

    /**
     * Resolves the whole tree into a single legacy {@code §}-coded string on top of
     * the given base style. Adjacent segments sharing the same effective style are
     * merged into one run; a {@code §r} reset separates runs whose codes would
     * otherwise leak into each other.
     * <p>在给定基础样式之上把整棵树解析为单个旧版 {@code §} 格式字符串。有效样式相同的
     * 相邻片段合并为同一段；格式码可能互相泄漏的段之间以 {@code §r} 重置分隔。</p>
     */
    public String getFormattedString(Style baseStyle) {
        final StringBuilder sb = new StringBuilder();
        final boolean[] anyCodes = {false};
        final Style[] previous = {null};
        visit((style, contents) -> {
            appendSegment(sb, previous, anyCodes, style, contents);
            return Optional.<Void>empty();
        }, baseStyle != null ? baseStyle : Style.EMPTY);
        return sb.toString();
    }

    /**
     * Appends one styled segment to the legacy output, merging with the previous
     * segment when the effective styles are equal and resetting with {@code §r}
     * before a different style whenever codes were already emitted.
     * <p>把一个带样式片段追加到旧版输出：有效样式与上一片段相同时直接合并，
     * 不同且之前已输出过格式码时先以 {@code §r} 重置。</p>
     *
     * @param sb       output buffer / 输出缓冲
     * @param previous single-element array holding the last segment's style / 单元素数组，保存上一片段的样式
     * @param anyCodes single-element flag: whether codes were already emitted / 单元素标记：之前是否已输出过格式码
     * @param style    the segment's effective style / 片段的有效样式
     * @param contents the segment's contents / 片段内容
     */
    private static void appendSegment(StringBuilder sb, Style[] previous, boolean[] anyCodes,
                                      Style style, String contents) {
        if (contents == null || contents.isEmpty()) {
            return;
        }
        if (previous[0] != null && previous[0].equals(style)) {
            // Same effective style as the previous segment — keep the run going.
            // 与上一片段有效样式相同 —— 延续同一段，不重复输出格式码。
            sb.append(contents);
            return;
        }
        if (anyCodes[0]) {
            // Previous segment emitted codes — reset before this segment's own codes.
            // 前一片段输出过格式码，先重置再输出本片段自身的格式码。
            sb.append('\u00a7').append('r');
        }
        String codes = legacyCodes(style);
        sb.append(codes).append(contents);
        if (!codes.isEmpty()) {
            anyCodes[0] = true;
        }
        previous[0] = style;
    }

    /**
     * Converts a style into its legacy {@code §} code prefix (colour first, then flags,
     * matching the vanilla emission order {@code k l m n o}).
     * <p>将样式转换为旧版 {@code §} 格式码前缀（先颜色后标志位，顺序对齐原版
     * {@code k l m n o}）。</p>
     */
    private static String legacyCodes(Style style) {
        if (style == null || style.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Style.TextColor color = style.getColor();
        if (color != null) {
            sb.append('\u00a7').append("0123456789abcdef".charAt(color.toLegacyIndex()));
        }
        if (style.isObfuscated()) sb.append('\u00a7').append('k');
        if (style.isBold()) sb.append('\u00a7').append('l');
        if (style.isStrikethrough()) sb.append('\u00a7').append('m');
        if (style.isUnderlined()) sb.append('\u00a7').append('n');
        if (style.isItalic()) sb.append('\u00a7').append('o');
        return sb.toString();
    }

    // ──── Convenience static String methods ────

    /**
     * Translates a key directly via {@link StatCollector}, returning the translated
     * string — side-agnostic, unlike the client-only {@code I18n}.
     * <p>直接通过 {@link StatCollector} 翻译键并返回翻译后的字符串 —— 与仅客户端的
     * {@code I18n} 不同，服务端同样可用。</p>
     *
     * <pre>{@code
     *   Text.translatableString("gui.no");
     *   Text.translatableString("item.count", 5, 10);
     * }</pre>
     */
    public static String translatableString(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
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
     * Siblings are carried over by reference.
     * <p>返回内容相同但应用了指定样式的新 Text。子组件按引用携带。</p>
     */
    public Text withStyle(Style style) {
        Text result = new Text(this.key, this.translatable, style, this.fallback, this.args);
        result.siblings.addAll(this.siblings);
        return result;
    }

    /**
     * Returns a new Text with the same content but the specified style applied
     * on top of any existing style. Siblings are carried over by reference.
     * <p>返回内容相同但在现有样式之上应用了指定样式的新 Text。子组件按引用携带。</p>
     */
    public Text withStyleApplied(Style style) {
        Style merged = (this.style != null) ? style.applyTo(this.style) : style;
        Text result = new Text(this.key, this.translatable, merged, this.fallback, this.args);
        result.siblings.addAll(this.siblings);
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
                && java.util.Objects.equals(style, other.style)
                && java.util.Objects.equals(fallback, other.fallback)
                && siblings.equals(other.siblings);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + (translatable ? 1 : 0);
        result = 31 * result + java.util.Arrays.hashCode(args);
        result = 31 * result + (style != null ? style.hashCode() : 0);
        result = 31 * result + (fallback != null ? fallback.hashCode() : 0);
        result = 31 * result + siblings.hashCode();
        return result;
    }

    // ──── JSON serialisation / JSON 序列化 ────

    /**
     * Serialises this Text tree back to a raw JSON text component string — the inverse
     * of {@link #fromJson(String)}.
     * <p>将本 Text 树序列化回原始 JSON 文本组件字符串 —— {@link #fromJson(String)} 的逆操作。</p>
     */
    public String toJson() {
        return toJsonElement().toString();
    }

    /**
     * Serialises this Text tree to a {@link JsonElement}.
     * <p>将本 Text 树序列化为 {@link JsonElement}。</p>
     */
    public JsonElement toJsonElement() {
        JsonObject obj = new JsonObject();
        if (translatable) {
            obj.addProperty("translate", key);
            if (fallback != null) {
                obj.addProperty("fallback", fallback);
            }
            if (args.length > 0) {
                JsonArray with = new JsonArray();
                for (Object arg : args) {
                    if (arg instanceof Text) {
                        with.add(((Text) arg).toJsonElement());
                    } else {
                        with.add(new JsonPrimitive(String.valueOf(arg)));
                    }
                }
                obj.add("with", with);
            }
        } else {
            obj.addProperty("text", key);
        }
        if (style != null && !style.isEmpty()) {
            Style.TextColor color = style.getColor();
            if (color != null) {
                String name = color.getName();
                obj.addProperty("color", name != null ? name : String.format("#%06X", color.getRgb()));
            }
            if (style.isBold()) obj.addProperty("bold", true);
            if (style.isItalic()) obj.addProperty("italic", true);
            if (style.isUnderlined()) obj.addProperty("underlined", true);
            if (style.isStrikethrough()) obj.addProperty("strikethrough", true);
            if (style.isObfuscated()) obj.addProperty("obfuscated", true);
            if (style.getInsertion() != null) obj.addProperty("insertion", style.getInsertion());
            if (style.getFont() != null) obj.addProperty("font", style.getFont().toString());
            if (style.getShadowColor() != null) obj.addProperty("shadow_color", style.getShadowColor());
            if (style.getClickEvent() != null) {
                JsonObject ce = new JsonObject();
                ce.addProperty("action", style.getClickEvent().getAction().name().toLowerCase(java.util.Locale.ROOT));
                ce.addProperty("value", style.getClickEvent().getValue());
                obj.add("clickEvent", ce);
            }
            if (style.getHoverEvent() != null) {
                JsonObject he = new JsonObject();
                he.addProperty("action", style.getHoverEvent().getAction().name().toLowerCase(java.util.Locale.ROOT));
                Object val = style.getHoverEvent().getValue();
                if (val instanceof Text) {
                    he.add("value", ((Text) val).toJsonElement());
                } else {
                    he.addProperty("value", String.valueOf(val));
                }
                obj.add("hoverEvent", he);
            }
        }
        if (!siblings.isEmpty()) {
            JsonArray extra = new JsonArray();
            for (Text sibling : siblings) {
                extra.add(sibling.toJsonElement());
            }
            obj.add("extra", extra);
        }
        return obj;
    }

    // ──── Serializer / 序列化器 ────

    /**
     * <p>
     * 原始 JSON 文本组件解析器 —— 对标高版本 {@code Component.Serializer}。<br>
     * 支持的输入形态：
     * </p>
     * <ul>
     *   <li>JSON 字符串 → 字面文本 / JSON string → literal text</li>
     *   <li>JSON 数组 → 首元素为父节点，其余作为子组件追加 / JSON array → first element
     *       as parent, rest appended as siblings</li>
     *   <li>JSON 对象 → {@code text} / {@code translate}+{@code with}+{@code fallback} 内容，搭配样式字段
     *       （{@code color}、{@code bold}、{@code italic}、{@code underlined}、
     *       {@code strikethrough}、{@code obfuscated}、{@code insertion}、{@code font}、
     *       {@code clickEvent}、{@code hoverEvent}）及 {@code extra} 子组件数组 /
     *       JSON object → {@code text} or {@code translate}+{@code with}+{@code fallback}
     *       content plus the style fields and the {@code extra} children array</li>
     * </ul>
     * <p>
     * 不支持需要游戏内实体上下文的内容类型（{@code score}、{@code selector}、
     * {@code nbt}、{@code keybind}），遇到时降级为空字面节点（仅保留样式与 {@code extra}）。
     * <br>Content types that need in-game entity context ({@code score}, {@code selector},
     * {@code nbt}, {@code keybind}) are not supported and degrade to an empty literal node
     * (style and {@code extra} preserved).
     * </p>
     */
    public static final class Serializer {

        private Serializer() {
        }

        /**
         * Parses a raw JSON text string. Falls back to a literal Text of the input
         * when the string is not valid JSON (mirrors command-block leniency).
         * <p>解析原始 JSON 文本字符串。非合法 JSON 时降级为输入内容的字面文本
         * （对齐命令方块的宽容行为）。</p>
         */
        public static Text fromJson(String json) {
            if (json == null || json.isEmpty()) {
                return Text.literal("");
            }
            try {
                JsonElement element = new JsonParser().parse(json);
                return fromJson(element);
            } catch (JsonParseException e) {
                return Text.literal(json);
            }
        }

        /**
         * Parses an already-parsed JSON element into a Text tree.
         * <p>将已解析的 JSON 元素转换为 Text 树。</p>
         */
        public static Text fromJson(JsonElement element) {
            if (element == null || element.isJsonNull()) {
                return Text.literal("");
            }
            if (element.isJsonPrimitive()) {
                return Text.literal(element.getAsString());
            }
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                if (array.size() == 0) {
                    return Text.literal("");
                }
                Text parent = fromJson(array.get(0));
                for (int i = 1; i < array.size(); i++) {
                    parent.append(fromJson(array.get(i)));
                }
                return parent;
            }
            if (!element.isJsonObject()) {
                throw new JsonParseException("Don't know how to turn " + element + " into a Text");
            }
            JsonObject obj = element.getAsJsonObject();

            // ── Content / 内容 ──
            Text result;
            if (obj.has("text")) {
                result = Text.literal(obj.get("text").getAsString());
            } else if (obj.has("translate")) {
                String translateKey = obj.get("translate").getAsString();
                String translateFallback = obj.has("fallback") ? obj.get("fallback").getAsString() : null;
                if (obj.has("with") && obj.get("with").isJsonArray()) {
                    JsonArray with = obj.getAsJsonArray("with");
                    Object[] withArgs = new Object[with.size()];
                    for (int i = 0; i < with.size(); i++) {
                        JsonElement arg = with.get(i);
                        if (arg.isJsonPrimitive() && !arg.getAsJsonPrimitive().isString()) {
                            // Numbers / booleans pass through as-is for template formatting.
                            // 数字/布尔直接传给模板格式化。
                            JsonPrimitive prim = arg.getAsJsonPrimitive();
                            withArgs[i] = prim.isNumber() ? (Object) prim.getAsNumber() : (Object) prim.getAsBoolean();
                        } else {
                            Text parsed = fromJson(arg);
                            // Plain literals collapse to String (keeps formatting lean);
                            // anything carrying style/siblings stays a component so its
                            // style survives into the rendered output — same leniency as
                            // the vanilla IChatComponent deserializer.
                            // 纯字面量收敛为 String（保持格式化轻量）；带样式/子组件的保持
                            // 组件形态，使其样式保留到渲染输出 —— 与原版 IChatComponent
                            // 反序列化器的宽容度一致。
                            withArgs[i] = isPlainLiteral(parsed) ? parsed.getKey() : parsed;
                        }
                    }
                    result = Text.translatableWithFallback(translateKey, translateFallback, withArgs);
                } else {
                    result = Text.translatableWithFallback(translateKey, translateFallback);
                }
            } else {
                // Unsupported content type (score/selector/nbt/keybind) — degrade to empty literal.
                // 不支持的内容类型（score/selector/nbt/keybind）—— 降级为空字面节点。
                result = Text.literal("");
            }

            // ── Style / 样式 ──
            Style style = parseStyle(obj);
            if (!style.isEmpty()) {
                result.setStyle(style);
            }

            // ── Children / 子组件 ──
            if (obj.has("extra") && obj.get("extra").isJsonArray()) {
                JsonArray extra = obj.getAsJsonArray("extra");
                for (int i = 0; i < extra.size(); i++) {
                    result.append(fromJson(extra.get(i)));
                }
            }
            return result;
        }

        /**
         * Whether the given Text carries nothing but its literal content — no
         * translatable key, no style, no siblings. Such nodes can safely collapse
         * into a plain String inside a {@code with} argument array.
         * <p>给定 Text 是否仅携带字面内容 —— 无可翻译键、无样式、无子组件。
         * 这类节点可以安全地收敛为 {@code with} 参数数组中的纯字符串。</p>
         */
        private static boolean isPlainLiteral(Text text) {
            return !text.translatable
                    && (text.style == null || text.style.isEmpty())
                    && !text.hasSiblings();
        }

        /**
         * Parses the style fields of a component object into a {@link Style}.
         * <p>将组件对象的样式字段解析为 {@link Style}。</p>
         */
        public static Style parseStyle(JsonObject obj) {
            Style style = Style.EMPTY;
            if (obj.has("color")) {
                Style.TextColor color = Style.TextColor.parseColor(obj.get("color").getAsString());
                if (color != null) {
                    style = style.withColor(color);
                }
            }
            if (obj.has("bold")) style = style.withBold(obj.get("bold").getAsBoolean());
            if (obj.has("italic")) style = style.withItalic(obj.get("italic").getAsBoolean());
            if (obj.has("underlined")) style = style.withUnderlined(obj.get("underlined").getAsBoolean());
            if (obj.has("strikethrough")) style = style.withStrikethrough(obj.get("strikethrough").getAsBoolean());
            if (obj.has("obfuscated")) style = style.withObfuscated(obj.get("obfuscated").getAsBoolean());
            if (obj.has("insertion")) style = style.withInsertion(obj.get("insertion").getAsString());
            if (obj.has("font")) style = style.withFont(new ResourceLocation(obj.get("font").getAsString()));
            if (obj.has("shadow_color")) style = style.withShadowColor(obj.get("shadow_color").getAsInt());
            if (obj.has("clickEvent") && obj.get("clickEvent").isJsonObject()) {
                Style.ClickEvent click = parseClickEvent(obj.getAsJsonObject("clickEvent"));
                if (click != null) {
                    style = style.withClickEvent(click);
                }
            }
            if (obj.has("hoverEvent") && obj.get("hoverEvent").isJsonObject()) {
                Style.HoverEvent hover = parseHoverEvent(obj.getAsJsonObject("hoverEvent"));
                if (hover != null) {
                    style = style.withHoverEvent(hover);
                }
            }
            return style;
        }

        /**
         * Parses a {@code clickEvent} object; unknown actions yield {@code null}.
         * <p>解析 {@code clickEvent} 对象；未知 action 返回 {@code null}。</p>
         */
        @Nullable
        private static Style.ClickEvent parseClickEvent(JsonObject obj) {
            if (!obj.has("action") || !obj.has("value")) {
                return null;
            }
            try {
                Style.ClickEvent.Action action = Style.ClickEvent.Action.valueOf(
                        obj.get("action").getAsString().toUpperCase(java.util.Locale.ROOT));
                return new Style.ClickEvent(action, obj.get("value").getAsString());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        /**
         * Parses a {@code hoverEvent} object; {@code show_text} values become Text,
         * other actions keep their raw string value (non-primitive payloads degrade
         * to their JSON text instead of throwing). Unknown actions yield {@code null}.
         * <p>解析 {@code hoverEvent} 对象；{@code show_text} 的值解析为 Text，
         * 其余 action 保留原始字符串（非原始载荷降级为 JSON 文本而非抛异常）。
         * 未知 action 返回 {@code null}。</p>
         */
        @Nullable
        private static Style.HoverEvent parseHoverEvent(JsonObject obj) {
            if (!obj.has("action") || !obj.has("value")) {
                return null;
            }
            try {
                Style.HoverEvent.Action action = Style.HoverEvent.Action.valueOf(
                        obj.get("action").getAsString().toUpperCase(java.util.Locale.ROOT));
                JsonElement valueElement = obj.get("value");
                Object value;
                if (action == Style.HoverEvent.Action.SHOW_TEXT) {
                    value = fromJson(valueElement);
                } else if (valueElement.isJsonPrimitive()) {
                    value = valueElement.getAsString();
                } else {
                    // Non-primitive values (e.g. modern show_item object payloads)
                    // degrade to their raw JSON text instead of throwing.
                    // 非原始值（如现代 show_item 的对象载荷）降级为原始 JSON 文本，避免抛异常。
                    value = valueElement.toString();
                }
                return new Style.HoverEvent(action, value);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
