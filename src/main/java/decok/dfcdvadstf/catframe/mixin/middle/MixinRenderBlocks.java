package decok.dfcdvadstf.catframe.mixin.middle;

import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.RenderDispatcher;
import decok.dfcdvadstf.catframe.model.render.RenderJsonBlockModel;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into RenderBlocks to intercept <b>vanilla block</b> rendering.
 * <p>
 * This Mixin only handles vanilla blocks (renderType 0) that have a VMM model override
 * (loaded from blockstates / model_mappings), routing them through
 * {@link RenderDispatcher#renderBlock} → UniformRenderPipeline.
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
     * {@link RenderDispatcher}.
     */
    @Inject(method = "renderBlockByRenderType", at = @At("HEAD"), cancellable = true)
    private void catframe$onRenderBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        // Mod blocks with ISBRH — Forge handles them via custom renderType, skip here
        if (RenderJsonBlockModel.isRegistered(block)) return;

        if (ModelRegistry.hasModel(block)) {
            boolean result = RenderDispatcher.renderBlock(
                    blockAccess, x, y, z, block, (RenderBlocks) (Object) this);
            cir.setReturnValue(result);
        }
    }

    /**
     * Inject at the head of renderBlockUsingTexture (vanilla destroy overlay path).
     * <p>
     * The vanilla 1.7.10 destroy pipeline passes the destroy-stage IIcon through
     * {@code RenderBlocks.setOverrideBlockTexture}, but that mechanism never reaches
     * ISBRH handlers — so CatFrame-owned blocks (VMM renderType-0 blocks and
     * RenderJsonBlockModel ISBRH blocks) would be redrawn with their normal texture
     * during breaking. Route them through {@link RenderDispatcher#renderBlockDestroy}
     * instead, which reuses the block's own model geometry as the decal projection.
     * Blocks without a CatFrame model fall through to the vanilla path untouched.
     * <p>
     * 拦截原版破坏贴图渲染入口：CatFrame 接管方块（VMM renderType-0 方块与
     * RenderJsonBlockModel ISBRH 方块）改走 renderBlockDestroy；未接管方块放行原版。
     */
    @Inject(method = "renderBlockUsingTexture", at = @At("HEAD"), cancellable = true)
    private void catframe$onRenderBlockUsingTexture(Block block, int x, int y, int z,
                                                    IIcon icon, CallbackInfo ci) {
        // Blocks without a CatFrame model keep the vanilla override-texture path
        if (!RenderJsonBlockModel.isRegistered(block) && !ModelRegistry.hasModel(block)) return;

        RenderDispatcher.renderBlockDestroy(blockAccess, x, y, z, block, icon);
        ci.cancel();
    }
}
