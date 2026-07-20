package decok.dfcdvadstf.catframe.ui.components.events;

import decok.dfcdvadstf.catframe.ui.components.Component;
import decok.dfcdvadstf.catframe.ui.navigation.FocusNavigationEvent;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenDirection;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;

/**
 * <p>
 * 屏幕键盘输入分发器 —— 读取 LWJGL2 的<strong>当前</strong>键盘事件
 * （{@code getEventKey}/{@code getEventKeyState}/{@code getEventCharacter}），
 * 拆分为 {@code keyPressed}/{@code keyReleased}/{@code charTyped} 并派发到根组件，
 * 同时处理 Tab/Shift+Tab 焦点导航。<br>
 * 必须在 {@code GuiScreen.handleKeyboardInput()} 的 {@code while(Keyboard.next())} 循环
 * 上下文中调用（此时 LWJGL 的 {@code current_event} 有效）；本类<strong>不</strong>调用
 * {@code Keyboard.next()}，因此不会「偷走」原版事件。
 * </p>
 * <p>
 * Screen keyboard input dispatcher — reads LWJGL2's <strong>current</strong> keyboard
 * event and splits it into {@code keyPressed}/{@code keyReleased}/{@code charTyped},
 * dispatching to the root component and handling Tab/Shift+Tab focus navigation. It must
 * be called within the {@code GuiScreen.handleKeyboardInput()} context (where LWJGL's
 * current event is valid) and does <strong>not</strong> call {@code Keyboard.next()}, so
 * it never steals events from vanilla.
 * </p>
 */
public final class ScreenKeyboardInput {

    private ScreenKeyboardInput() {
    }

    /**
     * Read the current LWJGL keyboard event and dispatch split events to {@code root}.
     * <p>读取当前 LWJGL 键盘事件并向 {@code root} 派发拆分事件。</p>
     *
     * @param root the CatFrame root component/container / CatFrame 根组件/容器
     */
    public static void handleCurrentEvent(@Nullable Component root) {
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
        final boolean consumed = root.keyPressed(keyCode);
        if (!consumed && isTextCharacter(character)) {
            root.charTyped(character);
        }

        // ── Legacy bridge: keep keyTyped-based widgets working ──
        root.keyTyped(character, keyCode);
    }

    /**
     * Optional arrow-key navigation entry point for hosts that want directional focus
     * movement. Not triggered automatically (arrow keys are needed by text fields/gameplay).
     * <p>可选的方向键导航入口，供需要方向性焦点移动的宿主调用。不会自动触发
     * （方向键通常被文本框/游戏本身使用）。</p>
     *
     * @return true if focus moved / 若焦点发生移动则返回 true
     */
    public static boolean navigate(@Nullable Component root, ScreenDirection direction) {
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
     *         / 若字符是应交给 {@code charTyped} 的可显示字符则返回 true（排除 NUL 与控制/DEL）
     */
    private static boolean isTextCharacter(char c) {
        return c != 0 && c >= 0x20 && c != 0x7F;
    }
}
