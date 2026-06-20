package decok.dfcdvadstf.catframe.mixin.middle;

import decok.dfcdvadstf.catframe.model.IBlockJsonModel;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into RenderBlocks to intercept vanilla block rendering.
 * <p>
 * Blocks implementing {@link IBlockJsonModel} are <b>skipped</b> here ———
 * they use their own ISBRH ({@link decok.dfcdvadstf.catframe.model.render.RenderJsonBlockModel})
 * which delegates to {@link VanillaModelManager.PublicRenderAPI} internally.
 * <p>
 * This Mixin only handles <b>vanilla blocks</b> that have a VMM model override
 * (loaded from blockstates / model_mappings), routing them through the same
 * {@link VanillaModelManager.PublicRenderAPI#renderBlock} → UniformRenderPipeline path.
 */
@Mixin(RenderBlocks.class)
public class MixinRenderBlocks {

    @Shadow
    public IBlockAccess blockAccess;

    /**
     * Inject at the head of renderBlockByRenderType.
     * <ul>
     *   <li>IBlockJsonModel blocks → skip (they use their own ISBRH renderID)</li>
     *   <li>Vanilla blocks with VMM model → intercept and route through PublicRenderAPI</li>
     * </ul>
     */
    @Inject(method = "renderBlockByRenderType", at = @At("HEAD"), cancellable = true)
    private void catframe$onRenderBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        // IBlockJsonModel blocks use their own ISBRH — don't intercept them here
        if (block instanceof IBlockJsonModel) return;

        if (VanillaModelManager.ModelRegistration.hasModel(block)) {
            boolean result = VanillaModelManager.PublicRenderAPI.renderBlock(
                    blockAccess, x, y, z, block, (RenderBlocks) (Object) this);
            cir.setReturnValue(result);
        }
    }
}
