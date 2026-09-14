package decok.dfcdvadstf.catframe.mixin.early;

import decok.dfcdvadstf.catframe.resources.builtin.BuiltinPackDescriptor;
import decok.dfcdvadstf.catframe.resources.builtin.BuiltinPackEntry;
import decok.dfcdvadstf.catframe.resources.builtin.BuiltinPackRegistry;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Localizes the title of built-in pack entries in the resource pack GUI.
 * <p>
 * The vanilla title is the entry's pack name, which for built-in packs must
 * stay stable (it is the persistence key written to {@code options.txt} and it
 * participates in entry equality), so it cannot carry a display name. This
 * hook substitutes the registered translation key at render time only; the
 * description needs no hook because it already travels as a
 * {@code ChatComponentTranslation}.
 * <p>
 * 本地化资源包 GUI 中内置包条目的标题。原版标题取自条目的包名，而内置包的包名
 * 必须保持稳定（它既是写入 {@code options.txt} 的持久化键，也参与条目相等性），
 * 因此不能承载展示名。本注入仅在渲染时改用注册表登记的翻译键；描述无需注入，
 * 因为它本就是以 {@code ChatComponentTranslation} 形式流转的。
 */
@Mixin(ResourcePackListEntryFound.class)
public abstract class MixinResourcePackListEntryFound {

    /** The vanilla getter for the wrapped repository entry. */
    @Shadow
    public abstract ResourcePackRepository.Entry func_148318_i();

    /** Replaces the title of built-in entries with their localized name. */
    @Inject(method = "func_148312_b", at = @At("HEAD"), cancellable = true)
    private void catframe$localizeBuiltinPackName(CallbackInfoReturnable<String> cir) {
        ResourcePackRepository.Entry entry = this.func_148318_i();
        if (!(entry instanceof BuiltinPackEntry)) {
            return;
        }
        BuiltinPackDescriptor descriptor = BuiltinPackRegistry
                .getDescriptor(((BuiltinPackEntry) entry).catframe$getBuiltinId());
        if (descriptor != null) {
            cir.setReturnValue(I18n.format(descriptor.getNameKey()));
        }
    }
}
