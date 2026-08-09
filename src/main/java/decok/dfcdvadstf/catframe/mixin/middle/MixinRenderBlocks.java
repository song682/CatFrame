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
     * Inject at the head of renderBlockUsingTexture to intercept the vanilla
     * <b>destroy texture</b> pass ({@code RenderGlobal.drawBlockDamageTexture}).
     * <p>
     * Vanilla routes the destroy pass through {@code overrideBlockTexture}, which only
     * reaches renderType-0 rendering — ISBRH handlers (mod blocks) and VMM-overridden
     * vanilla blocks would otherwise be re-drawn with their normal texture (ghost block
     * effect, no cracks). We intercept both groups and route them through
     * {@link RenderDispatcher#renderBlockDestroy} (BLOCK_DESTROY phase + destroy icon),
     * then cancel the vanilla path. Blocks outside the CatFrame pipeline keep vanilla
     * behavior untouched.
     * <p>
     * 注入到 renderBlockUsingTexture 头部，接管原版破坏贴图批次
     * （{@code RenderGlobal.drawBlockDamageTexture}）：模组 ISBRH 方块与 VMM 接管方块
     * 走 {@link RenderDispatcher#renderBlockDestroy}（BLOCK_DESTROY 阶段 + 破坏图标），
     * 其余方块保持原版行为。
     */
    @Inject(method = "renderBlockUsingTexture(Lnet/minecraft/block/Block;IIILnet/minecraft/util/IIcon;)V",
            at = @At("HEAD"), cancellable = true)
    private void catframe$onRenderBlockUsingTexture(Block block, int x, int y, int z, IIcon icon, CallbackInfo ci) {
        if (RenderJsonBlockModel.isRegistered(block) || ModelRegistry.hasModel(block)) {
            RenderDispatcher.renderBlockDestroy(blockAccess, x, y, z, block, icon);
            ci.cancel();
        }
    }
}
