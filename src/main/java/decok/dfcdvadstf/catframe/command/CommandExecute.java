package decok.dfcdvadstf.catframe.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/**
 * The {@code /execute} command: a 1.7.10 subset of the modern command (worklist D5).
 * <pre>{@code
 *   /execute as <players> <subcommand...>            run as each player (sender name)
 *   /execute at <players> <subcommand...>            run at each player's position and world
 *   /execute positioned <x> <y> <z> <subcommand...>  run at a position (~ = current, ~n = offset)
 *   /execute ... run <command>                       terminal: run the command
 * }</pre>
 * <p>
 * Chains such as {@code /execute as Steve at Steve run give @p apple} work
 * because each modifier wraps the sender and recurses into the rest of the
 * argument list.
 * </p>
 * <p>
 * Limits of 1.7.10: selectors resolve players only (no {@code @e}); the sender
 * interface has no command-entity accessor, so {@code as} changes the sender
 * name only; coordinates are integer block positions; no {@code if}/{@code unless}
 * or {@code store} clauses.
 * </p>
 * <p>
 * The {@code run} part goes through the server's own command manager, so any
 * registered command (including {@code /function}) works as the subcommand.
 * </p>
 */
public class CommandExecute extends CommandBase {

    private static final String RUN = "run";
    private static final String AS = "as";
    private static final String AT = "at";
    private static final String POSITIONED = "positioned";

    @Override
    public String getCommandName() {
        return "execute";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "commands.catframe.execute.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        execute(sender, args, 0);
    }

    /** Interprets the modifiers starting at {@code index}; recursive for chains. */
    private void execute(ICommandSender sender, String[] args, int index) {
        if (index >= args.length) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        String sub = args[index].toLowerCase(Locale.ROOT);

        if (RUN.equals(sub)) {
            if (index + 1 >= args.length) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            ICommandManager commands = MinecraftServer.getServer().getCommandManager();
            commands.executeCommand(sender, join(args, index + 1));
            return;
        }

        if (AS.equals(sub) || AT.equals(sub)) {
            if (index + 1 >= args.length) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            EntityPlayerMP[] targets = net.minecraft.command.PlayerSelector.matchPlayers(sender, args[index + 1]);
            if (targets.length == 0) {
                throw new PlayerNotFoundException();
            }
            for (EntityPlayerMP target : targets) {
                ICommandSender next = AS.equals(sub)
                        ? new RedirectedSender(sender, target.getCommandSenderName(), null, null)
                        : new RedirectedSender(sender, null, target.getPlayerCoordinates(), target.worldObj);
                execute(next, args, index + 2);
            }
            return;
        }

        if (POSITIONED.equals(sub)) {
            if (index + 3 >= args.length) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            ChunkCoordinates base = sender.getPlayerCoordinates();
            ChunkCoordinates position = new ChunkCoordinates(
                    coordinate(args[index + 1], base.posX),
                    coordinate(args[index + 2], base.posY),
                    coordinate(args[index + 3], base.posZ));
            execute(new RedirectedSender(sender, null, position, null), args, index + 4);
            return;
        }

        throw new CommandException("commands.catframe.execute.unknown", args[index]);
    }

    /** Absolute integer, {@code ~} (current value) or {@code ~n} (offset from current). */
    private static int coordinate(String token, int current) {
        try {
            if (token.startsWith("~")) {
                String offset = token.substring(1);
                return offset.isEmpty() ? current : current + (int) Math.floor(Double.parseDouble(offset));
            }
            return (int) Math.floor(Double.parseDouble(token));
        } catch (NumberFormatException e) {
            throw new CommandException("commands.catframe.execute.bad_coord", token);
        }
    }

    private static String join(String[] args, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                out.append(' ');
            }
            out.append(args[i]);
        }
        return out.toString();
    }

    @Override
    public List<?> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, AS, AT, POSITIONED, RUN);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (AS.equals(sub) || AT.equals(sub)) {
                return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
            }
        }
        return null;
    }

    /**
     * Sender decorator: every method delegates to the original sender except the
     * overridden name, position or world. Nested decorators compose naturally.
     */
    private static final class RedirectedSender implements ICommandSender {

        private final ICommandSender base;
        private final String name;
        private final ChunkCoordinates position;
        private final World world;

        RedirectedSender(ICommandSender base, String name, ChunkCoordinates position, World world) {
            this.base = base;
            this.name = name;
            this.position = position;
            this.world = world;
        }

        @Override
        public String getCommandSenderName() {
            return name != null ? name : base.getCommandSenderName();
        }

        @Override
        public IChatComponent func_145748_c_() {
            return name != null ? new ChatComponentText(name) : base.func_145748_c_();
        }

        @Override
        public void addChatMessage(IChatComponent message) {
            base.addChatMessage(message);
        }

        @Override
        public boolean canCommandSenderUseCommand(int level, String command) {
            return base.canCommandSenderUseCommand(level, command);
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return position != null ? position : base.getPlayerCoordinates();
        }

        @Override
        public World getEntityWorld() {
            return world != null ? world : base.getEntityWorld();
        }
    }
}
