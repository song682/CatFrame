package decok.dfcdvadstf.catframe.ui;

import javax.annotation.Nullable;

import decok.dfcdvadstf.catframe.ui.components.ActionBarOverlay;


/**
 * <p>
 * ActionBar 公共 API 门面 —— 提供简洁的静态调用以显示物品栏上方浮动文字。<br>
 * 内部委托给 {@link ActionBarOverlay#INSTANCE}。
 * </p>
 * <p>
 * ActionBar public API facade — concise static calls for showing the floating text above
 * the hotbar. Delegates to {@link ActionBarOverlay#INSTANCE}.
 * </p>
 *
 * <h3>用法 / Usage</h3>
 * <pre>{@code
 *   ActionBar.show("\u00a7aSaved!");                 // plain white
 *   ActionBar.show(Text.translatable("my.key"));     // translatable
 *   ActionBar.show(Text.literal("Now playing"), true); // HSV rainbow (record-style)
 *   ActionBar.clear();
 * }</pre>
 *
 * <p>Must be called on the client thread. The message renders on the HUD via
 * {@code ClientActionBarHandler}.</p>
 */
public final class ActionBar {

    private ActionBar() {
    }

    /**
     * Show a plain white message for {@link ActionBarOverlay#DISPLAY_TICKS} ticks.
     * <p>显示一条纯白消息，持续 {@link ActionBarOverlay#DISPLAY_TICKS} ticks。</p>
     */
    public static void show(Text message) {
        ActionBarOverlay.INSTANCE.setMessage(message, false);
    }

    /**
     * Show a message, choosing plain white or the HSV rainbow animation.
     * <p>显示一条消息，可选纯白或 HSV 彩虹动画。</p>
     *
     * @param animate {@code true} for the record-style rainbow, {@code false} for white
     *                / {@code true} 为唱片风格彩虹，{@code false} 为纯白
     */
    public static void show(Text message, @Nullable boolean animate) {
        ActionBarOverlay.INSTANCE.setMessage(message, animate);
    }

    /**
     * Convenience overload for a literal string.
     * <p>字面字符串便捷重载。</p>
     */
    public static void show(String message, @Nullable boolean animate) {
        ActionBarOverlay.INSTANCE.setMessage(Text.literal(message), animate);
    }

    /**
     * Immediately clear any active message.
     * <p>立即清除当前消息。</p>
     */
    public static void clear() {
        ActionBarOverlay.INSTANCE.clear();
    }
}
