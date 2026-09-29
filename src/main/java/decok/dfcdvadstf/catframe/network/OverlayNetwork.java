package decok.dfcdvadstf.catframe.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * <p>
 * Overlay network channel — holds the {@link SimpleNetworkWrapper} singleton and
 * registers all custom messages.
 * </p>
 */
public final class OverlayNetwork {

    /** Channel name for overlay packets */
    private static final String CHANNEL_NAME = "catframe";

    /** The shared network wrapper instance */
    public static SimpleNetworkWrapper INSTANCE;

    private OverlayNetwork() {
    }

    /**
     * Initialise the channel and register all message types.
     * Called from {@code CommonProxy.preInit}.
     */
    public static void init() {
        INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);

        // Discriminator 0: S2C title / actionbar overlay packet
        INSTANCE.registerMessage(
                PacketTitleOverlayHandler.class,
                PacketTitleOverlay.class,
                0,
                Side.CLIENT
        );
    }
}
