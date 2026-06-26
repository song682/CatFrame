package decok.dfcdvadstf.catframe.mixin.middle;

import decok.dfcdvadstf.catframe.resource.langguage.LanguageRegister;
import decok.dfcdvadstf.catframe.resource.langguage.LocalizationManager;
import net.minecraft.client.resources.LanguageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@link LanguageManager#setCurrentLanguage} so that when the player
 * switches language in the GUI, our JSON-based {@link LocalizationManager}
 * reloads its translations for the new language.
 * <p>
 * 钩入 {@link LanguageManager#setCurrentLanguage}，
 * 让玩家在 GUI 切换语言时，JSON {@link LocalizationManager} 也同步重载翻译。
 */
@Mixin(LanguageManager.class)
public class MixinLanguageManager {

    /**
     * After vanilla sets the new language, trigger a full reload of
     * our JSON translations — but only if mods have registered language
     * domains already (skip the very first startup call).
     */
    @Inject(method = "setCurrentLanguage", at = @At("RETURN"), remap = false)
    private void catframe$onLanguageChanged(CallbackInfo ci) {
        // Only reload if domains have been registered (skip initial startup)
        if (!LanguageRegister.getDomains().isEmpty()) {
            LocalizationManager.reload();
        }
    }
}
