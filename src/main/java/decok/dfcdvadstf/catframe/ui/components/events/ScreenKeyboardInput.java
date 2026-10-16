package decok.dfcdvadstf.catframe.ui.components.events;

import decok.dfcdvadstf.catframe.ui.navigation.FocusNavigationEvent;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenDirection;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;

/**
 * <p>
 * Screen keyboard input dispatcher — reads LWJGL2's <strong>current</strong> keyboard
 * event and splits it into {@code keyPressed}/{@code keyReleased}/{@code charTyped},
 * dispatching to the root component and handling Tab/Shift+Tab focus navigation. It must
 * be called within the {@code GuiScreen.handleKeyboardInput()} context (where LWJGL's
 * current event is valid) and does <strong>not</strong> call {@code Keyboard.next()}, so
 * it never steals events from vanilla. It also does <strong>not</strong> call
 * {@code keyTyped}: the vanilla {@code this.keyTyped(char, int)} inside
 * {@code handleKeyboardInput()} (for the built-in {@code Screen} base that is
 * {@code Screen.keyTyped} → {@code dispatchKeyTyped}) already is the sole input channel
 * for leaf components that implement only {@code keyTyped}, and dispatching it here
 * again would deliver every keystroke twice (see UI-Completity.md C2 round 3).
 * </p>
 */
public final class ScreenKeyboardInput {

    private ScreenKeyboardInput() {
    }

    /**
     * Read the current LWJGL keyboard event and dispatch split events to {@code root}.
     *
     * @param root the CatFrame root component/container
     */
    public static void handleCurrentEvent(@Nullable GuiEventListener root) {
        if (root == null) {
            return;
        }
        final boolean pressed = Keyboard.getEventKeyState();
        final int keyCode = Keyboard.getEventKey();
        final char character = Keyboard.getEventCharacter();

        if (!pressed) {
            root.keyReleased(keyCode);
            return;
        }

        // ── Tab / Shift+Tab focus navigation (skip Ctrl+Tab: reserved for tab switching) ──
        if (keyCode == Keyboard.KEY_TAB && !KeyTypedEvent.isControlKeyPressed()
                && root instanceof ContainerEventHandler) {
            final boolean forward = !KeyTypedEvent.isShiftKeyPressed();
            final ComponentPath path = root.nextFocusPath(new FocusNavigationEvent.TabNavigation(forward));
            if (path != null) {
                path.applyFocus(true);
                return; // Tab consumed by navigation
            }
        }

        // ── Split key press + char ──
        // Note: the legacy keyTyped bridge has been removed. The vanilla
        // GuiScreen.handleKeyboardInput() loop calls this.keyTyped() →
        // Screen.keyTyped() → dispatchKeyTyped(), which already delivers
        // the event to all children exactly once. Calling root.keyTyped()
        // here as well caused every keystroke to reach widgets twice.
        final boolean consumed = root.keyPressed(keyCode);
        if (!consumed && isTextCharacter(character)) {
            root.charTyped(character);
        }
    }

    /**
     * Optional arrow-key navigation entry point for hosts that want directional focus
     * movement. Not triggered automatically (arrow keys are needed by text fields/gameplay).
     *
     * @return true if focus moved
     */
    public static boolean navigate(@Nullable GuiEventListener root, ScreenDirection direction) {
        if (!(root instanceof ContainerEventHandler)) {
            return false;
        }
        final ComponentPath path = root.nextFocusPath(new FocusNavigationEvent.ArrowNavigation(direction));
        if (path != null) {
            path.applyFocus(true);
            return true;
        }
        return false;
    }

    /**
     * @return true if the character is a printable/text character worth delivering to
     *         {@code charTyped} (excludes NUL and control/DEL)
     */
    private static boolean isTextCharacter(char c) {
        return c != 0 && c >= 0x20 && c != 0x7F;
    }
}
