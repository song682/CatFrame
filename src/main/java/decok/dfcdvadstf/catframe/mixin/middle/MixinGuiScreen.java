package decok.dfcdvadstf.catframe.mixin.middle;

import decok.dfcdvadstf.catframe.ui.components.Component;
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
 * Intercepts:
 * <ul>
 *   <li>{@code handleKeyboardInput} — CatFrame focus/keyboard dispatch</li>
 * </ul>
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen extends Gui {

    /**
     * 接管原版键盘事件——读取 LWJGL2 的当前事件并拆分为
     * {@code keyPressed}/{@code keyReleased}/{@code charTyped}，同时处理 Tab 焦点导航。
     * <p>仅对实现了 {@link CatFrameInputScreen} 的屏幕生效。注入于 {@code handleKeyboardInput}
     * 的 HEAD：此时仍处于原版 {@code while(Keyboard.next())} 循环内，
     * LWJGL {@code current_event} 有效且未被消耗。</p>
     */
    @Inject(method = "handleKeyboardInput", at = @At("HEAD"))
    private void catframe$dispatchKeyboard(CallbackInfo ci) {
        if (!(((Object) this) instanceof CatFrameInputScreen)) {
            return;
        }
        Component root = ((CatFrameInputScreen) (Object) this).getEventRoot();
        ScreenKeyboardInput.handleCurrentEvent(root);
    }
}
