package decok.dfcdvadstf.catframe.mixin.middle.render;

import decok.dfcdvadstf.catframe.adapter.vanilla.model.ChunkProbeCache;
import decok.dfcdvadstf.catframe.model.render.extension.ao.light.BlockModelLighter;
import decok.dfcdvadstf.catframe.model.render.pipeline.WorldTranslucentPasses;
import net.minecraft.client.renderer.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Manages the LRU cache lifecycle of {@link BlockModelLighter} around
 * chunk compilation ({@link WorldRenderer#updateRenderer}).
 * <p>
 * Aligned with the {@code BlockModelLighter.enableCaching()} / {@code clearCache()}
 * call sites in 26.1.2 {@code SectionCompiler.compile()}.
 * <p>
 * Also maintains the per-thread chunk-tessellation scope flag of
 * {@link WorldTranslucentPasses} so the force_translucent pass split only ever applies
 * to submissions made inside a chunk build.
 * <p>
 * Opens/closes the {@link ChunkProbeCache} compile window on the same head/tail: neighbor
 * reads by dynamic resolvers are memoized for the (immutable, from the render thread's
 * point of view) chunk build and dropped right after it — a scratch cache, never a store.
 * </p>
 */
@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    @Inject(method = "updateRenderer", at = @At("HEAD"))
    private void catframe$onChunkCompileBegin(CallbackInfo ci) {
        BlockModelLighter.enableCaching();
        // Per-thread tessellation scope for the force_translucent pass split (WorldTranslucentPasses).
        WorldTranslucentPasses.beginTessellating();
        ChunkProbeCache.begin();
    }

    @Inject(method = "updateRenderer", at = @At("TAIL"))
    private void catframe$onChunkCompileEnd(CallbackInfo ci) {
        BlockModelLighter.clearCache();
        WorldTranslucentPasses.endTessellating();
        ChunkProbeCache.end();
    }
}
