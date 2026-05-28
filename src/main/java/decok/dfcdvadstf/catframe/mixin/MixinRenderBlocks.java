package decok.dfcdvadstf.catframe.mixin;

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
 * If a block has a registered JSON model override, render it using
 * VanillaModelManager instead of the default renderer.
 */
@Mixin(RenderBlocks.class)
public class MixinRenderBlocks {

  @Shadow
  public IBlockAccess blockAccess;

  /**
   * Inject at the head of renderBlockByRenderType to check if we should
   * override the block's rendering with our JSON model system.
   */
  @Inject(method = "renderBlockByRenderType", at = @At("HEAD"), cancellable = true)
  private void catframe$onRenderBlock(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
    if (VanillaModelManager.hasModel(block)) {
      boolean result = VanillaModelManager.renderBlock(blockAccess, x, y, z, block, (RenderBlocks) (Object) this);
      cir.setReturnValue(result);
    }
  }
}
