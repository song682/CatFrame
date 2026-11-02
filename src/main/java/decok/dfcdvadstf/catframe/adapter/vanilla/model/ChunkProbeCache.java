package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Compile-window neighbor probe cache (B1 scratch, not a store).
 * <p>
 * During one {@code WorldRenderer.updateRenderer} pass, dynamic resolvers probe the same
 * neighbor cells repeatedly (adjacent panes probe each other's cell; a stair row re-reads
 * the blocks above its corners). Client chunk compilation is atomic on the render thread —
 * the world cannot change inside the window — so a per-thread {@code (Block, meta)} lookup
 * cache is exactly correct there, and discarding it at window end leaves no stale state and
 * no second source of truth (derived facts keep living where vanilla puts them: recomputed
 * from the world each compile).
 * <p>
 * Thread model mirrors {@code BlockModelLighter}'s lifecycle: the mixin enables the window
 * on the compiling thread ({@link #begin()} / {@link #end()}), and {@link #wrap} hands out a
 * decorator only while the window is active. Outside a window — GUI, held-item, particle
 * paths — {@code wrap} returns the world reference itself: zero allocation, zero behavior
 * change.
 * <p>
 * Only {@link IBlockAccess#getBlock} and {@link IBlockAccess#getBlockMetadata} are cached;
 * they are what the resolver probe chains
 * ({@code canPaneConnectTo} / {@code canConnectRedstone} / {@code isPowerProviderOrWire})
 * bottom out in, and every internal read goes through the passed-in IBlockAccess, so the
 * decorator captures vanilla's own lookups without touching its implementation.
 */
@SideOnly(Side.CLIENT)
public final class ChunkProbeCache {

    /**
     * Table capacity: covers one section's probe footprint (16x16 column x 16 + boundary
     * shell ≈ 6k distinct cells) with headroom. Power of two for the mask probe.
     */
    private static final int CAPACITY = 8192;
    /** Linear probing needs a spare slot for the empty-key sentinel; clear past 3/4 fill. */
    private static final int REHASH_FILL = CAPACITY * 3 / 4;
    /** Unused-slot marker; never produced by the coordinate pack (y is clamped non-negative). */
    private static final long EMPTY = -1L;
    /** Slot marker meaning "block cached, meta not requested yet" (valid metas are 0..15). */
    private static final byte META_UNLOADED = (byte) -1;

    /** Window flag + per-thread table; the arrays are allocated lazily on the first begin. */
    private static final ThreadLocal<ProbeState> STATE = new ThreadLocal<ProbeState>() {
        @Override
        protected ProbeState initialValue() {
            return new ProbeState();
        }
    };

    private static final class ProbeState {
        boolean active;
        long[] keys;
        Block[] blocks;
        byte[] metas;
        int size;
        /** The single decorator instance reused for the whole window on this thread. */
        CachedWorld wrapper;
    }

    private ChunkProbeCache() {
    }

    // ==================== Lifecycle (mixin call sites) ====================

    /** Opens the probe window for the current thread (chunk compile start). */
    public static void begin() {
        ProbeState s = STATE.get();
        if (s.keys == null) {
            s.keys = new long[CAPACITY];
            s.blocks = new Block[CAPACITY];
            s.metas = new byte[CAPACITY];
            java.util.Arrays.fill(s.keys, EMPTY);
        }
        clearTable(s);
        s.active = true;
    }

    /** Closes the window and drops every cached read (chunk compile end). */
    public static void end() {
        ProbeState s = STATE.get();
        s.active = false;
        if (s.keys != null) clearTable(s);
    }

    private static void clearTable(ProbeState s) {
        java.util.Arrays.fill(s.keys, EMPTY);
        java.util.Arrays.fill(s.blocks, null);
        java.util.Arrays.fill(s.metas, META_UNLOADED);
        s.size = 0;
        s.wrapper = null;
    }

    // ==================== Integration point ====================

    /**
     * Returns a cached view of {@code world} inside a probe window, or {@code world} itself
     * when no window is active on this thread (or there is no world at all).
     */
    public static IBlockAccess wrap(IBlockAccess world) {
        ProbeState s = STATE.get();
        if (!s.active || world == null) return world;
        if (s.wrapper == null || s.wrapper.delegate != world) {
            s.wrapper = new CachedWorld(world, s);
        }
        return s.wrapper;
    }

    /** Test hook: whether a probe window is open on the current thread. */
    public static boolean isActive() {
        return STATE.get().active;
    }

    // ==================== Decorator ====================

    /**
     * Hand-rolled open-addressing table (primitive arrays, no Long boxing, no fastutil —
     * absent from the 1.7.10 baseline). Key is the coordinate pack; y is masked into 21
     * bits, which is lossless for the block-space range the resolvers probe and keeps
     * hash collisions cheap to resolve by full-key comparison.
     */
    private static final class CachedWorld implements IBlockAccess {
        private static final int Y_MASK = (1 << 21) - 1;

        final IBlockAccess delegate;
        private final ProbeState state;

        CachedWorld(IBlockAccess delegate, ProbeState state) {
            this.delegate = delegate;
            this.state = state;
        }

        private static long pack(int x, int y, int z) {
            return ((long) x << 32) + ((long) (y & Y_MASK) << 20) + (z & 0xFFFFFL);
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            long key = pack(x, y, z);
            long[] keys = state.keys;
            int mask = keys.length - 1;
            int idx = mix(key) & mask;
            while (true) {
                long k = keys[idx];
                if (k == EMPTY) {
                    Block b = delegate.getBlock(x, y, z);
                    if (state.size >= REHASH_FILL) {
                        resetTable(); // pathological footprint (shouldn't happen per section)
                        return b;
                    }
                    keys[idx] = key;
                    state.blocks[idx] = b;
                    state.metas[idx] = META_UNLOADED; // meta fetched lazily by the method below
                    state.size++;
                    return b;
                }
                if (k == key) return state.blocks[idx];
                idx = (idx + 1) & mask;
            }
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            long key = pack(x, y, z);
            long[] keys = state.keys;
            int mask = keys.length - 1;
            int idx = mix(key) & mask;
            while (true) {
                long k = keys[idx];
                if (k == EMPTY) {
                    // Metas live in the block-line slot: the read below inserts it.
                    getBlock(x, y, z);
                    if (keys[idx] == key) return loadMeta(idx, x, y, z);
                    return delegate.getBlockMetadata(x, y, z);
                }
                if (k == key) return loadMeta(idx, x, y, z);
                idx = (idx + 1) & mask;
            }
        }

        /** First meta request for the slot pays one delegate read; later requests are cached. */
        private int loadMeta(int idx, int x, int y, int z) {
            byte cached = state.metas[idx];
            if (cached != META_UNLOADED) return cached & 0xF;
            byte m = (byte) delegate.getBlockMetadata(x, y, z);
            state.metas[idx] = m;
            return m & 0xF;
        }

        /** Avalanche the packed key so nearby coordinates spread across the table. */
        private static int mix(long key) {
            long h = key * 0x9E3779B97F4A7C15L;
            return (int) ((h ^ (h >>> 32)) * 0x85EBCA6B) * 0xC2B2AE35;
        }

        private void resetTable() {
            java.util.Arrays.fill(state.keys, EMPTY);
            java.util.Arrays.fill(state.blocks, null);
            java.util.Arrays.fill(state.metas, META_UNLOADED);
            state.size = 0;
        }

        // ---- everything else delegates untouched ----

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return delegate.getTileEntity(x, y, z);
        }

        @Override
        public int getLightBrightnessForSkyBlocks(int x, int y, int z, int p_72802_4_) {
            return delegate.getLightBrightnessForSkyBlocks(x, y, z, p_72802_4_);
        }

        @Override
        public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
            return delegate.isBlockProvidingPowerTo(x, y, z, side);
        }

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            return delegate.isAirBlock(x, y, z);
        }

        @Override
        public BiomeGenBase getBiomeGenForCoords(int x, int z) {
            return delegate.getBiomeGenForCoords(x, z);
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public boolean extendedLevelsInChunkCache() {
            return delegate.extendedLevelsInChunkCache();
        }

        @Override
        public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) {
            return delegate.isSideSolid(x, y, z, side, _default);
        }
    }
}
