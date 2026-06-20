package decok.dfcdvadstf.catframe.mixin.middle;

import decok.dfcdvadstf.catframe.langguage.LanguageRegister;
import decok.dfcdvadstf.catframe.langguage.LocalizationManager;
import net.minecraft.util.StringTranslate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks {@link StringTranslate#translateKey(String)} so that every vanilla
 * translation lookup — including {@code StatCollector.translateToLocal},
 * {@code Item.getItemStackDisplayName()}, etc. — first tries our JSON-based
 * {@link LocalizationManager}.
 * <p>
 * 钩入原版 {@link StringTranslate#translateKey(String)}，
 * 让所有原版翻译查询先走我们的 JSON {@link LocalizationManager}。
 */
@Mixin(StringTranslate.class)
public class MixinStringTranslate {

    /**
     * Intercept translateKey at HEAD.  If the key exists in any registered
     * domain's translations (or fallback), return it immediately.
     * <p>
     * Vanilla passes keys like {@code "item.bluey_plushy.name"};
     * we search every domain — e.g. {@code "catframe:item.bluey_plushy.name"}.
     */
    @Inject(method = "translateKey", at = @At("HEAD"), cancellable = true, remap = false)
    private void catframe$interceptTranslateKey(String key, CallbackInfoReturnable<String> cir) {
        if (key == null || key.isEmpty()) return;

        for (String domain : LanguageRegister.getDomains().keySet()) {
            if (LocalizationManager.Translation.hasKey(domain, key)) {
                String result = LocalizationManager.Translation.translate(domain, key);
                cir.setReturnValue(result);
                return;
            }
        }
    }
}
