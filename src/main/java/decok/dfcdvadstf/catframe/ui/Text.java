package decok.dfcdvadstf.catframe.ui;

import com.google.gson.*;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        String self = selfString();
        if (siblings.isEmpty()) {
            return self;
        }
        StringBuilder sb = new StringBuilder(self);
        for (Text sibling : siblings) {
            sb.append(sibling.getString());
        }
        return sb.toString();
    }

    /**
     * Resolves only this node's own content, ignoring siblings.
     * <p>仅解析本节点自身内容，不含子组件。</p>
     */
    private String selfString() {
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
        StringBuilder sb = new StringBuilder();
        appendFormatted(Style.EMPTY, sb, new boolean[]{false});
        return sb.toString();
    }

    /**
     * Recursive worker for {@link #getFormattedString()} — emits {@code §r} between
     * differently-styled segments so formatting never leaks across nodes.
     * <p>{@link #getFormattedString()} 的递归实现 —— 在样式不同的片段之间插入
     * {@code §r}，避免格式泄漏到后续节点。</p>
     *
     * @param inherited style inherited from the parent chain / 从父链继承的样式
     * @param sb        output buffer / 输出缓冲
     * @param anyCodes  single-element flag: whether codes were already emitted / 单元素标记：之前是否已输出过格式码
     */
    private void appendFormatted(Style inherited, StringBuilder sb, boolean[] anyCodes) {
        Style effective = (this.style != null) ? this.style.applyTo(inherited) : inherited;
        String self = selfString();
        if (self != null && !self.isEmpty()) {
            String codes = legacyCodes(effective);
            if (anyCodes[0]) {
                // Previous segment emitted codes — reset before this segment's own codes.
                // 前一片段输出过格式码，先重置再输出本片段自身的格式码。
                sb.append('\u00a7').append('r');
            }
            sb.append(codes).append(self);
            if (!codes.isEmpty()) {
                anyCodes[0] = true;
            }
        }
        for (Text sibling : siblings) {
            sibling.appendFormatted(effective, sb, anyCodes);
        }
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
     * Siblings are carried over by reference.
     * <p>返回内容相同但应用了指定样式的新 Text。子组件按引用携带。</p>
     */
    public Text withStyle(Style style) {
        Text result = new Text(this.key, this.translatable, this.args);
        result.style = style;
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
        Text result = new Text(this.key, this.translatable, this.args);
        result.style = merged;
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
                && siblings.equals(other.siblings);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + (translatable ? 1 : 0);
        result = 31 * result + java.util.Arrays.hashCode(args);
        result = 31 * result + (style != null ? style.hashCode() : 0);
        result = 31 * result + siblings.hashCode();
        return result;
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
     *   <li>JSON 对象 → {@code text} / {@code translate}+{@code with} 内容，搭配样式字段
     *       （{@code color}、{@code bold}、{@code italic}、{@code underlined}、
     *       {@code strikethrough}、{@code obfuscated}、{@code insertion}、{@code font}、
     *       {@code clickEvent}、{@code hoverEvent}）及 {@code extra} 子组件数组 /
     *       JSON object → {@code text} or {@code translate}+{@code with} content plus the
     *       style fields and the {@code extra} children array</li>
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
                if (obj.has("with") && obj.get("with").isJsonArray()) {
                    JsonArray with = obj.getAsJsonArray("with");
                    Object[] withArgs = new Object[with.size()];
                    for (int i = 0; i < with.size(); i++) {
                        JsonElement arg = with.get(i);
                        if (arg.isJsonPrimitive() && !arg.getAsJsonPrimitive().isString()) {
                            // Numbers / booleans pass through as-is for I18n.format.
                            // 数字/布尔直接传给 I18n.format。
                            JsonPrimitive prim = arg.getAsJsonPrimitive();
                            withArgs[i] = prim.isNumber() ? (Object) prim.getAsNumber() : (Object) prim.getAsBoolean();
                        } else {
                            // Nested components resolve to their formatted string form.
                            // 嵌套组件解析为其格式化字符串形态。
                            withArgs[i] = fromJson(arg).getFormattedString();
                        }
                    }
                    result = Text.translatable(translateKey, withArgs);
                } else {
                    result = Text.translatable(translateKey);
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
         * other actions keep their raw string value. Unknown actions yield {@code null}.
         * <p>解析 {@code hoverEvent} 对象；{@code show_text} 的值解析为 Text，
         * 其余 action 保留原始字符串。未知 action 返回 {@code null}。</p>
         */
        @Nullable
        private static Style.HoverEvent parseHoverEvent(JsonObject obj) {
            if (!obj.has("action") || !obj.has("value")) {
                return null;
            }
            try {
                Style.HoverEvent.Action action = Style.HoverEvent.Action.valueOf(
                        obj.get("action").getAsString().toUpperCase(java.util.Locale.ROOT));
                Object value = (action == Style.HoverEvent.Action.SHOW_TEXT)
                        ? fromJson(obj.get("value"))
                        : obj.get("value").getAsString();
                return new Style.HoverEvent(action, value);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
