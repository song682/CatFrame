package decok.dfcdvadstf.catframe.mixin.middle.render;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chunk-boundary render refresh on chunk <em>load</em> (the IWorldAccess notification bypass).
 *
 * <p>Vanilla asymmetry (verified in 1.7.10 sources): {@code WorldClient.doPreChunk} marks the
 * full block range for re-render only on the <b>unload</b> branch, and both chunk-data handlers
 * ({@code handleChunkData} / {@code handleMapChunkBulk}) mark <b>only the received chunk's own
 * range</b> after {@code fillChunk}. So a freshly loaded neighbour never re-meshes the existing
 * boundary sections of adjacent chunks: connection geometry (panes / walls / fences / stairs
 * corners and plain {@code shouldSideBeRendered} faces) across the chunk seam stays stale until
 * some later block update touches it.
 *
 * <p>Fix shape: append one extra notification through the official {@code World} →
 * {@code IWorldAccess} multicast surface ({@link WorldClient#markBlockRangeForRenderUpdate}),
 * one block wide along the four edges of the loaded chunk — "look at the neighbourhood only".
 * The ±1 section dilation needed for seam geometry is not our concern: it is already built into
 * {@code RenderGlobal.markBlocksForUpdate}. Injection is at the handler TAIL, after
 * {@code fillChunk}, so the neighbour rebuild reads the new chunk's block data (deferred rebuild
 * via {@code worldRenderersToUpdate} runs on the next render pass).
 *
 * <p>The unload path already re-marks the neighbours via {@code doPreChunk(x, z, false)} and is
 * skipped here.
 */
@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {

    @Shadow
    private WorldClient clientWorldController;

    /**
     * Single-chunk upload (S21): after the vanilla self-range mark, ping the four neighbour
     * edge strips. The unload early-return (load flag set, extent 0) is not a load: skip.
     */
    @Inject(method = "handleChunkData", at = @At("TAIL"))
    private void catframe$markNeighborsOnChunkData(S21PacketChunkData packet, CallbackInfo ci) {
        if (packet.func_149274_i() && packet.func_149276_g() == 0) {
            return;
        }
        catframe$markBoundaryNeighbors(packet.func_149273_e(), packet.func_149271_f());
    }

    /**
     * Bulk chunk upload (S26): every entry in the packet has been filled and self-marked by
     * vanilla inside the loop; mark the neighbour edges of each column afterwards.
     */
    @Inject(method = "handleMapChunkBulk", at = @At("TAIL"))
    private void catframe$markNeighborsOnChunkBulk(S26PacketMapChunkBulk packet, CallbackInfo ci) {
        for (int i = 0; i < packet.func_149254_d(); i++) {
            catframe$markBoundaryNeighbors(packet.func_149255_a(i), packet.func_149253_b(i));
        }
    }

    /**
     * Mark the one-block-wide ring hugging the loaded chunk column (west/east columns span the
     * corners too, north/south rows stay inside the column's own x-extent to avoid overlap).
     * Full height like the vanilla chunk-load marks (0..256).
     */
    private void catframe$markBoundaryNeighbors(int chunkX, int chunkZ) {
        WorldClient world = this.clientWorldController;
        if (world == null) {
            return;
        }
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        world.markBlockRangeForRenderUpdate(x0 - 1, 0, z0 - 1, x0 - 1, 256, z0 + 16);   // west column
        world.markBlockRangeForRenderUpdate(x0 + 16, 0, z0 - 1, x0 + 16, 256, z0 + 16); // east column
        world.markBlockRangeForRenderUpdate(x0, 0, z0 - 1, x0 + 15, 256, z0 - 1);       // north row
        world.markBlockRangeForRenderUpdate(x0, 0, z0 + 16, x0 + 15, 256, z0 + 16);     // south row
    }
}
