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
 * <p>
 * 把原版 {@code refreshResources()} 的 HEAD 转发给 {@link BuiltinPackBootstrap}：
 * preInit 之后那次启动刷新（启动两次刷新中的第二次）正是普通模组注册的内置包
 * 被注入仓库、且其启用状态被恢复的时点（精确时序见该类）。
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftRefreshResources {

    /** Pre-refresh hook; the bootstrap itself is a no-op outside its startup window. */
    @Inject(method = "refreshResources", at = @At("HEAD"))
    private void catframe$bootstrapBuiltinPacks(CallbackInfo ci) {
        BuiltinPackBootstrap.onRefreshResources((Minecraft) (Object) this);
    }
}
