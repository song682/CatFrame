package decok.dfcdvadstf.catframe.datapack.mcfunction;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import decok.dfcdvadstf.catframe.tags.TagLoader;
import decok.dfcdvadstf.catframe.tags.TagRegistry;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.ICommandManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * State and runner for datapack functions (worklist D5, reconsidered).
 * <p>
 * Functions are plain command lists, so execution does not need a command
 * tree: each line is handed to the server's own {@link ICommandManager}, the
 * same entry point typed chat commands use. Function tags reuse the generic
 * {@link TagLoader}, so cross-tag references, cycle detection and empty-tag
 * warnings behave exactly like item and block tags.
 * </p>
 * <p>
 * Vanilla hooks are honored: {@code #minecraft:load} runs once per apply pass
 * and {@code #minecraft:tick} runs at the start of every server tick.
 * </p>
 */
public final class FunctionManager {

    private static final Logger LOGGER = LogManager.getLogger(FunctionManager.class);

    /** Vanilla tag run after every (re)load. */
    public static final ResourceLocation TAG_LOAD = new ResourceLocation("minecraft", "load");
    /** Vanilla tag run every server tick. */
    public static final ResourceLocation TAG_TICK = new ResourceLocation("minecraft", "tick");

    /** Maximum nested {@code /function} depth; guards against unbounded recursion. */
    public static final int MAX_CALL_DEPTH = 64;

    private static final Map<ResourceLocation, McFunction> FUNCTIONS = new LinkedHashMap<>();

    private static final TagLoader<McFunction> TAGS = new TagLoader<>(TagRegistry.FUNCTIONS,
            new TagLoader.ElementLookup<McFunction>() {
                @Override
                public Optional<? extends McFunction> get(ResourceLocation id, boolean required) {
                    return Optional.ofNullable(FUNCTIONS.get(id));
                }
            });

    private static int callDepth;

    private FunctionManager() {
    }

    /** Function tag loader (load/build driven by {@code FunctionsContentLoader}). */
    public static TagLoader<McFunction> tagLoader() {
        return TAGS;
    }

    /** Replaces the function table; called by the loader on every pass. */
    public static void setFunctions(Map<ResourceLocation, McFunction> functions) {
        FUNCTIONS.clear();
        FUNCTIONS.putAll(functions);
    }

    /** Clears functions; function tags are cleared by their loader. */
    public static void clearFunctions() {
        FUNCTIONS.clear();
    }

    /** @return the function with the given id, or {@code null}. */
    public static McFunction get(ResourceLocation id) {
        return FUNCTIONS.get(id);
    }

    /** @return every loaded function id, in load order. */
    public static Set<ResourceLocation> getFunctionIds() {
        return Collections.unmodifiableSet(FUNCTIONS.keySet());
    }

    /** @return every built function tag id. */
    public static Set<ResourceLocation> getTagIds() {
        return TAGS.getAllTagNames();
    }

    /** @return the functions of a tag, flattened; empty when the tag is unknown. */
    public static List<McFunction> getTag(ResourceLocation id) {
        return TAGS.getTagContents(id);
    }

    /**
     * Runs one function as the given sender.
     *
     * @return the sum of successful command results (vanilla semantics)
     */
    public static int run(McFunction function, ICommandSender sender) {
        if (function == null || sender == null) {
            return 0;
        }
        ICommandManager commands = commandManager();
        if (commands == null) {
            return 0;
        }
        if (callDepth >= MAX_CALL_DEPTH) {
            LOGGER.error("Function call depth exceeded {} while running {}; call skipped",
                    MAX_CALL_DEPTH, function.getId());
            return 0;
        }
        int successes = 0;
        callDepth++;
        try {
            for (McFunction.Line line : function.getLines()) {
                try {
                    successes += commands.executeCommand(sender, line.getCommand());
                } catch (RuntimeException e) {
                    LOGGER.error("Function {} line {} failed: {}", function.getId(), line.getNumber(), line.getCommand(), e);
                }
            }
        } finally {
            callDepth--;
        }
        return successes;
    }

    /**
     * Runs every function of a tag, in tag order.
     *
     * @return the sum of successful command results over all functions
     */
    public static int runTag(ResourceLocation tag, ICommandSender sender) {
        int successes = 0;
        for (McFunction function : getTag(tag)) {
            successes += run(function, sender);
        }
        return successes;
    }

    /** Runs {@code #minecraft:load} as the server console; no-op without a running server. */
    public static void runLoadTag() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            runTag(TAG_LOAD, server);
        }
    }

    private static ICommandManager commandManager() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null ? null : server.getCommandManager();
    }

    /** Registers the tick hook on the FML event bus. Called from the common proxy's preInit. */
    public static void register() {
        FMLCommonHandler.instance().bus().register(new TickHandler());
    }

    /** Runs {@code #minecraft:tick} at the start of every server tick. */
    public static final class TickHandler {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.START || FUNCTIONS.isEmpty()) {
                return;
            }
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                runTag(TAG_TICK, server);
            }
        }
    }
}
