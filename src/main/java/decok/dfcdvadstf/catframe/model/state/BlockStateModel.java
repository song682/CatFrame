package decok.dfcdvadstf.catframe.model.state;

import decok.dfcdvadstf.catframe.model.core.baking.ModelBaker;
import decok.dfcdvadstf.catframe.model.state.block.MultipartBlockModel;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel;
import decok.dfcdvadstf.catframe.model.state.block.StateProviderBlockModel;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Blockstate model interface. Similar to 1.21.5's BlockStateModel,
 * it resolves a blockstate (world + x/y/z + metadata/CatBlockState) into a set of render parts.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link ResidentStateModel} — unified implementation backed by the resident state table (combines variants / multipart / dynamic properties)</li>
 *   <li>{@link StateProviderBlockModel} — delegates to IBlockStateProvider (string property mapping)</li>
 *   <li>{@link MultipartBlockModel} — multipart condition combination</li>
 * </ul>
 */
public interface BlockStateModel {

    /**
     * Collects render parts via metadata (legacy path, kept for backward compatibility).
     *
     * @param world    world (may be null in item rendering contexts)
     * @param x        block X coordinate
     * @param y        block Y coordinate
     * @param z        block Z coordinate
     * @param metadata block metadata
     * @return the render part collection
     */
    BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata);

    /**
     * Collects render parts while also returning the blockstate properties built during matching
     * (such as a pane's north/east/south/west).
     * <p>
     * The default implementation delegates to {@link #collectParts(IBlockAccess, int, int, int, int)}
     * with null properties; implementations that have already computed properties override this
     * method to reuse the same Map and avoid recomputation.
     * The returned property Map follows a read-only convention (an unmodifiable view) and is only
     * carried for this single submission.
     *
     * @param world    world (may be null in item rendering contexts)
     * @param x        block X coordinate
     * @param y        block Y coordinate
     * @param z        block Z coordinate
     * @param metadata block metadata
     * @return parts + properties (properties may be null)
     */
    default CollectedPart collectPartsWithProps(IBlockAccess world, int x, int y, int z, int metadata) {
        return new CollectedPart(collectParts(world, x, y, z, metadata), null);
    }

    /**
     * Collection result: render parts + blockstate properties built during matching (may be null).
     * <p>
     * The property Map only lives for the duration of a single render submission; callers must not
     * modify it or retain it across frames.
     */
    final class CollectedPart {
        public final BlockStateModelPart part;
        @Nullable
        public final Map<String, String> blockstateProps;

        public CollectedPart(BlockStateModelPart part,
                             @Nullable Map<String, String> blockstateProps) {
            this.part = part;
            this.blockstateProps = blockstateProps;
        }
    }

    /**
     * v0.3.0: collects render parts via {@link BlockState} (new path).
     * The default implementation delegates to the metadata version; subclasses may override for
     * type-safe property matching.
     *
     * @param world world (may be null in item rendering contexts)
     * @param x     block X coordinate
     * @param y     block Y coordinate
     * @param z     block Z coordinate
     * @param state the CatBlockState instance
     * @return the render part collection
     */
    default BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, BlockState state) {
        return collectParts(world, x, y, z, 0);
    }

    /**
     * Whether this model fully covers the block's rendering.
     * true = replaces the entire block render; false = overlay layer (corresponds to autoOverlay).
     */
    default boolean isFullModel() {
        return true;
    }

    // ==================== Added (aligned with 26.1.2) ====================

    /**
     * Collects parts from a random source — supports the random selection in WeightedVariants.
     * The default implementation delegates to the legacy collectParts.
     *
     * @param random the random source
     * @param output the output list to collect into
     */
    default void collectParts(Random random, List<BlockStateModelPart> output) {
        BlockStateModelPart part = collectParts(null, 0, 0, 0, 0);
        if (part != null && !part.isEmpty()) {
            output.add(part);
        }
    }

    /**
     * Particle texture material. The default implementation returns null.
     *
     * @return the IIcon used for particles, may be null
     */
    @Nullable
    default IIcon particleIcon() {
        return null;
    }

    /**
     * Particle texture material (with metadata context) — resolved when generating
     * break/hit/blockdust particles.
     * <p>
     * Mirrors 26.1.2's {@code BlockStateModel#particleMaterial()}: implementations resolve the
     * main part of the blockstate by metadata (with no world/coordinate context, since the block
     * may already have been removed at particle-generation time) and return the explicit
     * particle-slot IIcon; when missing or unresolvable it is null (ParticleIconResolver maps it
     * to missingno following high-version semantics). A default of null means metadata-level
     * resolution is unsupported (the caller falls back to vanilla block.getIcon).
     * Metadata-aware particle icon resolution mirroring 26.1.2 particleMaterial();
     * returns the explicit particle-slot icon or null (mapped to missingno upstream).
     *
     * @param metadata block metadata
     * @return the IIcon used for particles, may be null
     */
    @Nullable
    default IIcon particleIcon(int metadata) {
        return null;
    }

    // ==================== Unbaked sub-interface ====================

    /**
     * Unbaked model interface — a BlockStateModel definition that has not been baked yet.
     * Mirrors 26.1.2's {@code BlockStateModel.Unbaked}.
     */
    interface Unbaked {
        /**
         * Bakes into a complete BlockStateModel.
         *
         * @param baker the ModelBaker instance
         * @return the baked BlockStateModel
         */
        BlockStateModel bake(ModelBaker baker);

        /**
         * Resolves dependencies: notifies the consumer of dependency model paths.
         *
         * @param dependencyConsumer the consumer that receives dependency model paths
         */
        void resolveDependencies(Consumer<String> dependencyConsumer);
    }
}
