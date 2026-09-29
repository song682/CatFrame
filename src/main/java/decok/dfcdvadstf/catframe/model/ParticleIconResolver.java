package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.model.render.RenderJsonBlockModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.block.StateProviderBlockModel;
import decok.dfcdvadstf.catframe.resources.atlas.CatAtlasManager;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model-driven particle icon resolver entry point for destroy / hit / block-dust particles.
 * <p>
 * Mirrors 26.1.2 {@code BlockStateModelSet#getParticleMaterial(state)}: resolves
 * particle icons for CatFrame-managed blocks per {@code (block, metadata)}, matching
 * modern semantics — explicit textures.particle slot takes precedence; missing/unresolvable
 * slots map directly to missingno (purple-black checkerboard, matching
 * {@code MaterialBaker#reportMissingReference} → {@code blockMissing}), no fallback guessing.
 * </p>
 * <p>
 * No world/position context (particles may spawn after block removal; 26.1.2 is also per-state).
 * Results cached by {@code (blockId, meta)}: a single break generates 64 particles,
 * avoiding full-chain re-resolution per particle. Only non-null results cached —
 * transient misses (models not yet baked) must not pollute cache
 * (matches BakedModelCache bake-failure-no-cache semantics); cache cleared on
 * resource reload alongside {@link BakedModelCache#clear}.
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class ParticleIconResolver {

    private ParticleIconResolver() {
    }

    /** (blockId << 16 | meta & 0xFFFF) → resolved particle IIcon. Only non-null results cached. */
    private static final Map<Long, IIcon> CACHE = new ConcurrentHashMap<>();

    /**
     * Resolve block's particle icon (model-driven).
     * <p>
     * Managed blocks: missing/unresolvable slots map to missingno per modern semantics.
     * Unmanaged blocks (or missingno unavailable) return null — caller falls back to
     * vanilla {@code block.getIcon(side, meta)}.
     *
     * @param block    block instance
     * @param metadata block metadata
     * @return IIcon for particle, or null
     */
    @Nullable
    public static IIcon getParticleIcon(Block block, int metadata) {
        if (block == null) return null;

        long key = ((long) Block.getIdFromBlock(block) << 16) | (metadata & 0xFFFF);
        IIcon cached = CACHE.get(key);
        if (cached != null) return cached;

        IIcon icon = resolve(block, metadata);
        // Cache only non-null: during load, models not yet baked must not memoize "no particle" (same semantics as BakedModelCache bake-failure-no-cache).
        // Positive results only — a transient miss (models not baked yet) must not be memoized.
        if (icon != null) {
            CACHE.put(key, icon);
        }
        return icon;
    }

/**
     * Extract explicit particle-slot icon from part (null = slot missing or part unresolvable).
     * <p>
     * Modern semantics have no substitute guess — null returned here gets mapped to missingno
     * by {@link #getParticleIcon} (26.1.2 semantics, no substitute guessing).
     * {@link BlockStateModelPart#particleIcon()} (first quad) remains as legacy fallback
     * but no longer participates in this semantic chain.
     */
    @Nullable
    public static IIcon fromPart(@Nullable BlockStateModelPart part) {
        return part == null ? null : part.particleSlotIcon();
    }

    /**
     * Clear on resource reload (texture stitch) to avoid stale IIcon from previous cycle.
     */
    public static void clear() {
        CACHE.clear();
    }

    // ==================== Internal resolution ====================

    @Nullable
    private static IIcon resolve(Block block, int metadata) {
        // Gate: same "CatFrame managed" check as MixinRenderBlocks.
        if (!ModelRegistry.hasModel(block) && !RenderJsonBlockModel.isRegistered(block)) return null;

        BlockStateModel model = ModelRegistry.registeredBlockModels.get(block);
        if (model != null) {
            return orMissingno(model.particleIcon(metadata), block, metadata);
        }

        // Matches RenderDispatcher path 3: no registered instance but stateBlockData has block —
        // Temporary StateProviderBlockModel parse (provider contract allows null world; result cached at most once).
        if (block instanceof IBlockStateProvider && ModelManagerDataLoader.stateBlockData.containsKey(block)) {
            BlockstateJson bs = ModelManagerDataLoader.stateBlockData.get(block);
            if (bs != null) {
                IIcon slot = new StateProviderBlockModel((IBlockStateProvider) block, bs, null).particleIcon(metadata);
                return orMissingno(slot, block, metadata);
            }
        }
        // Managed check passed but no model instance (e.g. mod block with only ISBRH registration) → don't intercept, fall through to vanilla getIcon.
        return null;
    }

    /**
     * Modern fallback mapping: managed block's explicit missing/unresolvable slot → missingno
     * (matching {@code MaterialBaker#reportMissingReference} → {@code blockMissing}),
     * no substitute guessing. Extreme case: missingno unavailable returns null (caller falls back to vanilla getIcon).
     */
    @Nullable
    private static IIcon orMissingno(@Nullable IIcon slot, Block block, int metadata) {
        if (slot != null) return slot;
        if (CatFrameConfig.shouldLogDebug()) {
            CatFrame.logger.info("[ParticleIconResolver] missing particle slot: block={} meta={} -> missingno",
                    Block.blockRegistry.getNameForObject(block), metadata);
        }
        return CatAtlasManager.getMissingIcon("particle");
    }
}
