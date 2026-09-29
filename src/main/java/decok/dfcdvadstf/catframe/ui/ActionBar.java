package decok.dfcdvadstf.catframe.ui;

import decok.dfcdvadstf.catframe.ui.components.ActionBarOverlay;

import javax.annotation.Nullable;


/**
 * <p>
 * ActionBar public API facade — concise static calls for showing the floating text above
 * the hotbar. Delegates to {@link ActionBarOverlay#INSTANCE}.
 * </p>
 *
 * <h3>Usage</h3>
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
     */
    public static void show(Text message) {
        ActionBarOverlay.INSTANCE.setMessage(message, false);
    }

    /**
     * Show a message, choosing plain white or the HSV rainbow animation.
     *
     * @param animate {@code true} for the record-style rainbow, {@code false} for white
     */
    public static void show(Text message, @Nullable boolean animate) {
        ActionBarOverlay.INSTANCE.setMessage(message, animate);
    }

    /**
     * Convenience overload for a literal string.
     */
    public static void show(String message, @Nullable boolean animate) {
        ActionBarOverlay.INSTANCE.setMessage(Text.literal(message), animate);
    }

    /**
     * Immediately clear any active message.
     */
    public static void clear() {
        ActionBarOverlay.INSTANCE.clear();
    }
}
