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
     * <p>产生本事件的 GuiScreen 实例。</p>
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
     * <p>
     * 围绕打开中屏幕的<strong>单个</strong>键盘事件触发，处于原版
     * {@code GuiScreen.handleInput()} 的 {@code while(Keyboard.next())} 循环内——
     * LWJGL2 当前事件有效，订阅者可自由读取
     * {@code Keyboard.getEventKey()}/{@code getEventKeyState()}/{@code getEventCharacter()}；
     * 但<strong>禁止</strong>自行调用 {@code Keyboard.next()}，否则会偷走原版后续事件。
     * </p>
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
         * <p>
         * 在屏幕处理当前键盘事件<strong>之前</strong>触发。取消本事件将整体跳过这一次
         * {@code GuiScreen.handleKeyboardInput()}：原版 {@code keyTyped}（含 Esc 关屏）、
         * CatFrame 拆分派发（{@code keyPressed}/{@code keyReleased}/{@code charTyped} 及
         * Tab 焦点导航）、{@code Minecraft.func_152348_aa()}（截图等全局按键派发）均被压掉。
         * 取消只作用于当前事件，不影响循环内后续按键。典型用途：输入法组合期间吞掉按键。
         * </p>
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
         * (e.g. Esc), Post is not fired for the dead screen.
         * <p>
         * 在屏幕处理完（若 {@link Pre} 被取消则为跳过）当前键盘事件<strong>之后</strong>触发；
         * 仅当 {@code gui} 仍是 {@code Minecraft.currentScreen} 时才发出——若处理过程中
         * 发生关屏/换屏（如 Esc），不会对已失效的屏幕补发 Post。
         * </p>
         */
        public static class Post extends KeyboardInputEvent {

            public Post(GuiScreen gui) {
                super(gui);
            }
        }
    }
}
