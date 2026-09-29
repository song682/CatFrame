package decok.dfcdvadstf.catframe.ui;

import decok.dfcdvadstf.catframe.ui.components.ActionBarOverlay;
import decok.dfcdvadstf.catframe.ui.components.TitleOverlay;

import javax.annotation.Nullable;

/**
 * <p>
 * Title public API facade — concise static calls for the centred large title and subtitle,
 * covering the full modern {@code /title} command set. Title/subtitle delegate to
 * {@link TitleOverlay#INSTANCE}; the actionbar sub-command delegates to
 * {@link ActionBarOverlay#INSTANCE} (same backing as the {@link ActionBar} facade).
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   // Plain / literal
 *   Title.show(Text.literal("Chapter I"));
 *   Title.show("Chapter I", "The Beginning");          // title + subtitle
 *
 *   // Text components with Style
 *   Title.show(Text.literal("Boss", Style.EMPTY.withColor(0xFF5555).withBold(true)));
 *
 *   // Raw JSON text components
 *   Title.showJson("{\"text\":\"Victory\",\"color\":\"gold\",\"bold\":true}");
 *
 *   // Timing & lifecycle
 *   Title.times(10, 70, 20);
 *   Title.actionbar(Text.translatable("my.key"));
 *   Title.clear();
 *   Title.reset();
 * }</pre>
 *
 * <p>Must be called on the client thread. The title renders on the HUD via
 * {@code ClientOverlayHandler}.</p>
 *
 * <p>
 * Timing semantics: values set via {@link #times(int, int, int)} are client-session
 * state persisting across saves and servers; only a client restart or {@link #reset()}
 * restores the defaults of 10 / 70 / 20 ticks (0.5 / 3.5 / 1 s) — see the "Timing semantics"
 * section of {@link TitleOverlay}.
 * </p>
 */
public final class Title {

    private Title() {
    }

    // ──── title ────

    /**
     * Show a title with the currently configured times.
     * Mirrors {@code /title <target> title <text>}.
     */
    public static void show(Text title) {
        TitleOverlay.INSTANCE.showTitle(title);
    }

    /**
     * Convenience overload for a literal string title.
     */
    public static void show(String title) {
        show(Text.literal(title));
    }

    /**
     * Show a title together with a subtitle in one call.
     */
    public static void show(Text title, @Nullable Text subtitle) {
        TitleOverlay.INSTANCE.setSubtitle(subtitle);
        TitleOverlay.INSTANCE.showTitle(title);
    }

    /**
     * Convenience overload for literal string title + subtitle.
     */
    public static void show(String title, @Nullable String subtitle) {
        show(Text.literal(title), subtitle != null ? Text.literal(subtitle) : null);
    }

    /**
     * Show a title + subtitle with explicit timing in one call — equivalent to issuing
     * {@code /title times} followed by {@code /title title}. Note the times <b>persist</b>
     * for subsequent titles (client-session state), exactly like the vanilla command pair.
     *
     * @param fadeIn  fade-in ticks
     * @param stay    stay ticks
     * @param fadeOut fade-out ticks
     */
    public static void show(Text title, @Nullable Text subtitle, int fadeIn, int stay, int fadeOut) {
        TitleOverlay.INSTANCE.setTimes(fadeIn, stay, fadeOut);
        show(title, subtitle);
    }

    /**
     * Show a title parsed from a raw JSON text component (see {@link Text#fromJson}).
     */
    public static void showJson(String titleJson) {
        show(Text.fromJson(titleJson));
    }

    /**
     * Show a JSON title together with a JSON subtitle.
     * <p>同时显示 JSON 标题与 JSON 副标题。</p>
     */
    public static void showJson(String titleJson, @Nullable String subtitleJson) {
        show(Text.fromJson(titleJson), subtitleJson != null ? Text.fromJson(subtitleJson) : null);
    }

    // ──── subtitle ────

    /**
     * Set the subtitle shown below the title. Like vanilla, this alone does not trigger a
     * display — it appears with the active (or next) title.
     * Mirrors {@code /title <target> subtitle <text>}.
     */
    public static void subtitle(@Nullable Text subtitle) {
        TitleOverlay.INSTANCE.setSubtitle(subtitle);
    }

    /**
     * Convenience overload for a literal string subtitle.
     */
    public static void subtitle(@Nullable String subtitle) {
        subtitle(subtitle != null ? Text.literal(subtitle) : null);
    }

    // ──── actionbar ────

    /**
     * Show text in the action bar above the hotbar.
     * Mirrors {@code /title <target> actionbar <text>}.
     */
    public static void actionbar(Text message) {
        ActionBarOverlay.INSTANCE.setMessage(message);
    }

    /**
     * Convenience overload for a literal string actionbar message.
     */
    public static void actionbar(String message) {
        actionbar(Text.literal(message));
    }

    // ──── times / clear / reset ────

    /**
     * Configure fadeIn / stay / fadeOut ticks for subsequent titles.
     * Mirrors {@code /title <target> times <fadeIn> <stay> <fadeOut>}.
     */
    public static void times(int fadeIn, int stay, int fadeOut) {
        TitleOverlay.INSTANCE.setTimes(fadeIn, stay, fadeOut);
    }

    /**
     * Immediately clear the current title and subtitle (times are kept).
     * Mirrors {@code /title <target> clear}.
     */
    public static void clear() {
        TitleOverlay.INSTANCE.clear();
    }

    /**
     * Clear the display and restore default times.
     * Mirrors {@code /title <target> reset}.
     */
    public static void reset() {
        TitleOverlay.INSTANCE.reset();
    }
}
