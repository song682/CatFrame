package decok.dfcdvadstf.catframe.mixin.middle.render;

import decok.dfcdvadstf.catframe.model.render.pipeline.WorldTranslucentPasses;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Base-class hook that opts force-translucent blocks into the vanilla 1.7.10 dual-pass
 * chunk tessellation (Forge {@code canRenderInPass} contract).
 * <p>
 * Both injections target the <b>base</b> {@code Block} implementation only: a block class
 * that overrides {@code getRenderBlockPass()} / {@code canRenderInPass()} itself (native
 * translucent blocks such as glass, or GTNH-style multi-pass mod blocks) is dispatched to
 * the subclass body first and never reaches this mixin, so its original pass semantics
 * stay untouched — no injection-order competition with other mods.
 * <p>
 * Hijacked blocks report {@code getRenderBlockPass() == 1} (so the WorldRenderer pass loop
 * also compiles an alpha-pass mesh, drawn by EntityRenderer with GL_BLEND) and additionally
 * accept the solid pass 0, where the submission side ({@code UniformRenderPipeline.renderBlockQuads})
 * emits only the non-forced quads; the alpha pass emits only the forced ones. The
 * {@code WorldTranslucentPasses} sequence pairing (getRenderBlockPass → canRenderInPass →
 * render, queried per position without interleaving) carries the identity of the currently
 * tessellating block through these three calls.
 */
@Mixin(Block.class)
public abstract class MixinBlock {

    /**
     * Base getRenderBlockPass: a marked block must be scheduled into the alpha pass, otherwise
     * the pass loop never compiles a second mesh for pure-solid chunk sections.
     */
    @Inject(method = "getRenderBlockPass", at = @At("HEAD"), cancellable = true)
    private void catframe$forceAlphaPass(CallbackInfoReturnable<Integer> cir) {
        Block self = (Block) (Object) this;
        if (WorldTranslucentPasses.isForced(self)) {
            WorldTranslucentPasses.noteBasePassHijack(self);
            cir.setReturnValue(1);
        }
    }

    /**
     * Base canRenderInPass: the default body already answers pass 1 (pass == hijacked 1);
     * only the solid pass 0 needs to be opted back in, and only for the block whose base
     * getRenderBlockPass was just hijacked in this same tessellation iteration.
     */
    @Inject(method = "canRenderInPass", at = @At("HEAD"), cancellable = true, remap = false)
    private void catframe$keepSolidPassToo(int pass, CallbackInfoReturnable<Boolean> cir) {
        Block self = (Block) (Object) this;
        if (WorldTranslucentPasses.shouldSplitByPass(self)) {
            WorldTranslucentPasses.noteTessellationPass(pass);
            if (pass == 0) {
                cir.setReturnValue(true);
            }
        }
    }
}
