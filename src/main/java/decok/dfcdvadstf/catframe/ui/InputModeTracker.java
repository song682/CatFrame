package decok.dfcdvadstf.catframe.ui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * Tracks whether the most recent input was keyboard navigation or the mouse.
 * <p>
 * Counterpart of 26.1.2 {@code Minecraft#getLastInputType()} / {@code InputType}, set by
 * {@code KeyboardHandler} and {@code MouseHandler}:
 * </p>
 * <ul>
 *   <li>Tab and arrow key presses set keyboard mode (KEYBOARD_TAB / KEYBOARD_ARROW).</li>
 *   <li>Mouse button presses set mouse mode (MOUSE). Cursor movement does not change it.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class InputModeTracker {

    private static boolean keyboardMode = false;

    private InputModeTracker() {}

    /**
     * Call on every keyboard event (reads the current LWJGL event).
     */
    public static void onKeyboardInput() {
        if (!Keyboard.getEventKeyState()) {
            return;
        }
        switch (Keyboard.getEventKey()) {
            case Keyboard.KEY_TAB:
            case Keyboard.KEY_UP:
            case Keyboard.KEY_DOWN:
            case Keyboard.KEY_LEFT:
            case Keyboard.KEY_RIGHT:
                keyboardMode = true;
                break;
            default:
                break;
        }
    }

    /**
     * Call on mouse button presses.
     */
    public static void onMouseInput() {
        keyboardMode = false;
    }

    /**
     * @return true if the last input was keyboard navigation (26.1.2 {@code InputType.isKeyboard()}).
     */
    public static boolean isKeyboardMode() {
        return keyboardMode;
    }
}
