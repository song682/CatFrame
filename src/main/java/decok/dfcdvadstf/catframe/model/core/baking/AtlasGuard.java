package decok.dfcdvadstf.catframe.model.core.baking;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import javax.annotation.Nullable;

/**
 * Atlas gate for the block world path (aligned with the forbiddenSprites rejection in
 * 26.1.2 {@code SimpleModelWrapper.bake}).
 * <p>
 * If a model in the blockstate wrapper layer contains a sprite from an atlas other than blocks,
 * the entire model is rejected, a warning is logged, and it is replaced with {@code builtin/missing}
 * (missingno) — the world render path is always bound to the blocks atlas, so the UVs of a
 * cross-atlas quad would sample the wrong region.
 * The item path (ItemStateModel, baked directly) is not restricted and may use any atlas.
 */
public final class AtlasGuard {

    private AtlasGuard() {
    }

    /** Lazily baked cache of the builtin/missing replacement part. */
    @Nullable
    private static BlockStateModelPart missingPart;

    /**
     * Gate check: passes the part through unchanged when all of its quads come from the blocks atlas;
     * otherwise rejects it and returns the missing part.
     *
     * @param part      the part to check (may be null, returned as-is)
     * @param modelPath model path (used only for the warning log)
     * @return the original part passed through, or the replacement missing part
     */
    @Nullable
    public static BlockStateModelPart gate(@Nullable BlockStateModelPart part, String modelPath) {
        if (part == null || part.allQuadsInBlockAtlas()) return part;
        CatFrame.logger.warn(
                "[AtlasGuard] Rejecting block model '{}', since it contains sprites from outside blocks atlas",
                modelPath);
        return missingPart();
    }

    private static synchronized BlockStateModelPart missingPart() {
        if (missingPart == null) {
            missingPart = ModelBaker.bake(
                    ModelResolver.resolve("builtin/missing"), 0, 0, "builtin/missing");
            if (missingPart == null) missingPart = BlockStateModelPart.empty();
        }
        return missingPart;
    }
}
