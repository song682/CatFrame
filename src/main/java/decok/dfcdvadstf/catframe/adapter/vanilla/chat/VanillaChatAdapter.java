package decok.dfcdvadstf.catframe.adapter.vanilla.chat;

import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import javax.annotation.Nullable;

/**
 * <p>
 * 聊天组件桥接器 —— 在 UI 包的 {@link Text}/{@link Style} 树与 1.7.10 原版的
 * {@link IChatComponent}/{@link ChatStyle} 体系之间做双向转换。<br>
 * 与 {@code ChatComponentStyle} 的 {@code parentStyle} 继承链对齐：两种模型都是
 * "节点持有原始三态样式 + 渲染时沿树继承"，因此桥接尽量直接映射原始值。
 * </p>
 * <p>
 * Chat-component bridge — converts both ways between the UI package's
 * {@link Text}/{@link Style} tree and 1.7.10's vanilla
 * {@link IChatComponent}/{@link ChatStyle} system.<br>
 * It mirrors the {@code ChatStyle} {@code parentStyle} inheritance chain: both models
 * store raw tri-state values per node and resolve inheritance at render time, so the
 * bridge maps raw values directly wherever possible.
 * </p>
 *
 * <p>
 * 类型映射 / Type mapping:
 * <ul>
 *   <li>{@code literal Text} → {@link ChatComponentText}</li>
 *   <li>{@code translatable Text} → {@link ChatComponentTranslation}</li>
 *   <li>{@link Style} → {@link ChatStyle}</li>
 *   <li>{@link Style.ClickEvent} / {@link Style.HoverEvent} → {@link ClickEvent} / {@link HoverEvent}</li>
 * </ul>
 * </p>
 *
 * <p>
 * 降级 / Degradations:
 * <ul>
 *   <li>{@code insertion}、{@code font}、{@code shadow_color}、{@code fallback}
 *       在 1.7.10 无对应字段，转换到原版时丢弃 /
 *       No 1.7.10 counterpart for {@code insertion}, {@code font}, {@code shadow_color}
 *       or {@code fallback} — dropped in the vanilla direction.</li>
 *   <li>点击动作 {@code CHANGE_PAGE}、{@code COPY_TO_CLIPBOARD} 与悬停动作
 *       {@code SHOW_ENTITY} 无对应，丢弃 /
 *       {@code CHANGE_PAGE}, {@code COPY_TO_CLIPBOARD} click actions and the
 *       {@code SHOW_ENTITY} hover action have no counterparts — dropped.</li>
 *   <li>任意 RGB 颜色降级为最接近的 16 色之一 /
 *       Arbitrary RGB colours degrade to the nearest of the 16 legacy colours.</li>
 *   <li>反向转换（原版 → Text）读取的是<em>有效</em>样式（含原版继承链，其父链可能
 *       尚未在原树上挂接，因此由本转换器自顶向下重建），仅在与父级不同时写入节点，
 *       避免样式膨胀；非 {@code ChatComponentText}/{@code ChatComponentTranslation}
 *       的自定义组件降级为其自身无格式文本 /
 *       The vanilla→Text direction reads <em>effective</em> styles (inheritance
 *       included; the vanilla parent chain may not be wired yet, so it is rebuilt
 *       top-down here) and only writes values differing from the parent, avoiding
 *       bloat; custom component types degrade to their own unformatted text.</li>
 * </ul>
 * </p>
 */
public final class VanillaChatAdapter {

    private VanillaChatAdapter() {
    }

    // ──── Text → IChatComponent / Text 到原版 ────

    /**
     * Converts a Text tree into a vanilla {@link IChatComponent} tree. Styles are
     * written as raw (tri-state) values so the vanilla {@code parentStyle} chain
     * resolves inheritance the same way {@link Text} traversal does.
     * <p>把 Text 树转换为原版 {@link IChatComponent} 树。样式按原始（三态）值写入，
     * 使原版 {@code parentStyle} 链与 {@link Text} 遍历以相同方式解析继承。</p>
     */
    public static IChatComponent toVanilla(@Nullable Text text) {
        if (text == null) {
            return new ChatComponentText("");
        }
        IChatComponent result;
        if (text.isTranslatable()) {
            Object[] rawArgs = text.getArgs();
            Object[] vanillaArgs = new Object[rawArgs.length];
            for (int i = 0; i < rawArgs.length; i++) {
                Object arg = rawArgs[i];
                // Embedded components stay components so ChatComponentTranslation's
                // own template walker resolves their styles.
                // 内嵌组件保持组件形态，交由原版 ChatComponentTranslation 的模板遍历处理其样式。
                vanillaArgs[i] = (arg instanceof Text) ? toVanilla((Text) arg) : arg;
            }
            result = new ChatComponentTranslation(text.getKey(), vanillaArgs);
        } else {
            result = new ChatComponentText(text.getKey());
        }
        applyVanillaStyle(result, text.getStyle());
        for (Text sibling : text.getSiblings()) {
            result.appendSibling(toVanilla(sibling));
        }
        return result;
    }

    /**
     * Writes a raw {@link Style} onto a vanilla component's {@link ChatStyle}.
     * <p>把原始 {@link Style} 写入原版组件的 {@link ChatStyle}。</p>
     */
    private static void applyVanillaStyle(IChatComponent target, @Nullable Style style) {
        if (style == null || style.isEmpty()) {
            return;
        }
        ChatStyle vanilla = new ChatStyle();
        if (style.getColor() != null) {
            vanilla.setColor(toVanillaColor(style.getColor()));
        }
        vanilla.setBold(style.getRawFlag(Style.TextFormat.BOLD));
        vanilla.setItalic(style.getRawFlag(Style.TextFormat.ITALIC));
        vanilla.setStrikethrough(style.getRawFlag(Style.TextFormat.STRIKETHROUGH));
        vanilla.setUnderlined(style.getRawFlag(Style.TextFormat.UNDERLINE));
        vanilla.setObfuscated(style.getRawFlag(Style.TextFormat.OBFUSCATED));
        Style.ClickEvent click = style.getClickEvent();
        if (click != null) {
            ClickEvent.Action action = toVanillaClickAction(click.getAction());
            if (action != null) {
                vanilla.setChatClickEvent(new ClickEvent(action, click.getValue()));
            }
        }
        Style.HoverEvent hover = style.getHoverEvent();
        if (hover != null) {
            HoverEvent.Action action = toVanillaHoverAction(hover.getAction());
            if (action != null) {
                vanilla.setChatHoverEvent(new HoverEvent(action, toVanillaHoverValue(hover.getValue())));
            }
        }
        target.setChatStyle(vanilla);
    }

    /**
     * Maps a project colour to a vanilla colour — named colours keep their identity,
     * arbitrary RGB degrades to the nearest of the 16 legacy colours.
     * <p>把项目颜色映射为原版颜色 —— 具名颜色保持原样，任意 RGB 降级为最接近的 16 色之一。</p>
     */
    private static EnumChatFormatting toVanillaColor(Style.TextColor color) {
        String name = color.getName();
        if (name != null) {
            EnumChatFormatting named = EnumChatFormatting.getValueByName(name);
            if (named != null && named.isColor()) {
                return named;
            }
        }
        return EnumChatFormatting.values()[color.toLegacyIndex()];
    }

    /**
     * Maps a {@link Style.ClickEvent.Action} into its 1.7.10 counterpart, or
     * {@code null} when the action has none ({@code CHANGE_PAGE},
     * {@code COPY_TO_CLIPBOARD}).
     * <p>把 {@link Style.ClickEvent.Action} 映射为其 1.7.10 对应项；无对应
     * （{@code CHANGE_PAGE}、{@code COPY_TO_CLIPBOARD}）时返回 {@code null}。</p>
     */
    @Nullable
    private static ClickEvent.Action toVanillaClickAction(Style.ClickEvent.Action action) {
        switch (action) {
            case OPEN_URL: return ClickEvent.Action.OPEN_URL;
            case OPEN_FILE: return ClickEvent.Action.OPEN_FILE;
            case RUN_COMMAND: return ClickEvent.Action.RUN_COMMAND;
            case SUGGEST_COMMAND: return ClickEvent.Action.SUGGEST_COMMAND;
            default: return null;
        }
    }

    /**
     * Maps a {@link Style.HoverEvent.Action} into its 1.7.10 counterpart, or
     * {@code null} when the action has none ({@code SHOW_ENTITY}).
     * <p>把 {@link Style.HoverEvent.Action} 映射为其 1.7.10 对应项；无对应
     * （{@code SHOW_ENTITY}）时返回 {@code null}。</p>
     */
    @Nullable
    private static HoverEvent.Action toVanillaHoverAction(Style.HoverEvent.Action action) {
        switch (action) {
            case SHOW_TEXT: return HoverEvent.Action.SHOW_TEXT;
            case SHOW_ITEM: return HoverEvent.Action.SHOW_ITEM;
            case SHOW_ACHIEVEMENT: return HoverEvent.Action.SHOW_ACHIEVEMENT;
            default: return null;
        }
    }

    /**
     * Converts a hover payload into the 1.7.10 form — components convert as-is,
     * anything else becomes a literal text component.
     * <p>把悬停载荷转换为 1.7.10 形态 —— 组件原样转换，其它内容变为字面文本组件。</p>
     */
    private static IChatComponent toVanillaHoverValue(Object value) {
        if (value instanceof Text) {
            return toVanilla((Text) value);
        }
        return new ChatComponentText(value == null ? "" : String.valueOf(value));
    }

    // ──── IChatComponent → Text / 原版到 Text ────

    /**
     * Converts a vanilla {@link IChatComponent} tree into a {@link Text} tree.
     * <p>把原版 {@link IChatComponent} 树转换为 {@link Text} 树。</p>
     */
    public static Text fromVanilla(@Nullable IChatComponent component) {
        if (component == null) {
            return Text.literal("");
        }
        return fromVanilla(component, null);
    }

    /**
     * Recursive worker carrying the effective parent chain rebuilt by this converter
     * (the vanilla tree's own {@code parentStyle} links are wired lazily at render
     * time and may not be established yet).
     * <p>递归实现，携带由本转换器重建的有效父链（原版树自身的 {@code parentStyle}
     * 链接在渲染时才惰性挂接，可能尚未建立）。</p>
     */
    private static Text fromVanilla(IChatComponent component, @Nullable ChatStyle parentVanilla) {
        ChatStyle effective = component.getChatStyle().createShallowCopy().setParentStyle(parentVanilla);

        Text result;
        if (component instanceof ChatComponentText) {
            result = Text.literal(((ChatComponentText) component).getChatComponentText_TextValue());
        } else if (component instanceof ChatComponentTranslation) {
            ChatComponentTranslation translation = (ChatComponentTranslation) component;
            Object[] rawArgs = translation.getFormatArgs();
            Object[] args = new Object[rawArgs.length];
            for (int i = 0; i < rawArgs.length; i++) {
                Object arg = rawArgs[i];
                // Embedded components inherit this node's effective style, exactly as
                // the vanilla translation constructor wires them.
                // 内嵌组件继承本节点的有效样式，与原版翻译构造器的挂接一致。
                args[i] = (arg instanceof IChatComponent) ? fromVanilla((IChatComponent) arg, effective) : arg;
            }
            result = Text.translatableWithFallback(translation.getKey(), null, args);
        } else {
            // Custom component types degrade to their own unformatted text.
            // 自定义组件类型降级为其自身无格式文本。
            result = Text.literal(component.getUnformattedTextForChat());
        }

        Style style = fromVanillaStyle(effective, parentVanilla);
        if (!style.isEmpty()) {
            result.setStyle(style);
        }
        for (Object sibling : component.getSiblings()) {
            result.append(fromVanilla((IChatComponent) sibling, effective));
        }
        return result;
    }

    /**
     * Reads this node's effective style values, writing only those differing from
     * the already-resolved parent — equal values are left for the Text tree to
     * inherit, so unstyled trees stay lean.
     * <p>读取本节点的有效样式值，仅写入与已解析父级不同的部分 —— 相等的值留给
     * Text 树继承，使无样式树保持轻量。</p>
     */
    private static Style fromVanillaStyle(ChatStyle effective, @Nullable ChatStyle parentVanilla) {
        Style style = Style.EMPTY;
        EnumChatFormatting color = effective.getColor();
        EnumChatFormatting parentColor = (parentVanilla != null) ? parentVanilla.getColor() : null;
        if (color != null && color != parentColor) {
            Style.TextColor parsed = Style.TextColor.parseColor(color.getFriendlyName());
            if (parsed != null) {
                style = style.withColor(parsed);
            }
        }
        if (effective.getBold() != (parentVanilla != null && parentVanilla.getBold())) {
            style = style.withBold(effective.getBold());
        }
        if (effective.getItalic() != (parentVanilla != null && parentVanilla.getItalic())) {
            style = style.withItalic(effective.getItalic());
        }
        if (effective.getStrikethrough() != (parentVanilla != null && parentVanilla.getStrikethrough())) {
            style = style.withStrikethrough(effective.getStrikethrough());
        }
        if (effective.getUnderlined() != (parentVanilla != null && parentVanilla.getUnderlined())) {
            style = style.withUnderlined(effective.getUnderlined());
        }
        if (effective.getObfuscated() != (parentVanilla != null && parentVanilla.getObfuscated())) {
            style = style.withObfuscated(effective.getObfuscated());
        }
        ClickEvent click = effective.getChatClickEvent();
        ClickEvent parentClick = (parentVanilla != null) ? parentVanilla.getChatClickEvent() : null;
        if (click != null && !click.equals(parentClick)) {
            Style.ClickEvent.Action action = fromVanillaClickAction(click.getAction());
            if (action != null) {
                style = style.withClickEvent(new Style.ClickEvent(action, click.getValue()));
            }
        }
        HoverEvent hover = effective.getChatHoverEvent();
        HoverEvent parentHover = (parentVanilla != null) ? parentVanilla.getChatHoverEvent() : null;
        if (hover != null && !hover.equals(parentHover)) {
            Style.HoverEvent.Action action = fromVanillaHoverAction(hover.getAction());
            if (action != null) {
                style = style.withHoverEvent(new Style.HoverEvent(action, fromVanillaHoverValue(hover)));
            }
        }
        return style;
    }

    /**
     * Maps a 1.7.10 click action into the project's action set, or {@code null} for
     * {@code TWITCH_USER_INFO} which has no counterpart.
     * <p>把 1.7.10 点击动作映射到项目动作集合；{@code TWITCH_USER_INFO} 无对应，
     * 返回 {@code null}。</p>
     */
    @Nullable
    private static Style.ClickEvent.Action fromVanillaClickAction(ClickEvent.Action action) {
        switch (action) {
            case OPEN_URL: return Style.ClickEvent.Action.OPEN_URL;
            case OPEN_FILE: return Style.ClickEvent.Action.OPEN_FILE;
            case RUN_COMMAND: return Style.ClickEvent.Action.RUN_COMMAND;
            case SUGGEST_COMMAND: return Style.ClickEvent.Action.SUGGEST_COMMAND;
            default: return null;
        }
    }

    /**
     * Maps a 1.7.10 hover action into the project's action set.
     * <p>把 1.7.10 悬停动作映射到项目动作集合。</p>
     */
    @Nullable
    private static Style.HoverEvent.Action fromVanillaHoverAction(HoverEvent.Action action) {
        switch (action) {
            case SHOW_TEXT: return Style.HoverEvent.Action.SHOW_TEXT;
            case SHOW_ITEM: return Style.HoverEvent.Action.SHOW_ITEM;
            case SHOW_ACHIEVEMENT: return Style.HoverEvent.Action.SHOW_ACHIEVEMENT;
            default: return null;
        }
    }

    /**
     * Converts a hover payload back into the project form — {@code show_text}
     * becomes a Text component, other actions travel as their plain text form.
     * <p>把悬停载荷转换回项目形态 —— {@code show_text} 变为 Text 组件，
     * 其余 action 以纯文本形态传递。</p>
     */
    private static Object fromVanillaHoverValue(HoverEvent hover) {
        IChatComponent value = hover.getValue();
        if (hover.getAction() == HoverEvent.Action.SHOW_TEXT) {
            return fromVanilla(value);
        }
        return value == null ? "" : value.getUnformattedText();
    }
}
