package decok.dfcdvadstf.catframe.mixin.early;

import decok.dfcdvadstf.catframe.resources.builtin.BuiltinPackInjector;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hooks {@link BuiltinPackInjector} into the vanilla repository rebuild cycle:
 * the built-in entries of the current list are captured at the HEAD of
 * {@code updateRepositoryEntriesAll} and re-added at its RETURN.
 * <p>
 * The vanilla method replaces the entry list wholesale, so the built-in
 * entries would otherwise disappear on every rebuild. The HEAD/RETURN pair
 * also covers the very first rebuild inside the {@code Minecraft} constructor
 * (the only run of this method that happens before any mod code), which is what
 * lets the vanilla constructor's own name-matching loop restore the enabled
 * state of built-in packs from {@code options.txt}.
 * <p>
 * 把 {@link BuiltinPackInjector} 挂进原版仓库的重建周期：在
 * {@code updateRepositoryEntriesAll} 的 HEAD 捕获当前列表中的内置条目，在其
 * RETURN 重新加回。原版方法会整体替换条目列表，若不处理，内置条目会在每次
 * 重建时消失。HEAD/RETURN 这对注入同样覆盖 {@code Minecraft} 构造器中的首次
 * 重建（该方法唯一早于任何模组代码的运行时机），这正是原版构造器自身的按名
 * 匹配循环能够从 {@code options.txt} 恢复内置包启用状态的原因。
 */
@Mixin(ResourcePackRepository.class)
public abstract class MixinResourcePackRepository {

    /** Vanilla-private list of every known entry; the target method rewrites it wholesale. */
    @Shadow
    private List repositoryEntriesAll;

    /** Captures the built-in entries before the target method replaces the list. */
    @Inject(method = "updateRepositoryEntriesAll", at = @At("HEAD"))
    private void catframe$captureBuiltinEntries(CallbackInfo ci) {
        BuiltinPackInjector.captureBuiltinEntries(this.repositoryEntriesAll);
    }

    /** Re-adds the built-in entries after the target method replaced the list. */
    @Inject(method = "updateRepositoryEntriesAll", at = @At("RETURN"))
    private void catframe$injectBuiltinEntries(CallbackInfo ci) {
        BuiltinPackInjector.injectBuiltinEntries((ResourcePackRepository) (Object) this, this.repositoryEntriesAll);
    }
}
