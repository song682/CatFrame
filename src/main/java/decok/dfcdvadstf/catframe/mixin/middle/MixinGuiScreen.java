package decok.dfcdvadstf.catframe.mixin.middle;

import decok.dfcdvadstf.catframe.ui.components.Component;
import decok.dfcdvadstf.catframe.ui.components.events.CatFrameInputScreen;
import decok.dfcdvadstf.catframe.ui.components.events.ScreenKeyboardInput;
import decok.dfcdvadstf.catframe.ui.render.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin into GuiScreen to redirect vanilla tooltip rendering through CatFrame's
 * deferred tooltip pipeline ({@link GuiGraphicsExtractor}).
 * <p>
 * Intercepts:
 * <ul>
 *   <li>{@code drawHoveringText} — generic text tooltip → captured for deferred render</li>
 *   <li>{@code renderToolTip} — item tooltip → captured for deferred render</li>
 *   <li>{@code handleKeyboardInput} — CatFrame focus/keyboard dispatch</li>
 * </ul>
 * <p>
 * <b>帧生命周期（reset/flush）不在此处：</b>{@code GuiContainer} 重写 {@code drawScreen}
 * 且不调用 {@code super}，注入到 {@code GuiScreen.drawScreen} 的 reset/flush 在容器界面永不触发。
 * 因此改由 {@link decok.dfcdvadstf.catframe.compact.vanilla.ClientScreenGraphicsHandler}
 * 通过 Forge {@code DrawScreenEvent.Pre/Post} 驱动，对所有屏幕生效。
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

    /**
     * 接管原版 func_146283_a (drawHoveringText 的包装) — 重定向到延迟 tooltip 管线。
     * <p>使用 SRG 名因为 drawHoveringText 本身不在 mcp-srg 映射中。</p>
     */
    @SuppressWarnings("unchecked")
    @Inject(method = "func_146283_a", at = @At("HEAD"), cancellable = true)
    private void catframe$redirectDrawHoveringText(List lines, int x, int y, CallbackInfo ci) {
        if (lines == null || lines.isEmpty()) return;
        GuiGraphicsExtractor.getInstance().setTooltipForNextFrame(
                (List<String>) lines, x, y);
        ci.cancel();
    }

    /**
     * 接管原版 renderToolTip — 重定向到延迟 tooltip 管线（含 DataComponent style 支持）。
     */
    @Inject(method = "renderToolTip", at = @At("HEAD"), cancellable = true)
    private void catframe$redirectRenderToolTip(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (stack == null) return;
        GuiGraphicsExtractor.getInstance().setTooltipForNextFrame(
                Minecraft.getMinecraft().fontRenderer, stack, x, y);
        ci.cancel();
    }
}
