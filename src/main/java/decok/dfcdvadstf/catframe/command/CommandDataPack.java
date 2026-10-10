package decok.dfcdvadstf.catframe.command;

import decok.dfcdvadstf.catframe.datapack.DataPack;
import decok.dfcdvadstf.catframe.datapack.DataPackErrors;
import decok.dfcdvadstf.catframe.datapack.DataPackManager;
import decok.dfcdvadstf.catframe.datapack.DataPackRepository;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentTranslation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <p>
 * The {@code /datapack} command — command surface of the datapack pipeline
 * (worklist A5):
 * </p>
 * <pre>{@code
 *   /datapack list                — list all discovered packs and their state
 *   /datapack enable <id>         — enable a pack and reapply immediately
 *   /datapack disable <id>        — disable a pack and reapply immediately
 *   /datapack reload              — rescan all sources and reapply
 * }</pre>
 * <p>
 * Registered on the server side only (via {@code FMLServerStartingEvent}) —
 * the pipeline is server-authoritative (decision E3). In singleplayer the
 * integrated server executes it locally; on multiplayer the vanilla client
 * forwards the command text as chat, which the server routes into its command
 * manager, so a dedicated registration is all that is needed.
 * </p>
 */
public class CommandDataPack extends CommandBase {

    @Override
    public String getCommandName() {
        return "datapack";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "commands.catframe.datapack.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(sub)) {
            list(sender);
        } else if ("reload".equals(sub)) {
            reload(sender);
        } else if ("enable".equals(sub) || "disable".equals(sub)) {
            if (args.length < 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            setEnabled(sender, args[1], "enable".equals(sub));
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void list(ICommandSender sender) {
        DataPackRepository repository = DataPackManager.getRepository();
        List<DataPack> packs = repository == null ? new ArrayList<DataPack>() : repository.getPacks();
        sender.addChatMessage(new ChatComponentTranslation("commands.catframe.datapack.list.header", packs.size()));
        if (packs.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("commands.catframe.datapack.list.empty"));
            return;
        }
        for (DataPack pack : packs) {
            String description = pack.getMetadata().getDescription();
            if (description == null || description.isEmpty()) {
                description = "-";
            }
            sender.addChatMessage(new ChatComponentTranslation(
                    pack.isEnabled()
                            ? "commands.catframe.datapack.list.entry.enabled"
                            : "commands.catframe.datapack.list.entry.disabled",
                    pack.getId(), pack.getSourceId(), description));
        }
    }

    private void reload(ICommandSender sender) {
        DataPackManager.reload();
        DataPackRepository repository = DataPackManager.getRepository();
        DataPackErrors errors = DataPackManager.getLastErrors();
        int active = repository == null ? 0 : repository.getEnabledPacksByPriority().size();
        sender.addChatMessage(new ChatComponentTranslation(
                "commands.catframe.datapack.reload.done", active, errors.size()));
    }

    private void setEnabled(ICommandSender sender, String packId, boolean enable) {
        if (!DataPackManager.setEnabled(packId, enable)) {
            throw new CommandException("commands.catframe.datapack.unknown", packId);
        }
        sender.addChatMessage(new ChatComponentTranslation(
                enable ? "commands.catframe.datapack.enabled" : "commands.catframe.datapack.disabled", packId));
    }

    @Override
    public List<?> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "enable", "disable", "reload");
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("enable".equals(sub) || "disable".equals(sub)) {
                DataPackRepository repository = DataPackManager.getRepository();
                if (repository == null) {
                    return null;
                }
                List<String> ids = new ArrayList<>();
                for (DataPack pack : repository.getPacks()) {
                    ids.add(pack.getId());
                }
                return getListOfStringsMatchingLastWord(args, ids.toArray(new String[0]));
            }
        }
        return null;
    }
}
