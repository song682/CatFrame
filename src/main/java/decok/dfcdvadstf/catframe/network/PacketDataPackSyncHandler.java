package decok.dfcdvadstf.catframe.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import decok.dfcdvadstf.catframe.datapack.client.ClientDataSync;

/**
 * <p>
 * Client-side handler — receives {@link PacketDataPackSync} snapshots from the
 * server and forwards them to the client-side sync registry, which dispatches
 * by loader id (worklist C5).
 * </p>
 */
public class PacketDataPackSyncHandler implements IMessageHandler<PacketDataPackSync, IMessage> {

    @Override
    public IMessage onMessage(PacketDataPackSync message, MessageContext ctx) {
        ClientDataSync.apply(message.getLoaderId(), message.getPayloadJson());
        return null;
    }
}
