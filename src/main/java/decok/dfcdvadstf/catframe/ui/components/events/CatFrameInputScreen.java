package decok.dfcdvadstf.catframe.ui.components.events;

import decok.dfcdvadstf.catframe.mixin.middle.event.MixinGuiScreen;

import javax.annotation.Nullable;

/**
 * <p>
 * CatFrame input screen marker — implemented by a host {@code GuiScreen} subclass to opt
 * into CatFrame's split keyboard events (keyPressed/keyReleased/charTyped) and Tab focus
 * navigation. {@link MixinGuiScreen} detects this
 * interface, reads the LWJGL2 keyboard event and auto-routes it to the component returned
 * by {@link #getEventRoot()}.
 * </p>
 * <p>
 * Contract:<br>
 * A screen implementing this interface <strong>must not</strong> also forward keys to the
 * root manually inside its own {@code keyTyped}, otherwise events are dispatched twice
 * (this mechanism already bridges legacy {@code keyTyped} via {@code root.keyTyped}).
 * </p>
 */
public interface CatFrameInputScreen {

    /**
     * @return the root component/container that should receive keyboard input, or
     *         {@code null} to skip CatFrame dispatch this frame
     */
    @Nullable
    GuiEventListener getEventRoot();

    /**
     * Whether this screen dispatches CatFrame split keyboard events on its own — e.g. by
     * overriding {@code GuiScreen.handleKeyboardInput()} and calling {@code ScreenKeyboardInput}
     * directly. When {@code true}, {@link MixinGuiScreen}
     * <strong>must not</strong> dispatch for this screen, otherwise every key would be delivered
     * twice (once by the screen, once by the mixin).
     * <p>
     * Defaults to {@code false}: a foreign host {@code GuiScreen} (vanilla / another mod) that
     * merely implements this interface cannot self-dispatch, so the mixin drives it. The built-in
     * {@link decok.dfcdvadstf.catframe.ui.screens.Screen} base overrides this to {@code true}.
     * </p>
     *
     * @return {@code true} to opt out of mixin dispatch (self-dispatching screen)
     */
    default boolean handlesKeyboardDispatchInternally() {
        return false;
    }
}
