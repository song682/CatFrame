package decok.dfcdvadstf.catframe.datapack.client;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side receive end of the datapack sync channel (worklist C5).
 * <p>
 * {@link decok.dfcdvadstf.catframe.network.PacketDataPackSync} carries one
 * content type's snapshot per packet (loader id + JSON payload); this registry
 * dispatches it to the matching applier and owns the disconnect cleanup so no
 * server content can leak into the next session.
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class ClientDataSync {

    private static final Logger LOGGER = LogManager.getLogger(ClientDataSync.class);

    /** One content type's client-side applier. */
    public interface Applier {

        /** Installs a full snapshot; an empty payload clears this type's state. */
        void apply(String payloadJson);

        /** Removes everything this applier installed (server disconnect). */
        void clear();
    }

    /** Appliers keyed by loader id. Empty at present — no content type is syncable. */
    private static final Map<String, Applier> APPLIERS = new LinkedHashMap<>();

    private ClientDataSync() {
    }

    /** Dispatches one snapshot by loader id, as received from the server. */
    public static void apply(String loaderId, String payloadJson) {
        Applier applier = APPLIERS.get(loaderId);
        if (applier == null) {
            LOGGER.warn("Ignoring datapack sync for unknown loader id '{}'", loaderId);
            return;
        }
        applier.apply(payloadJson);
    }

    /** Clears every applier — the client must not keep server content after disconnect. */
    public static void clearAll() {
        for (Applier applier : APPLIERS.values()) {
            applier.clear();
        }
    }

    /** Registers the disconnect listener on the FML event bus. Called from ClientProxy. */
    public static void register() {
        FMLCommonHandler.instance().bus().register(new DisconnectHandler());
    }

    /** Drops all synced content when the client leaves a server (or a singleplayer world). */
    public static final class DisconnectHandler {

        @SubscribeEvent
        public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            clearAll();
        }
    }
}
