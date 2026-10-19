package decok.dfcdvadstf.catframe.command;

import decok.dfcdvadstf.catframe.datapack.mcfunction.FunctionManager;
import decok.dfcdvadstf.catframe.datapack.mcfunction.McFunction;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code /function} command (worklist D5, reconsidered).
 * <pre>{@code
 *   /function <ns:path>     run one datapack function
 *   /function #<ns:tag>     run every function of a function tag
 * }</pre>
 * <p>
 * Registered on the server side (via {@code FMLServerStartingEvent}). Functions
 * run with the caller as sender, like vanilla.
 * </p>
 */
public class CommandFunction extends CommandBase {

    @Override
    public String getCommandName() {
        return "function";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "commands.catframe.function.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1 || args[0].isEmpty()) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        String target = args[0];
        boolean tag = target.startsWith("#");
        ResourceLocation location = parse(tag ? target.substring(1) : target);

        if (tag) {
            if (!FunctionManager.getTagIds().contains(location)) {
                throw new CommandException("commands.catframe.function.unknown_tag", target);
            }
            int results = FunctionManager.runTag(location, sender);
            sender.addChatMessage(new ChatComponentTranslation("commands.catframe.function.tag.done", target, results));
            return;
        }

        McFunction function = FunctionManager.get(location);
        if (function == null) {
            throw new CommandException("commands.catframe.function.unknown", target);
        }
        int results = FunctionManager.run(function, sender);
        sender.addChatMessage(new ChatComponentTranslation("commands.catframe.function.done", target, results));
    }

    private static ResourceLocation parse(String text) {
        try {
            return new ResourceLocation(text);
        } catch (RuntimeException e) {
            throw new CommandException("commands.catframe.function.invalid_id", text);
        }
    }

    @Override
    public List<?> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length != 1) {
            return null;
        }
        List<String> options = new ArrayList<>();
        for (ResourceLocation id : FunctionManager.getFunctionIds()) {
            options.add(id.toString());
        }
        for (ResourceLocation id : FunctionManager.getTagIds()) {
            options.add("#" + id);
        }
        return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
    }
}
