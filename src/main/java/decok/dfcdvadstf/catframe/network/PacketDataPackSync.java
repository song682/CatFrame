package decok.dfcdvadstf.catframe.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * <p>
 * Server → Client datapack content sync (worklist C5). Generic transport: one
 * packet carries one content type's snapshot — a loader id plus the loader's
 * JSON payload — so a new syncable content type only needs a client-side
 * applier, not a new message type.
 * </p>
 *
 * <h3>Wire format</h3>
 * <ol>
 *   <li>{@code UTF8 string} — loader id (e.g. {@code "tints"})</li>
 *   <li>{@code UTF8 string} — JSON payload (loader-specific snapshot)</li>
 * </ol>
 *
 * <p>
 * Snapshots are re-sent whole (never incremental) on login and after every
 * apply pass; an empty array payload is meaningful and clears the client-side
 * state of that loader. Payloads are expected to stay small (declarations
 * comparable to a few thousand entries).
 * </p>
 */
public class PacketDataPackSync implements IMessage {

    private String loaderId;
    private String payloadJson;

    /** No-arg constructor required by Forge */
    public PacketDataPackSync() {
    }

    public PacketDataPackSync(String loaderId, String payloadJson) {
        this.loaderId = loaderId;
        this.payloadJson = payloadJson;
    }

    public String getLoaderId() {
        return loaderId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    // ──── IMessage ────

    @Override
    public void fromBytes(ByteBuf buf) {
        loaderId = ByteBufUtils.readUTF8String(buf);
        payloadJson = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, loaderId == null ? "" : loaderId);
        ByteBufUtils.writeUTF8String(buf, payloadJson == null ? "" : payloadJson);
    }
}
