package decok.dfcdvadstf.catframe.datapack;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import decok.dfcdvadstf.catframe.datapack.content.TintsContentLoader;
import decok.dfcdvadstf.catframe.network.OverlayNetwork;
import decok.dfcdvadstf.catframe.network.PacketDataPackSync;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server → client datapack sync (worklist C5).
 * <p>
 * The datapack pipeline is server-authoritative (decision E3), so content that
 * must exist on clients — currently tint declarations (C3) — travels through
 * {@link PacketDataPackSync} snapshots: whole snapshots, never increments.
 * </p>
 * <p>
 * Two triggers:
 * <ol>
 * <li>player login — the joining player receives the current snapshot
 *     ({@link PlayerLoginHandler}, registered on the FML event bus from the
 *     common proxy's preInit);</li>
 * <li>after every apply pass — every online player receives the fresh snapshot
 *     ({@link DataPackManager#apply()} calls {@link #broadcast()}; at server
 *     start the player list is empty, which is naturally a no-op).</li>
 * </ol>
 * Failures here are logged and never propagate into the apply pipeline.
 * </p>
 */
public final class DataPackSync {

    private static final Logger LOGGER = LogManager.getLogger(DataPackSync.class);

    private DataPackSync() {
    }

    /** Registers the login listener on the FML event bus. Called from proxy preInit. */
    public static void register() {
        FMLCommonHandler.instance().bus().register(new PlayerLoginHandler());
    }

    /** Sends every syncable content snapshot to all online players. */
    public static void broadcast() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        for (Object candidate : server.getConfigurationManager().playerEntityList) {
            if (candidate instanceof EntityPlayerMP) {
                sendTo((EntityPlayerMP) candidate);
            }
        }
    }

    /** Sends every syncable content snapshot to one player. */
    public static void sendTo(EntityPlayerMP player) {
        if (player == null || OverlayNetwork.INSTANCE == null) {
            return;
        }
        try {
            // Every content type that needs a client-side presence ships its snapshot
            // here; currently only tints (C3). Future syncable types extend this list.
            OverlayNetwork.INSTANCE.sendTo(
                    new PacketDataPackSync(TintsContentLoader.ID, TintsContentLoader.buildPayload()), player);
        } catch (RuntimeException e) {
            LOGGER.error("Datapack sync to {} failed", player.getCommandSenderName(), e);
        }
    }

    /** Login trigger: synchronizes the current snapshot to the joining player. */
    public static final class PlayerLoginHandler {

        @SubscribeEvent
        public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            // Client-side logins carry an EntityClientPlayerMP — only the server
            // side (dedicated server or the integrated server in singleplayer)
            // owns a datapack snapshot to send.
            if (event.player instanceof EntityPlayerMP) {
                sendTo((EntityPlayerMP) event.player);
            }
        }
    }
}
