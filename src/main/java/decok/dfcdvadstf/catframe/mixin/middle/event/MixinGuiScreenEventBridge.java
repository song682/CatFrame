package decok.dfcdvadstf.catframe.mixin.middle.event;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import decok.dfcdvadstf.catframe.adapter.forge.event.CatFrameGuiEvent;
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
