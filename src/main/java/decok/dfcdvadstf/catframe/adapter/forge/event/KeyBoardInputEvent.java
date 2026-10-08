package decok.dfcdvadstf.catframe.adapter.forge.event;

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
 * {@code decok.dfcdvadstf.catframe.mixin.middle.event.MixinGuiScreenEventBridge}, which wraps the
 * {@code this.handleKeyboardInput()} call site inside {@code GuiScreen.handleInput()} — the
 * same patch shape Forge 1.8+ applies — so screens overriding {@code handleKeyboardInput}
 * (including CatFrame's own {@code ui.screens.Screen}) are covered as well.
 * </p>
 */
@SideOnly(Side.CLIENT)
public class KeyBoardInputEvent extends Event {

    /**
     * The GuiScreen object generating this event.
     */
    public final GuiScreen gui;

    public KeyBoardInputEvent(GuiScreen gui) {
        this.gui = gui;
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
    public static class Pre extends KeyBoardInputEvent {
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
    public static class Post extends KeyBoardInputEvent {
        public Post(GuiScreen gui) {
            super(gui);
        }
    }
}
