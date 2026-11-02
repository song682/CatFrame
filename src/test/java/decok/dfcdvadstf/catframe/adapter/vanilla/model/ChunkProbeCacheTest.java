package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Verification for {@link ChunkProbeCache}: the decorator is only handed out inside an
 * open window, repeated reads of one cell hit the delegate once per window, and closing the
 * window drops every cached value (a later window re-reads mutated world state — no
 * cross-window residue).
 * <p>
 * The fake world counts raw {@code getBlock}/{@code getBlockMetadata} arrivals and records
 * the probe trace, which is enough to observe dedup without booting the game.
 */
public class ChunkProbeCacheTest {

    /** Cell key for the fake's storage and the trace assertions. */
    private static long cell(int x, int y, int z) {
        return ((long) x << 40) ^ ((long) y << 20) ^ z;
    }

    private static final class CountingWorld implements IBlockAccess {
        final Map<Long, Integer> meta = new HashMap<>();
        final List<String> trace = new ArrayList<>();
        int blockReads;
        int metaReads;

        void set(int x, int y, int z, int m) {
            meta.put(cell(x, y, z), m);
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            blockReads++;
            trace.add("b" + x + "," + y + "," + z);
            // Distinct block instance per meta value; null models air. Identity survives
            // the cache round-trip, so shared results are assertable with assertSame.
            Integer m = meta.get(cell(x, y, z));
            return m == null ? null : BLOCK_MARKERS[m & 15];
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            metaReads++;
            trace.add("m" + x + "," + y + "," + z);
            Integer m = meta.get(cell(x, y, z));
            return m == null ? 0 : m;
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) { return null; }

        @Override
        public int getLightBrightnessForSkyBlocks(int x, int y, int z, int p) { return 0; }

        @Override
        public int isBlockProvidingPowerTo(int x, int y, int z, int side) { return 0; }

        @Override
        public boolean isAirBlock(int x, int y, int z) { return false; }

        @Override
        public BiomeGenBase getBiomeGenForCoords(int x, int z) { return null; }

        @Override
        public int getHeight() { return 256; }

        @Override
        public boolean extendedLevelsInChunkCache() { return false; }

        @Override
        public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean d) { return d; }
    }

    /** Marker "blocks" — distinct plain Block instances; only their identity matters. */
    private static final Block[] BLOCK_MARKERS = new Block[16];
    static {
        for (int i = 0; i < 16; i++) BLOCK_MARKERS[i] = new MarkerBlock();
    }

    private static final class MarkerBlock extends Block {
        MarkerBlock() {
            super(Material.wood);
            // marker identity only — no registry / texture boot needed
        }
    }

    /** Pane-style probe: the cell itself plus its four horizontal neighbors. */
    private static void probeNeighbors(IBlockAccess w, int x, int y, int z) {
        w.getBlock(x, y, z);
        w.getBlockMetadata(x, y, z);
        w.getBlock(x + 1, y, z);
        w.getBlock(x - 1, y, z);
        w.getBlock(x, y, z + 1);
        w.getBlock(x, y, z - 1);
    }

    @Test
    public void wrapOutsideWindowReturnsWorldItself() {
        CountingWorld world = new CountingWorld();
        assertSame("no window: wrap must be a no-op", world, ChunkProbeCache.wrap(world));
        ChunkProbeCache.begin();
        ChunkProbeCache.end();
        assertSame("after the window closed: still the original reference",
                world, ChunkProbeCache.wrap(world));
        assertSame("null stays null (GUI / particle path)", null, ChunkProbeCache.wrap(null));
    }

    @Test
    public void windowDedupesMutualNeighborProbes() {
        CountingWorld world = new CountingWorld();
        // Two adjacent glass panes: each probes the other's cell, so those two cells are
        // read twice by the raw chain; the cache must collapse that to one delegate read.
        world.set(10, 64, 10, 0);
        world.set(11, 64, 10, 5);

        ChunkProbeCache.begin();
        IBlockAccess w = ChunkProbeCache.wrap(world);
        probeNeighbors(w, 10, 64, 10); // reads (10,64,10) and (11,64,10) among others
        probeNeighbors(w, 11, 64, 10); // re-reads both cells from the other side
        ChunkProbeCache.end();

        // 8 distinct cells for block reads, each touched at least twice → deduped to 8.
        assertEquals("getBlock arrivals must equal the distinct-cell count",
                8, world.blockReads);
        assertEquals("getBlockMetadata arrivals must equal the distinct-cell count",
                2, world.metaReads);
        assertEquals("no duplicate cell may reach the delegate twice",
                8 + 2, world.trace.size());
    }

    @Test
    public void cachedReadsReturnIdenticalValues() {
        CountingWorld world = new CountingWorld();
        world.set(0, 70, 0, 3);

        ChunkProbeCache.begin();
        IBlockAccess w = ChunkProbeCache.wrap(world);
        Block first = w.getBlock(0, 70, 0);
        int firstMeta = w.getBlockMetadata(0, 70, 0);
        assertSame("second read of the same cell returns the cached reference",
                first, w.getBlock(0, 70, 0));
        assertEquals(firstMeta, w.getBlockMetadata(0, 70, 0));
        assertEquals("delegate saw one read per call site at most", 1, world.blockReads);
        ChunkProbeCache.end();
    }

    @Test
    public void noResidueAcrossWindows() {
        CountingWorld world = new CountingWorld();
        world.set(5, 64, 5, 1);

        ChunkProbeCache.begin();
        IBlockAccess w1 = ChunkProbeCache.wrap(world);
        assertSame(BLOCK_MARKERS[1], w1.getBlock(5, 64, 5));
        ChunkProbeCache.end();

        world.set(5, 64, 5, 2); // world changed between compiles (neighbor update)

        ChunkProbeCache.begin();
        IBlockAccess w2 = ChunkProbeCache.wrap(world);
        assertSame("a new window must observe the mutated cell, not the dropped cache",
                BLOCK_MARKERS[2], w2.getBlock(5, 64, 5));
        ChunkProbeCache.end();
    }
}
