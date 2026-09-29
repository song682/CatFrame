package decok.dfcdvadstf.catframe.mixin.middle.event;

import decok.dfcdvadstf.catframe.ui.components.events.CatFrameInputScreen;
import decok.dfcdvadstf.catframe.ui.components.events.ScreenKeyboardInput;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into GuiScreen to dispatch CatFrame focus/keyboard events.
 * <p>
 * This is the <strong>mechanism layer</strong>: it reads LWJGL2's current keyboard event during
 * the vanilla {@code while(Keyboard.next())} loop and routes it to any {@code GuiScreen} that
 * implements {@link CatFrameInputScreen}. Its unique value is retrofitting CatFrame input onto
 * <em>foreign</em> screens (vanilla / other mods) that cannot extend the CatFrame base.
 * </p>
 * <p>
 * The built-in {@link decok.dfcdvadstf.catframe.ui.screens.Screen} base is one consumer of this
 * contract, but it <strong>self-dispatches</strong> in its own {@code handleKeyboardInput()} and
 * therefore returns {@code true} from {@link CatFrameInputScreen#handlesKeyboardDispatchInternally()};
 * this mixin skips such screens so keys are never delivered twice.
 * </p>
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen extends Gui {

    /**
     * Takes over the vanilla keyboard event - reads LWJGL2's current event and splits it into
     * {@code keyPressed}/{@code keyReleased}/{@code charTyped}, while also handling Tab focus navigation.
     * <p>Only applies to screens implementing {@link CatFrameInputScreen}. Injected at the HEAD of
     * {@code handleKeyboardInput}: at that point we are still inside the vanilla
     * {@code while(Keyboard.next())} loop, so LWJGL's {@code current_event} is valid and unconsumed.</p>
     */
    @Inject(method = "handleKeyboardInput", at = @At("HEAD"))
    private void catframe$dispatchKeyboard(CallbackInfo ci) {
        if (!(((Object) this) instanceof CatFrameInputScreen)) {
            return;
        }
        CatFrameInputScreen screen = (CatFrameInputScreen) (Object) this;
        // Screens that dispatch on their own (e.g. ui.screens.Screen) opt out here to avoid
        // double delivery — this mixin only serves foreign hosts that cannot self-dispatch.
        if (screen.handlesKeyboardDispatchInternally()) {
            return;
        }
        ScreenKeyboardInput.handleCurrentEvent(screen.getEventRoot());
    }
}
