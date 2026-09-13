package decok.dfcdvadstf.catframe.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * <p>
 * Overlay 网络通道 —— 持有 {@link SimpleNetworkWrapper} 单例并注册所有自定义消息。
 * </p>
 * <p>
 * Overlay network channel — holds the {@link SimpleNetworkWrapper} singleton and
 * registers all custom messages.
 * </p>
 */
public final class OverlayNetwork {

    /** Channel name for overlay packets / 覆盖层包使用的通道名 */
    private static final String CHANNEL_NAME = "catframe";

    /** The shared network wrapper instance / 共享的网络包装实例 */
    public static SimpleNetworkWrapper INSTANCE;

    private OverlayNetwork() {
    }

    /**
     * Initialise the channel and register all message types.
     * Called from {@code CommonProxy.preInit}.
     * <p>初始化通道并注册所有消息类型。由 {@code CommonProxy.preInit} 调用。</p>
     */
    public static void init() {
        INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);

        // Discriminator 0: S2C title / actionbar overlay packet
        // 鉴别符 0：服务端→客户端 标题/动作栏覆盖层包
        INSTANCE.registerMessage(
                PacketTitleOverlayHandler.class,
                PacketTitleOverlay.class,
                0,
                Side.CLIENT
        );
    }
}
