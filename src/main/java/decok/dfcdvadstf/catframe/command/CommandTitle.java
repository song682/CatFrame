package decok.dfcdvadstf.catframe.command;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.relauncher.Side;
import decok.dfcdvadstf.catframe.network.OverlayNetwork;
import decok.dfcdvadstf.catframe.network.PacketTitleOverlay;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.Title;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import java.util.List;
import java.util.Locale;

/**
 * <p>
 * {@code /title} 命令 —— 复刻高版本 {@code /title} 的完整语法，
 * 同时承担客户端与服务端职责：
 * </p>
 * <pre>{@code
 *   /title <targets> (clear|reset)
 *   /title <targets> (title|subtitle|actionbar) <text>
 *   /title <targets> times <fadeIn> <stay> <fadeOut>
 * }</pre>
 * <p>
 * The {@code /title} command — full modern {@code /title} syntax as above,
 * combining client and server responsibilities in a single class.
 * </p>
 *
 * <h3>客户端侧 / Client side</h3>
 * <p>
 * 通过 {@code ClientCommandHandler} 注册，拦截玩家在聊天框中输入的 {@code /title}。
 * 单人/局域网模式下本地执行，{@code <targets>} 收敛为本地玩家匹配；
 * 多人联机时自动将输入转发为同名 {@code /title} 发往服务端。
 * <br>Registered via {@code ClientCommandHandler}, intercepts player-typed {@code /title}.
 * In singleplayer / LAN, executes locally; on multiplayer, auto-forwards as
 * {@code /title} to the server.
 * </p>
 *
 * <h3>服务端侧 / Server side</h3>
 * <p>
 * 通过 {@code FMLServerStartingEvent} 注册，处理来自服务端控制台、命令方块、
 * OP 玩家以及客户端转发的请求。使用 {@code PlayerSelector} 解析目标选择器，
 * 通过 S2C 网络包向任意在线玩家下发 Title / ActionBar。
 * <br>Registered via {@code FMLServerStartingEvent}, handles requests from the server
 * console, command blocks, OPs and forwarded client input. Resolves target selectors
 * via {@code PlayerSelector} and pushes Title / ActionBar to any online player via
 * S2C network packets.
 * </p>
 *
 * <h3>文本参数 / Text argument</h3>
 * <p>
 * {@code <text>} 由第 3 个参数起以空格拼接，经 {@link Text#fromJson(String)} 宽容解析：
 * 合法的原始 JSON 文本组件（含样式与 {@code extra}）按富文本处理，非 JSON 输入降级为字面文本。
 * <br>{@code <text>} joins the remaining args with spaces and goes through the lenient
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
        return isServerSide()
                ? "commands.catframe.title.server.usage"
                : "commands.catframe.title.usage";
    }

    /**
     * Client side: always returns {@code true} — the command is client-local in
     * singleplayer and touches no server state, so the vanilla op-level gate does
     * not apply.
     * <br>Server side: delegates to {@link #getRequiredPermissionLevel()} (OP level 2).
     * <p>客户端侧：始终返回 {@code true} —— 单人模式下命令纯客户端本地执行、不触碰
     * 服务端状态，故不适用原版 OP 权限门槛。服务端侧：委托至
     * {@link #getRequiredPermissionLevel()}（OP 等级 2）。</p>
     */
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return !isServerSide() || getRequiredPermissionLevel() <= 2;
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

        if (isServerSide()) {
            processServerCommand(sender, args);
        } else {
            processClientCommand(sender, args);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Server-side logic / 服务端逻辑
    // ════════════════════════════════════════════════════════════════════

    /**
     * Server-side command processing: resolves target selectors via
     * {@code PlayerSelector} and dispatches S2C overlay packets to all matched players.
     * <p>服务端命令处理：通过 {@code PlayerSelector} 解析目标选择器，
     * 向所有匹配玩家发送 S2C 覆盖层包。</p>
     */
    private void processServerCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP[] targets = resolveTargets(sender, args[0]);
        if (targets.length == 0) {
            throw new PlayerNotFoundException("commands.catframe.title.noTargets");
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        if ("clear".equals(sub)) {
            requireArgCount(args, 2, sender);
            sendToAll(targets, PacketTitleOverlay.simple(PacketTitleOverlay.Action.CLEAR));
            notifySender(sender, "commands.catframe.title.cleared", targets.length);
        } else if ("reset".equals(sub)) {
            requireArgCount(args, 2, sender);
            sendToAll(targets, PacketTitleOverlay.simple(PacketTitleOverlay.Action.RESET));
            notifySender(sender, "commands.catframe.title.reset", targets.length);
        } else if ("times".equals(sub)) {
            requireArgCount(args, 5, sender);
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

    /**
     * Resolves the target token to an array of server players.
     * Rejects {@code @e}; accepts {@code @a/@p/@r/@s} (with optional selector args)
     * and plain player names looked up via {@code PlayerSelector} /
     * {@code MinecraftServer}.
     * <p>将目标令牌解析为服务端玩家数组。拒绝 {@code @e}；接受 {@code @a/@p/@r/@s}
     * （可附带选择器参数）以及通过 {@code PlayerSelector} / {@code MinecraftServer}
     * 查找的纯玩家名。</p>
     *
     * @throws CommandException      if {@code @e} is used / 使用了 {@code @e} 时抛出
     * @throws PlayerNotFoundException if no players match / 无玩家匹配时抛出
     */
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

    /**
     * Sends the given packet to every target player via the overlay network channel.
     * <p>通过覆盖层网络通道向每位目标玩家发送指定包。</p>
     */
    private static void sendToAll(EntityPlayerMP[] targets, IMessage message) {
        for (EntityPlayerMP player : targets) {
            OverlayNetwork.INSTANCE.sendTo(message, player);
        }
    }

    private static void notifySender(ICommandSender sender, String translationKey, int targetCount) {
        sender.addChatMessage(new ChatComponentTranslation(translationKey, targetCount));
    }

    // ════════════════════════════════════════════════════════════════════
    //  Client-side logic / 客户端逻辑
    // ════════════════════════════════════════════════════════════════════

    /**
     * Client-side command processing.
     * In multiplayer, forwards the raw input as {@code /title} to the server.
     * In singleplayer / LAN, executes locally against the local player.
     * <p>客户端命令处理。多人联机时将原始输入转发为 {@code /title} 到服务端；
     * 单人/局域网模式下对本地玩家执行。</p>
     */
    private void processClientCommand(ICommandSender sender, String[] args) {
        Minecraft mc = Minecraft.getMinecraft();

        // Multiplayer: forward to server for server-side target resolution
        // 多人联机：转发到服务端，由服务端解析目标选择器
        if (!mc.isSingleplayer()) {
            mc.thePlayer.sendChatMessage("/title " + joinArgs(args, 0));
            return;
        }

        // Singleplayer: execute locally
        EntityPlayer player = mc.thePlayer;
        if (player == null) {
            throw new CommandException("commands.catframe.title.noPlayer");
        }
        String playerName = player.getCommandSenderName();
        requireLocalTarget(args[0], playerName);

        String sub = args[1].toLowerCase(Locale.ROOT);

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

    // ════════════════════════════════════════════════════════════════════
    //  Shared helpers / 共用辅助方法
    // ════════════════════════════════════════════════════════════════════

    /**
     * Detects whether the current execution context is the server side.
     * Uses {@link FMLCommonHandler#getEffectiveSide()} which checks the current
     * thread name — {@code "Server thread"} maps to {@link Side#SERVER}, everything
     * else to {@link Side#CLIENT}.
     * <p>检测当前执行上下文是否为服务端侧。使用 {@link FMLCommonHandler#getEffectiveSide()}
     * 依据当前线程名判断 —— {@code "Server thread"} 映射为 {@link Side#SERVER}，
     * 其余映射为 {@link Side#CLIENT}。</p>
     */
    private static boolean isServerSide() {
        return FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER;
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
            if (isServerSide()) {
                return getListOfStringsMatchingLastWord(args, getServerPlayerNames());
            }
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            String name = player != null ? player.getCommandSenderName() : "";
            return getListOfStringsMatchingLastWord(args, "@p", "@a", "@r", "@s", name);
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, SUB_COMMANDS);
        }
        return null;
    }

    private static String[] getServerPlayerNames() {
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
