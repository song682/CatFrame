package decok.dfcdvadstf.catframe.mixin.middle;

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
 * also covers the very first rebuild during {@code Minecraft.startGame()}
 * (when the repository is constructed), which is what lets the vanilla
 * name-matching loop of the constructor restore the enabled state of built-in
 * packs from {@code options.txt} once the startup bootstrap has injected them.
 * <p>
 * 把 {@link BuiltinPackInjector} 挂进原版仓库的重建周期：在
 * {@code updateRepositoryEntriesAll} 的 HEAD 捕获当前列表中的内置条目，在其
 * RETURN 重新加回。原版方法会整体替换条目列表，若不处理，内置条目会在每次
 * 重建时消失。HEAD/RETURN 这对注入同样覆盖 {@code Minecraft.startGame()} 中
 * 的首次重建（仓库构造之时），这正是启动引导注入内置包之后，原版构造器自身
 * 的按名匹配循环能够从 {@code options.txt} 恢复其启用状态的原因。
 */
@Mixin(ResourcePackRepository.class)
public abstract class MixinBuiltinResourcePackRepository {

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
