package decok.dfcdvadstf.catframe.mixin.middle;

import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.RenderJsonBlockModel;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into RenderBlocks to intercept <b>vanilla block</b> rendering.
 * <p>
 * This Mixin only handles vanilla blocks (renderType 0) that have a VMM model override
 * (loaded from blockstates / model_mappings), routing them through
 * {@link VanillaModelManager.PublicRenderAPI#renderBlock} → UniformRenderPipeline.
 * <p>
 * Mod blocks that register via {@link RenderJsonBlockModel#register(Block)} have a custom
 * renderType and are handled by Forge through ISBRH <b>before</b> this Mixin fires —
 * they are explicitly skipped here to avoid double-interception.
 */
@Mixin(RenderBlocks.class)
public class MixinRenderBlocks {

    @Shadow
    public IBlockAccess blockAccess;

    /**
     * Inject at the head of renderBlockByRenderType.
     * <p>
     * Skips mod blocks registered via {@link RenderJsonBlockModel} — they have a custom
     * renderType that Forge dispatches to ISBRH before this Mixin fires.
     * For vanilla blocks with a VMM model override, intercepts and routes through
     * {@link VanillaModelManager.PublicRenderAPI}.
     */
    @Inject(method = "renderBlockByRenderType", at = @At("HEAD"), cancellable = true)
    private void catframe$onRenderBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        // Mod blocks with ISBRH — Forge handles them via custom renderType, skip here
        if (RenderJsonBlockModel.isRegistered(block)) return;

        if (VanillaModelManager.ModelRegistration.hasModel(block)) {
            boolean result = VanillaModelManager.PublicRenderAPI.renderBlock(
                    blockAccess, x, y, z, block, (RenderBlocks) (Object) this);
            cir.setReturnValue(result);
        }
    }
}
