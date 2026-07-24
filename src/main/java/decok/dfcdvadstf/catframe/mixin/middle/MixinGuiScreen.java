package decok.dfcdvadstf.catframe.mixin.middle;

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
 * <p>
 * 本类是<strong>机制层</strong>：在原版 {@code while(Keyboard.next())} 循环内读取 LWJGL2 当前键盘事件，
 * 并路由给任何实现了 {@link CatFrameInputScreen} 的 {@code GuiScreen}。其不可替代的价值在于：
 * 向无法继承 CatFrame 基类的<em>外部</em>屏幕（原版 / 他模组）接入 CatFrame 输入能力。
 * 内建 {@link decok.dfcdvadstf.catframe.ui.screens.Screen} 基类只是本契约的一个消费者，
 * 但它在自己的 {@code handleKeyboardInput()} 中自派发，因此
 * {@link CatFrameInputScreen#handlesKeyboardDispatchInternally()} 返回 {@code true}；
 * 本 mixin 会跳过这类屏幕，以免按键被投递两次。
 * </p>
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
        CatFrameInputScreen screen = (CatFrameInputScreen) (Object) this;
        // Screens that dispatch on their own (e.g. ui.screens.Screen) opt out here to avoid
        // double delivery — this mixin only serves foreign hosts that cannot self-dispatch.
        if (screen.handlesKeyboardDispatchInternally()) {
            return;
        }
        ScreenKeyboardInput.handleCurrentEvent(screen.getEventRoot());
    }
}
