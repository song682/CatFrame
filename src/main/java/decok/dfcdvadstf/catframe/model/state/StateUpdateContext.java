package decok.dfcdvadstf.catframe.model.state;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Read-only world / position context handed to a {@link StateUpdater} and {@link RenderPredicate},
 * aligned with the {@code LevelReader} + {@code BlockPos} pair modern vanilla passes to
 * {@code updateShape}.
 *
 * <p>The context exposes the block's own coordinates and metadata plus relative neighbour
 * accessors. {@link #world} may be {@code null} in coordinate-less contexts (item / GUI preview,
 * break-particle resolution); the accessors then return {@code null} / {@code 0} so updaters can
 * degrade gracefully to the base state.
 *
 * <p>{@link #neighborState(int, int, int)} resolves a neighbour's <b>typed</b> base state by
 * looking up its {@link BlockStateDefinition} and decoding its metadata. Results (including
 * "no definition" misses) are memoized for the lifetime of this context — one update pass — so a
 * stair corner check that reads the same neighbour twice does not re-decode it. The definition
 * lookup is injected by the caller (the client render path supplies the model registry), keeping
 * this class common-side with no client-only dependency.
 *
 * <p>{@link #getTileEntity()} exposes the tile entity at the block's own position (1.7.10 keeps
 * a large part of a block's real state in its tile entity rather than in metadata — a flower
 * pot's planted item, for instance). The tile entity reference is returned as-is: it is live and
 * mutable, so an updater must read it inside {@code update} and must never retain it.
 *
 * <p>Common-side: depends only on {@link IBlockAccess}, {@link Block}, {@link TileEntity} and the
 * typed state classes.
 */
public final class StateUpdateContext {

    /** Read-only world access; {@code null} for item / GUI / particle (coordinate-less) contexts. */
    @Nullable
    public final IBlockAccess world;
    public final int x;
    public final int y;
    public final int z;
    /** The block's own metadata (0-15). */
    public final int meta;

    /** Block → definition lookup for {@link #neighborState}; nullable (then neighbour states are null). */
    @Nullable
    private final Function<Block, BlockStateDefinition<?>> defLookup;

    /** Memoized neighbour states keyed by packed relative offset; caches null misses too. */
    private final Map<Long, BlockState> neighborCache = new HashMap<>();

    /**
     * @param world     read-only world access (may be null)
     * @param x         block X coordinate
     * @param y         block Y coordinate
     * @param z         block Z coordinate
     * @param meta      the block's own metadata
     * @param defLookup neighbour block → definition lookup (may be null; neighbour states then null)
     */
    public StateUpdateContext(@Nullable IBlockAccess world, int x, int y, int z, int meta,
                              @Nullable Function<Block, BlockStateDefinition<?>> defLookup) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.meta = meta;
        this.defLookup = defLookup;
    }

    /**
     * Returns the neighbour's typed base state at the relative offset, or {@code null} when the
     * world is absent, the neighbour has no registered definition, or no lookup was supplied.
     * Memoized per context (one update pass).
     *
     * @param dx relative X offset
     * @param dy relative Y offset
     * @param dz relative Z offset
     * @return the neighbour's base state, or {@code null}
     */
    @Nullable
    public BlockState neighborState(int dx, int dy, int dz) {
        if (world == null || defLookup == null) return null;
        long key = packOffset(dx, dy, dz);
        if (neighborCache.containsKey(key)) return neighborCache.get(key);
        BlockState result = null;
        Block neighbor = world.getBlock(x + dx, y + dy, z + dz);
        if (neighbor != null) {
            BlockStateDefinition<?> def = defLookup.apply(neighbor);
            if (def != null) {
                result = def.getStateFromMeta(world.getBlockMetadata(x + dx, y + dy, z + dz));
            }
        }
        neighborCache.put(key, result);
        return result;
    }

    /**
     * Returns the neighbour block at the relative offset, or {@code null} when the world is absent.
     */
    @Nullable
    public Block getBlock(int dx, int dy, int dz) {
        return world == null ? null : world.getBlock(x + dx, y + dy, z + dz);
    }

    /**
     * Returns the neighbour metadata at the relative offset, or {@code 0} when the world is absent.
     */
    public int getMetadata(int dx, int dy, int dz) {
        return world == null ? 0 : world.getBlockMetadata(x + dx, y + dy, z + dz);
    }

    /**
     * Returns the tile entity at this block's own position, or {@code null} when the world is
     * absent or the block has no tile entity.
     * <p>
     * The reference is live: it reflects the current state at render time and must not be cached
     * across the update pass.
     *
     * @return the tile entity at {@code (x, y, z)}, or {@code null}
     */
    @Nullable
    public TileEntity getTileEntity() {
        return getTileEntity(0, 0, 0);
    }

    /**
     * Returns the tile entity at the relative offset, or {@code null} when the world is absent or
     * that position has none.
     *
     * @param dx relative X offset
     * @param dy relative Y offset
     * @param dz relative Z offset
     * @return the tile entity at the offset position, or {@code null}
     */
    @Nullable
    public TileEntity getTileEntity(int dx, int dy, int dz) {
        return world == null ? null : world.getTileEntity(x + dx, y + dy, z + dz);
    }

    /** Packs a relative offset into a collision-free long key (21 bits per axis, signed). */
    private static long packOffset(int dx, int dy, int dz) {
        return ((dx & 0x1FFFFFL) << 42) | ((dy & 0x1FFFFFL) << 21) | (dz & 0x1FFFFFL);
    }
}
