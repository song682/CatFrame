package decok.dfcdvadstf.catframe.mixin.middle;

import decok.dfcdvadstf.catframe.model.ItemModelGenerator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 在 TextureAtlasSprite.clearFramesTextureData() 被调用之前保存帧数据。
 * <p>
 * Minecraft 1.7.10 的 TextureMap.loadTextureAtlas 在将非动画 sprite
 * 上传 GPU 后会调用 clearFramesTextureData() 清空 CPU 侧帧数据。
 * 而 CatFrame 的模型烘焙发生在 atlas 加载之后，此时 getFrameTextureData(0)
 * 已返回空列表，导致 ItemModelGenerator.bakeSideFaces 无法读取像素数据
 * 来生成侧面 quad。
 * <p>
 * 本 Mixin 拦截 clearFramesTextureData()，在清空前将 level-0 帧数据
 * 保存到 {@link ItemModelGenerator#preservedFrames} 缓存中，
 * 供侧面生成器在烘焙阶段使用。
 */
@Mixin(TextureAtlasSprite.class)
public class MixinTextureMap {

    @Shadow
    protected List<?> framesTextureData;

    @Inject(method = "clearFramesTextureData", at = @At("HEAD"))
    private void catframe$preserveFrames(CallbackInfo ci) {
        if (this.framesTextureData != null && !this.framesTextureData.isEmpty()) {
            int[][] frameData = (int[][]) this.framesTextureData.get(0);
            if (frameData != null && frameData.length > 0 && frameData[0] != null) {
                TextureAtlasSprite self = (TextureAtlasSprite) (Object) this;
                ItemModelGenerator.preservedFrames.put(self.getIconName(), frameData);
            }
        }
    }
}
