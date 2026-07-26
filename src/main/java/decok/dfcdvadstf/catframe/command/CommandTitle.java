package decok.dfcdvadstf.catframe.command;

import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.Title;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;

import java.util.List;

/**
 * <p>
 * {@code /title} 客户端命令 —— 复刻高版本 {@code /title} 的完整语法：
 * </p>
 * <pre>{@code
 *   /title <targets> (clear|reset)
 *   /title <targets> (title|subtitle|actionbar) <title>
 *   /title <targets> times <fadeIn> <stay> <fadeOut>
 * }</pre>
 * <p>
 * The {@code /title} client command — full modern {@code /title} syntax as above.
 * </p>
 *
 * <h3>客户端命令定位 / Why a client command</h3>
 * <p>
 * Title / ActionBar 的全部状态都是客户端单例（{@code TitleOverlay} / {@code ActionBarOverlay}），
 * CatFrame 没有网络通道，也不保证存在于服务端，因此本命令经
 * {@code ClientCommandHandler} 注册、只在本地客户端执行。{@code <targets>} 据此收敛为
 * 本地玩家匹配：选择器（{@code @p/@a/@r/@s}）或与本地玩家名一致时生效，否则报错且
 * 不产生任何效果——与原版“时间值只发送到目标自己的客户端”的语义同构。
 * <br>All Title / ActionBar state lives in client singletons ({@code TitleOverlay} /
 * {@code ActionBarOverlay}); CatFrame has no network channel and is not guaranteed
 * server-side, so this command registers via {@code ClientCommandHandler} and executes
 * locally only. {@code <targets>} therefore narrows to local-player matching: a selector
 * ({@code @p/@a/@r/@s}) or the local player's name applies, anything else errors with no
 * effect — isomorphic to vanilla's "times are sent only to the target's own client".
 * </p>
 * <p>
 * <b>注意 / Note:</b> 客户端命令会拦截同名输入——若所连服务器自带 {@code /title}
 * （如插件实现），本地这条会优先生效、遮蔽服务端版本。
 * <br>A client command intercepts matching chat input — if the connected server provides
 * its own {@code /title} (e.g. via a plugin), this local one takes precedence and shadows it.
 * </p>
 *
 * <h3>文本参数 / Text argument</h3>
 * <p>
 * {@code <title>} 由第 3 个参数起以空格拼接，经 {@link Text#fromJson(String)} 宽容解析：
 * 合法的原始 JSON 文本组件（含样式与 {@code extra}）按富文本处理，非 JSON 输入降级为字面文本。
 * <br>{@code <title>} joins the remaining args with spaces and goes through the lenient
 * {@link Text#fromJson(String)}: valid raw JSON text components (styles and {@code extra}
 * included) render as rich text, non-JSON input degrades to a literal.
 * </p>
 */
public class CommandTitle extends CommandBase {

    /** Sub-command literals for tab completion / 用于 Tab 补全的子命令字面量 */
    private static final String[] SUB_COMMANDS = {"title", "subtitle", "actionbar", "times", "clear", "reset"};

    /** Player-type selector bases accepted for the local player / 可匹配本地玩家的玩家类选择器 */
    private static final String[] PLAYER_SELECTORS = {"@p", "@a", "@r", "@s"};

    @Override
    public String getCommandName() {
        return "title";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "commands.catframe.title.usage";
    }

    /**
     * Always usable — the command is client-local and touches no server state, so the
     * vanilla op-level gate (permission level 2 for {@code /title}) does not apply.
     * <p>始终可用 —— 命令纯客户端本地执行、不触碰服务端状态，故不适用原版
     * {@code /title} 的 OP 权限门槛（等级 2）。</p>
     */
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            throw new CommandException("commands.catframe.title.noPlayer");
        }
        String playerName = player.getCommandSenderName();
        requireLocalTarget(args[0], playerName);

        String sub = args[1].toLowerCase(java.util.Locale.ROOT);

        if ("clear".equals(sub)) {
            requireArgCount(args, 2, sender);
            Title.clear();
            sender.addChatMessage(new ChatComponentTranslation("commands.catframe.title.cleared", playerName));
        } else if ("reset".equals(sub)) {
            requireArgCount(args, 2, sender);
            Title.reset();
            sender.addChatMessage(new ChatComponentTranslation("commands.catframe.title.reset", playerName));
        } else if ("times".equals(sub)) {
            requireArgCount(args, 5, sender);
            int fadeIn = parseIntWithMin(sender, args[2], 0);
            int stay = parseIntWithMin(sender, args[3], 0);
            int fadeOut = parseIntWithMin(sender, args[4], 0);
            Title.times(fadeIn, stay, fadeOut);
            sender.addChatMessage(new ChatComponentTranslation("commands.catframe.title.times", playerName));
        } else if ("title".equals(sub) || "subtitle".equals(sub) || "actionbar".equals(sub)) {
            if (args.length < 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            Text text = Text.fromJson(joinArgs(args, 2));
            if ("title".equals(sub)) {
                Title.show(text);
                sender.addChatMessage(new ChatComponentTranslation("commands.catframe.title.title", playerName));
            } else if ("subtitle".equals(sub)) {
                Title.subtitle(text);
                sender.addChatMessage(new ChatComponentTranslation("commands.catframe.title.subtitle", playerName));
            } else {
                Title.actionbar(text);
                sender.addChatMessage(new ChatComponentTranslation("commands.catframe.title.actionbar", playerName));
            }
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    // ──── Target resolution / 目标解析 ────

    /**
     * Validates that the target token includes the local player: a player-type selector
     * ({@code @p/@a/@r/@s}, selector arguments like {@code @a[r=10]} are accepted without
     * evaluating the brackets locally) or a case-insensitive match of the local name.
     * {@code @e} is rejected as vanilla {@code /title} requires player targets.
     * <p>校验目标令牌包含本地玩家：玩家类选择器（{@code @p/@a/@r/@s}，形如
     * {@code @a[r=10]} 的选择器参数在本地不求值、直接接受）或与本地玩家名大小写不敏感
     * 匹配。{@code @e} 按原版 {@code /title} 仅限玩家目标的规则拒绝。</p>
     *
     * @throws CommandException when the target cannot include the local player
     *                          / 目标不可能包含本地玩家时抛出
     */
    private static void requireLocalTarget(String token, String playerName) {
        if (token.startsWith("@")) {
            int bracket = token.indexOf('[');
            String base = bracket >= 0 ? token.substring(0, bracket) : token;
            if ("@e".equals(base)) {
                throw new CommandException("commands.catframe.title.playersOnly");
            }
            for (String selector : PLAYER_SELECTORS) {
                if (selector.equals(base)) {
                    return;
                }
            }
            throw new CommandException("commands.catframe.title.notLocal", playerName);
        }
        if (!token.equalsIgnoreCase(playerName)) {
            throw new CommandException("commands.catframe.title.notLocal", playerName);
        }
    }

    /**
     * Requires the exact argument count for fixed-arity sub-commands.
     * <p>校验定长子命令的精确参数个数。</p>
     */
    private void requireArgCount(String[] args, int expected, ICommandSender sender) {
        if (args.length != expected) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    /**
     * Joins the args from the given index with single spaces — the raw text/JSON argument.
     * <p>从指定下标起以单空格拼接参数 —— 即原始文本 / JSON 参数。</p>
     */
    private static String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    // ──── Tab completion / Tab 补全 ────

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            String name = player != null ? player.getCommandSenderName() : "";
            return getListOfStringsMatchingLastWord(args, "@p", "@a", "@r", "@s", name);
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, SUB_COMMANDS);
        }
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 0;
    }
}
