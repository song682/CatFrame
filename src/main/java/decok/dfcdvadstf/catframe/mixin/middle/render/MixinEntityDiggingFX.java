package decok.dfcdvadstf.catframe.mixin.middle.render;

import decok.dfcdvadstf.catframe.model.ParticleIconResolver;
import net.minecraft.block.Block;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into EntityDiggingFX to route block destroy / hit / block-dust particle
 * textures through the CatFrame model system.
 * <p>
 * The 9-arg constructor is the single spawn point that samples the block texture
 * ({@code block.getIcon(side, meta)}); the 8-arg constructor and
 * {@code EntityBlockDustFX} both delegate to it — so destroy (64 particles), hit
 * (1 particle) and block-dust particles are all covered by this one redirect.
 * For blocks managed by CatFrame (model registry / ISBRH registration) the icon
 * is resolved from the model's {@code textures.particle} slot with the preserved
 * fallback chain (explicit slot → first quad → vanilla icon); unmanaged blocks
 * fall through to the vanilla per-side icon untouched.
 * <p>
 * 拦截 EntityDiggingFX 的粒子纹理采样点：CatFrame 接管方块的破坏/hit/blockdust
 * 粒子改由模型 particle 槽（经 ParticleIconResolver 回退链）决定，未接管方块放行原版。
 */
@Mixin(EntityDiggingFX.class)
public class MixinEntityDiggingFX {

    /**
     * Redirect the Block.getIcon(side, meta) call inside the 9-arg constructor.
     */
    @Redirect(method = "<init>(Lnet/minecraft/world/World;DDDDDDLnet/minecraft/block/Block;II)V",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/block/Block;getIcon(II)Lnet/minecraft/util/IIcon;"))
    private IIcon catframe$particleIcon(Block block, int side, int meta) {
        IIcon icon = ParticleIconResolver.getParticleIcon(block, meta);
        return icon != null ? icon : block.getIcon(side, meta);
    }
}
