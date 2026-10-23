package decok.dfcdvadstf.catframe.mixin.middle;

import decok.dfcdvadstf.catframe.resources.builtin.BuiltinPackBootstrap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards the head of every vanilla {@code refreshResources()} call to
 * {@link BuiltinPackBootstrap}: the startup refresh that follows preInit — the
 * second of the two startup refreshes — is where packs registered by ordinary
 * mods are injected into the repository and their enabled state is restored
 * (the exact timing lives in {@link BuiltinPackBootstrap}).
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftRefreshResources {

    /** Pre-refresh hook; the bootstrap itself is a no-op outside its startup window. */
    @Inject(method = "refreshResources", at = @At("HEAD"))
    private void catframe$bootstrapBuiltinPacks(CallbackInfo ci) {
        BuiltinPackBootstrap.onRefreshResources((Minecraft) (Object) this);
    }
}
