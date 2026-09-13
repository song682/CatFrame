package decok.dfcdvadstf.catframe.command;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import decok.dfcdvadstf.catframe.network.CatFrameNetwork;
import decok.dfcdvadstf.catframe.network.PacketTitleOverlay;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import java.util.List;
import java.util.Locale;

/**
 * <p>
 * {@code /cftitle} 服务端命令 —— 复刻高版本 {@code /title} 的完整语法，
 * 由服务端 OP 向任意在线玩家发送 Title / ActionBar。
 * </p>
 * <pre>{@code
 *   /cftitle <targets> (clear|reset)
 *   /cftitle <targets> (title|subtitle|actionbar) <text>
 *   /cftitle <targets> times <fadeIn> <stay> <fadeOut>
 * }</pre>
 * <p>
 * Server-side {@code /cftitle} command — full modern {@code /title} syntax,
 * usable by server OPs to send Title / ActionBar to any online player(s).
 * </p>
 *
 * <h3>Why {@code /cftitle} instead of {@code /title} / 为什么用 {@code /cftitle}</h3>
 * <p>
 * CatFrame 的客户端命令 {@code /title} 经 {@code ClientCommandHandler} 注册，
 * 会拦截同名输入——若服务端也注册 {@code /title}，客户端那条会优先生效、遮蔽服务端版本。
 * 因此服务端使用 {@code /cftitle} 这一独立名称。当客户端检测到多人联机时，
 * {@code CommandTitle} 会自动将 {@code /title} 输入转发为 {@code /cftitle} 发往服务端。
 * <br>The client command {@code /title} registers via {@code ClientCommandHandler} and
 * intercepts matching input — if the server also registered {@code /title}, the client one
 * would take precedence and shadow it. The server therefore uses the distinct name
 * {@code /cftitle}. When the client detects a multiplayer connection, {@code CommandTitle}
 * automatically forwards {@code /title} input as {@code /cftitle} to the server.
 * </p>
 *
 * <h3>权限 / Permission</h3>
 * <p>
 * 需要 OP 权限等级 2（与原版 {@code /title} 一致）。
 * <br>Requires OP permission level 2 (same as vanilla {@code /title}).
 * </p>
 */
public class CommandTitleServer extends CommandBase {

    /** Sub-command literals for tab completion / 用于 Tab 补全的子命令字面量 */
    private static final String[] SUB_COMMANDS = {"title", "subtitle", "actionbar", "times", "clear", "reset"};

    @Override
    public String getCommandName() {
        return "cftitle";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "commands.catframe.cftitle.usage";
    }

    /**
     * Requires OP level 2, matching vanilla {@code /title}.
     * <p>需要 OP 等级 2，与原版 {@code /title} 一致。</p>
     */
    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        // ── Resolve targets / 解析目标玩家 ──
        EntityPlayerMP[] targets = resolveTargets(sender, args[0]);
        if (targets.length == 0) {
            throw new PlayerNotFoundException("commands.catframe.cftitle.noTargets");
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        if ("clear".equals(sub)) {
            requireArgCount(args, 2);
            sendToAll(targets, PacketTitleOverlay.simple(PacketTitleOverlay.Action.CLEAR));
            notifyOperators(sender, "commands.catframe.cftitle.cleared", targets.length);
        } else if ("reset".equals(sub)) {
            requireArgCount(args, 2);
            sendToAll(targets, PacketTitleOverlay.simple(PacketTitleOverlay.Action.RESET));
            notifyOperators(sender, "commands.catframe.cftitle.reset", targets.length);
        } else if ("times".equals(sub)) {
            requireArgCount(args, 5);
            int fadeIn = parseIntWithMin(sender, args[2], 0);
            int stay = parseIntWithMin(sender, args[3], 0);
            int fadeOut = parseIntWithMin(sender, args[4], 0);
            sendToAll(targets, PacketTitleOverlay.times(fadeIn, stay, fadeOut));
            notifyOperators(sender, "commands.catframe.cftitle.times", targets.length);
        } else if ("title".equals(sub) || "subtitle".equals(sub) || "actionbar".equals(sub)) {
            if (args.length < 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            // The text argument is joined from remaining args; send as raw JSON string.
            // The client will parse it via Text.fromJson() on receipt.
            // 文本参数由剩余参数拼接；以原始 JSON 字符串发送。
            // 客户端收到后经 Text.fromJson() 解析。
            String rawText = joinArgs(args, 2);
            Text text = Text.fromJson(rawText);
            String json = text.toJson();

            PacketTitleOverlay.Action action;
            if ("title".equals(sub)) {
                action = PacketTitleOverlay.Action.TITLE;
            } else if ("subtitle".equals(sub)) {
                action = PacketTitleOverlay.Action.SUBTITLE;
            } else {
                action = PacketTitleOverlay.Action.ACTIONBAR;
            }
            sendToAll(targets, PacketTitleOverlay.text(action, json));
            notifyOperators(sender, "commands.catframe.cftitle." + sub, targets.length);
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    // ──── Target resolution / 目标解析 ────

    /**
     * Resolves the target token to an array of online players. Accepts player-type
     * selectors ({@code @p/@a/@r/@s} with optional arguments) or a player name.
     * {@code @e} is rejected as vanilla {@code /title} requires player targets.
     * <p>将目标令牌解析为在线玩家数组。接受玩家类选择器（{@code @p/@a/@r/@s}，
     * 可带参数）或玩家名。{@code @e} 按原版 {@code /title} 仅限玩家目标的规则拒绝。</p>
     */
    private static EntityPlayerMP[] resolveTargets(ICommandSender sender, String token) {
        // Reject @e — title targets players only
        // 拒绝 @e —— title 仅以玩家为目标
        if (token.startsWith("@e") && (token.length() == 2 || token.charAt(2) == '[')) {
            throw new CommandException("commands.catframe.cftitle.playersOnly");
        }

        // Try selector-based resolution first (@p, @a, @r, @s with optional args)
        // 先尝试选择器解析（@p/@a/@r/@s，可带方括号参数）
        if (token.startsWith("@")) {
            EntityPlayerMP[] matched = net.minecraft.command.PlayerSelector.matchPlayers(sender, token);
            if (matched != null && matched.length > 0) {
                return matched;
            }
            throw new PlayerNotFoundException();
        }

        // Fall back to exact name lookup / 否则按精确名字查找
        EntityPlayerMP player = MinecraftServer.getServer().getConfigurationManager().func_152612_a(token);
        if (player != null) {
            return new EntityPlayerMP[]{player};
        }
        throw new PlayerNotFoundException();
    }

    // ──── Helpers / 辅助方法 ────

    /**
     * Sends a packet to all target players via the CatFrame network channel.
     * <p>通过 CatFrame 网络通道向所有目标玩家发送包。</p>
     */
    private static void sendToAll(EntityPlayerMP[] targets, IMessage message) {
        for (EntityPlayerMP player : targets) {
            CatFrameNetwork.INSTANCE.sendTo(message, player);
        }
    }

    /**
     * Sends a feedback message to the command sender and logs to operator chat.
     * <p>向命令发送者发送反馈消息并记录到操作员聊天。</p>
     */
    private static void notifyOperators(ICommandSender sender, String translationKey, int targetCount) {
        sender.addChatMessage(new ChatComponentTranslation(translationKey, targetCount));
    }

    /**
     * Requires the exact argument count for fixed-arity sub-commands.
     * <p>校验定长子命令的精确参数个数。</p>
     */
    private void requireArgCount(String[] args, int expected) {
        if (args.length != expected) {
            throw new WrongUsageException("commands.catframe.cftitle.usage");
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
            // Tab-complete player names and selectors
            // Tab 补全玩家名和选择器
            return getListOfStringsMatchingLastWord(args,
                    getPlayerNames());
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, SUB_COMMANDS);
        }
        return null;
    }

    /**
     * Returns the names of all currently online players for tab completion.
     * <p>返回所有在线玩家名用于 Tab 补全。</p>
     */
    private static String[] getPlayerNames() {
        List<EntityPlayerMP> players = MinecraftServer.getServer().getConfigurationManager().playerEntityList;
        String[] names = new String[players.size()];
        for (int i = 0; i < players.size(); i++) {
            names[i] = players.get(i).getCommandSenderName();
        }
        return names;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 0;
    }
}
