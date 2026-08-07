package decok.dfcdvadstf.catframe.compact.forge.event;

import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;

/**
 * CatFrame's backfill of the Forge 1.8+ {@code GuiScreenEvent.KeyboardInputEvent} family for
 * 1.7.10, where vanilla Forge's {@code GuiScreenEvent} only offers
 * {@code InitGuiEvent}/{@code DrawScreenEvent}/{@code ActionPerformedEvent} and
 * {@code InputEvent.KeyInputEvent} never fires while a GUI is open (it is guarded by
 * {@code currentScreen == null || allowUserInput} in {@code Minecraft.runTick()}).
 * <p>
 * All events are posted on {@code MinecraftForge.EVENT_BUS} (not the FML bus), mirroring the
 * bus choice of Forge's own {@code GuiScreenEvent}. They are fired from
 * {@code decok.dfcdvadstf.catframe.mixin.middle.MixinGuiScreenEventBridge}, which wraps the
 * {@code this.handleKeyboardInput()} call site inside {@code GuiScreen.handleInput()} — the
 * same patch shape Forge 1.8+ applies — so screens overriding {@code handleKeyboardInput}
 * (including CatFrame's own {@code ui.screens.Screen}) are covered as well.
 * </p>
 * <p>
 * 本类将 Forge 1.8+ 的 {@code GuiScreenEvent.KeyboardInputEvent} 家族回填到 1.7.10——
 * 该版本原生 {@code GuiScreenEvent} 仅有 Init/Draw/ActionPerformed 三种子事件，而
 * {@code InputEvent.KeyInputEvent} 在 GUI 打开期间被 {@code Minecraft.runTick()} 的
 * {@code currentScreen == null || allowUserInput} 守卫罩住，完全不触发。
 * 所有事件发布到 {@code MinecraftForge.EVENT_BUS}（非 FML 总线），发射点为
 * {@code MixinGuiScreenEventBridge}：包住 {@code GuiScreen.handleInput()} 中
 * {@code this.handleKeyboardInput()} 的调用点（与 Forge 1.8+ 官方补丁同构），
 * 因此覆写了 {@code handleKeyboardInput} 的屏幕（含 CatFrame 自有 {@code Screen} 基类）同样被覆盖。
 * </p>
 */
@SideOnly(Side.CLIENT)
public class CatFrameGuiEvent extends Event {

    /**
     * The GuiScreen object generating this event.
     */
    public final GuiScreen gui;

    public CatFrameGuiEvent(GuiScreen gui) {
        this.gui = gui;
    }

    /**
     * Fired around one keyboard event of an open screen, inside the vanilla
     * {@code while(Keyboard.next())} loop of {@code GuiScreen.handleInput()} — LWJGL2's
     * <strong>current</strong> event is valid, so subscribers may freely read
     * {@code Keyboard.getEventKey()}/{@code getEventKeyState()}/{@code getEventCharacter()}.
     * Subscribers must <strong>not</strong> call {@code Keyboard.next()} themselves, or they
     * would steal subsequent events from vanilla.
     */
    public static class KeyboardInputEvent extends CatFrameGuiEvent {

        public KeyboardInputEvent(GuiScreen gui) {
            super(gui);
        }

        /**
         * Fired <strong>before</strong> the screen processes the current keyboard event.
         * Canceling skips the entire {@code GuiScreen.handleKeyboardInput()} for this event:
         * vanilla {@code keyTyped} (including Esc-to-close), CatFrame's split-key dispatch
         * ({@code keyPressed}/{@code keyReleased}/{@code charTyped} + Tab focus navigation)
         * and {@code Minecraft.func_152348_aa()} (global key dispatch, e.g. screenshot) are
         * all suppressed. Cancellation affects only the current event, not the rest of the
         * loop. Intended e.g. for IMEs swallowing keys during composition.
         */
        @Cancelable
        public static class Pre extends KeyboardInputEvent {
            public Pre(GuiScreen gui) {
                super(gui);
            }
        }

        /**
         * Fired <strong>after</strong> the screen processed (or, if {@link Pre} was canceled,
         * skipped) the current keyboard event — but only while {@code gui} is still
         * {@code Minecraft.currentScreen}: if handling closed or switched the screen
         * (e.g. Esc), Post is not fired for the dead screen
         */
        public static class Post extends KeyboardInputEvent {
            public Post(GuiScreen gui) {
                super(gui);
            }
        }
    }
}
