package decok.dfcdvadstf.catframe.mixin.middle;

import net.minecraft.client.renderer.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import decok.dfcdvadstf.catframe.model.render.extension.ao.light.BlockModelLighter;

/**
 * 在 chunk 编译（{@link WorldRenderer#updateRenderer}）前后管理
 * {@link BlockModelLighter} 的 LRU 缓存生命周期。
 * <p>
 * 对齐 26.1.2 {@code SectionCompiler.compile()} 中
 * {@code BlockModelLighter.enableCaching()} / {@code clearCache()} 的调用位置。
 */
@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    @Inject(method = "updateRenderer", at = @At("HEAD"))
    private void catframe$onChunkCompileBegin(CallbackInfo ci) {
        BlockModelLighter.enableCaching();
    }

    @Inject(method = "updateRenderer", at = @At("TAIL"))
    private void catframe$onChunkCompileEnd(CallbackInfo ci) {
        BlockModelLighter.clearCache();
    }
}
