package decok.dfcdvadstf.catframe.mixin.middle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import decok.dfcdvadstf.catframe.compact.forge.event.CatFrameGuiEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into GuiScreen to fire CatFrame's public {@link CatFrameGuiEvent.KeyboardInputEvent}
 * Pre/Post events — the backfill of Forge 1.8+'s {@code GuiScreenEvent.KeyboardInputEvent}.
 * <p>
 * This is the <strong>public event bridge</strong>, deliberately separate from
 * {@link MixinGuiScreen} (CatFrame's internal dispatch mechanism): external subscribers
 * (e.g. IME mods) get a keyboard hook on any open screen without writing their own mixin.
 * It wraps the {@code this.handleKeyboardInput()} call site inside
 * {@code GuiScreen.handleInput()} — the same patch shape Forge 1.8+ applies — so screens
 * overriding {@code handleKeyboardInput} are covered too; only screens overriding
 * {@code handleInput} itself fall outside (same blind spot as the official Forge patch).
 * </p>
 * <p>
 * 本类是<strong>公共事件桥</strong>，负责发射 CatFrame 对外的
 * {@link CatFrameGuiEvent.KeyboardInputEvent} Pre/Post 事件（Forge 1.8+
 * {@code GuiScreenEvent.KeyboardInputEvent} 的 1.7.10 回填），与内部派发机制层
 * {@link MixinGuiScreen} 刻意分离：外部订阅者（如输入法模组）无需自写 Mixin
 * 即可挂接任意打开中屏幕的键盘事件。注入方式为包住 {@code GuiScreen.handleInput()}
 * 中 {@code this.handleKeyboardInput()} 的调用点（与 Forge 1.8+ 官方补丁同构），
 * 因此覆写了 {@code handleKeyboardInput} 的屏幕同样被覆盖；仅覆写 {@code handleInput}
 * 本身的屏幕不在覆盖面内（与官方补丁盲区一致）。
 * </p>
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreenEventBridge {

    /**
     * Wrap one keyboard event of the vanilla {@code while(Keyboard.next())} loop:
     * post {@code Pre} (cancelable — canceling skips {@code handleKeyboardInput()} for this
     * event), then run the original call, then post {@code Post} only if this screen is
     * still {@code Minecraft.currentScreen} (guards against close/switch during handling,
     * e.g. Esc). LWJGL2's current event stays valid throughout — nothing here calls
     * {@code Keyboard.next()}.
     * <p>包裹原版 {@code while(Keyboard.next())} 循环中的单个键盘事件：先发可取消的
     * {@code Pre}（取消则跳过本次 {@code handleKeyboardInput()}），再执行原调用，
     * 最后仅当本屏幕仍是 {@code Minecraft.currentScreen} 时补发 {@code Post}
     * （防 Esc 等关屏/换屏后误发）。全程不调用 {@code Keyboard.next()}，
     * LWJGL2 当前事件始终有效。</p>
     */
    @WrapOperation(method = "handleInput",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiScreen;handleKeyboardInput()V"))
    private void catframe$fireKeyboardInputEvents(GuiScreen screen, Operation<Void> original) {
        if (!MinecraftForge.EVENT_BUS.post(new CatFrameGuiEvent.KeyboardInputEvent.Pre(screen))) {
            original.call(screen);
        }
        if (screen == Minecraft.getMinecraft().currentScreen) {
            MinecraftForge.EVENT_BUS.post(new CatFrameGuiEvent.KeyboardInputEvent.Post(screen));
        }
    }
}
