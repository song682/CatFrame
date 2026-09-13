package decok.dfcdvadstf.catframe.command;

import decok.dfcdvadstf.catframe.network.OverlayNetwork;
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
 * {@code /title} 服务端命令 —— 复刻高版本 {@code /title} 的完整语法，
 * 由服务端向任意在线玩家发送 Title / ActionBar。
 * </p>
 * <pre>{@code
 *   /title <targets> (clear|reset)
 *   /title <targets> (title|subtitle|actionbar) <text>
 *   /title <targets> times <fadeIn> <stay> <fadeOut>
 * }</pre>
 * <p>
 * Server-side {@code /title} command — full modern syntax, usable by the server
 * (console, command blocks, or OPs) to send Title / ActionBar to any online player(s).
 * </p>
 *
 * <h3>与客户端命令的关系 / Relationship with the client command</h3>\n * <p>
 * 客户端 {@code CommandTitle} 经 {@code ClientCommandHandler} 注册，会拦截玩家输入。
 * 单人/局域网模式下本地执行；多人联机时转发为同名 {@code /title} 到达本命令。
 * 服务端控制台、命令方块以及无客户端 mod 的场景直接由本命令处理。
 * <br>The client {@code CommandTitle} registers via {@code CommandHandler} and
 * intercepts player input. In singleplayer it executes locally; on multiplayer it
 * forwards as {@code /title} to reach this command. Server console, command blocks,
 * and cases without the client mod are handled directly by this command.
 * </p>
 */
public class CommandTitleServer extends CommandBase {

    private static final String[] SUB_COMMANDS = {"title", "subtitle", "actionbar", "times", "clear", "reset"};

    @Override
    public String getCommandName() {
        return "title";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "commands.catframe.title.server.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        EntityPlayerMP[] targets = resolveTargets(sender, args[0]);
        if (targets.length == 0) {
            throw new PlayerNotFoundException("commands.catframe.title.noTargets");
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        if ("clear".equals(sub)) {
            requireArgCount(args, 2);
            sendToAll(targets, PacketTitleOverlay.simple(PacketTitleOverlay.Action.CLEAR));
            notifySender(sender, "commands.catframe.title.cleared", targets.length);
        } else if ("reset".equals(sub)) {
            requireArgCount(args, 2);
            sendToAll(targets, PacketTitleOverlay.simple(PacketTitleOverlay.Action.RESET));
            notifySender(sender, "commands.catframe.title.reset", targets.length);
        } else if ("times".equals(sub)) {
            requireArgCount(args, 5);
            int fadeIn = parseIntWithMin(sender, args[2], 0);
            int stay = parseIntWithMin(sender, args[3], 0);
            int fadeOut = parseIntWithMin(sender, args[4], 0);
            sendToAll(targets, PacketTitleOverlay.times(fadeIn, stay, fadeOut));
            notifySender(sender, "commands.catframe.title.times", targets.length);
        } else if ("title".equals(sub) || "subtitle".equals(sub) || "actionbar".equals(sub)) {
            if (args.length < 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
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
            notifySender(sender, "commands.catframe.title." + sub, targets.length);
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    // ──── Target resolution / 目标解析 ────

    private static EntityPlayerMP[] resolveTargets(ICommandSender sender, String token) {
        if (token.startsWith("@e") && (token.length() == 2 || token.charAt(2) == '[')) {
            throw new CommandException("commands.catframe.title.playersOnly");
        }
        if (token.startsWith("@")) {
            EntityPlayerMP[] matched = net.minecraft.command.PlayerSelector.matchPlayers(sender, token);
            if (matched != null && matched.length > 0) {
                return matched;
            }
            throw new PlayerNotFoundException();
        }
        EntityPlayerMP player = MinecraftServer.getServer().getConfigurationManager().func_152612_a(token);
        if (player != null) {
            return new EntityPlayerMP[]{player};
        }
        throw new PlayerNotFoundException();
    }

    // ──── Helpers ────

    private static void sendToAll(EntityPlayerMP[] targets, cpw.mods.fml.common.network.simpleimpl.IMessage message) {
        for (EntityPlayerMP player : targets) {
            OverlayNetwork.INSTANCE.sendTo(message, player);
        }
    }

    private static void notifySender(ICommandSender sender, String translationKey, int targetCount) {
        sender.addChatMessage(new ChatComponentTranslation(translationKey, targetCount));
    }

    private void requireArgCount(String[] args, int expected) {
        if (args.length != expected) {
            throw new WrongUsageException(getCommandUsage(null));
        }
    }

    private static String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    // ──── Tab completion ────

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, getPlayerNames());
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, SUB_COMMANDS);
        }
        return null;
    }

    private static String[] getPlayerNames() {
        @SuppressWarnings("unchecked")
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
