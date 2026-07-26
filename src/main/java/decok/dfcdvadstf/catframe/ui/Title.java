package decok.dfcdvadstf.catframe.ui;

import decok.dfcdvadstf.catframe.ui.components.ActionBarOverlay;
import decok.dfcdvadstf.catframe.ui.components.TitleOverlay;

import javax.annotation.Nullable;

/**
 * <p>
 * Title 公共 API 门面 —— 提供简洁的静态调用以在屏幕中央显示大号标题与副标题，
 * 对标高版本的 {@code /title} 命令全集。<br>
 * 标题/副标题内部委托给 {@link TitleOverlay#INSTANCE}；动作栏（actionbar）子命令
 * 委托给 {@link ActionBarOverlay#INSTANCE}（与 {@link ActionBar} 门面同源）。
 * </p>
 * <p>
 * Title public API facade — concise static calls for the centred large title and subtitle,
 * covering the full modern {@code /title} command set. Title/subtitle delegate to
 * {@link TitleOverlay#INSTANCE}; the actionbar sub-command delegates to
 * {@link ActionBarOverlay#INSTANCE} (same backing as the {@link ActionBar} facade).
 * </p>
 *
 * <h3>用法 / Usage</h3>
 * <pre>{@code
 *   // Plain / literal
 *   Title.show(Text.literal("Chapter I"));
 *   Title.show("Chapter I", "The Beginning");          // title + subtitle
 *
 *   // Text components with Style / 带样式的文本组件
 *   Title.show(Text.literal("Boss", Style.EMPTY.withColor(0xFF5555).withBold(true)));
 *
 *   // Raw JSON text components / 原始 JSON 文本组件
 *   Title.showJson("{\"text\":\"Victory\",\"color\":\"gold\",\"bold\":true}");
 *
 *   // Timing & lifecycle / 计时与生命周期
 *   Title.times(10, 70, 20);
 *   Title.actionbar(Text.translatable("my.key"));
 *   Title.clear();
 *   Title.reset();
 * }</pre>
 *
 * <p>Must be called on the client thread. The title renders on the HUD via
 * {@code ClientOverlayHandler}.
 * <br>须在客户端线程调用。标题经 {@code ClientOverlayHandler} 在 HUD 上渲染。</p>
 */
public final class Title {

    private Title() {
    }

    // ──── title / 标题 ────

    /**
     * Show a title with the currently configured times.
     * Mirrors {@code /title <target> title <text>}.
     * <p>按当前配置的时间显示标题。对标 {@code /title <目标> title <文本>}。</p>
     */
    public static void show(Text title) {
        TitleOverlay.INSTANCE.showTitle(title);
    }

    /**
     * Convenience overload for a literal string title.
     * <p>字面字符串标题的便捷重载。</p>
     */
    public static void show(String title) {
        show(Text.literal(title));
    }

    /**
     * Show a title together with a subtitle in one call.
     * <p>一次调用同时显示标题与副标题。</p>
     */
    public static void show(Text title, @Nullable Text subtitle) {
        TitleOverlay.INSTANCE.setSubtitle(subtitle);
        TitleOverlay.INSTANCE.showTitle(title);
    }

    /**
     * Convenience overload for literal string title + subtitle.
     * <p>字面字符串标题 + 副标题的便捷重载。</p>
     */
    public static void show(String title, @Nullable String subtitle) {
        show(Text.literal(title), subtitle != null ? Text.literal(subtitle) : null);
    }

    /**
     * Show a title parsed from a raw JSON text component (see {@link Text#fromJson}).
     * <p>显示一个由原始 JSON 文本组件解析而来的标题（见 {@link Text#fromJson}）。</p>
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

    // ──── subtitle / 副标题 ────

    /**
     * Set the subtitle shown below the title. Like vanilla, this alone does not trigger a
     * display — it appears with the active (or next) title.
     * Mirrors {@code /title <target> subtitle <text>}.
     * <p>设置显示在标题下方的副标题。与原版一致，单独设置不会触发显示——
     * 随激活中（或下一个）标题一起出现。对标 {@code /title <目标> subtitle <文本>}。</p>
     */
    public static void subtitle(@Nullable Text subtitle) {
        TitleOverlay.INSTANCE.setSubtitle(subtitle);
    }

    /**
     * Convenience overload for a literal string subtitle.
     * <p>字面字符串副标题的便捷重载。</p>
     */
    public static void subtitle(@Nullable String subtitle) {
        subtitle(subtitle != null ? Text.literal(subtitle) : null);
    }

    // ──── actionbar / 动作栏 ────

    /**
     * Show text in the action bar above the hotbar.
     * Mirrors {@code /title <target> actionbar <text>}.
     * <p>在物品栏上方的动作栏显示文本。对标 {@code /title <目标> actionbar <文本>}。</p>
     */
    public static void actionbar(Text message) {
        ActionBarOverlay.INSTANCE.setMessage(message);
    }

    /**
     * Convenience overload for a literal string actionbar message.
     * <p>字面字符串动作栏消息的便捷重载。</p>
     */
    public static void actionbar(String message) {
        actionbar(Text.literal(message));
    }

    // ──── times / clear / reset ────

    /**
     * Configure fadeIn / stay / fadeOut ticks for subsequent titles.
     * Mirrors {@code /title <target> times <fadeIn> <stay> <fadeOut>}.
     * <p>配置后续标题的淡入 / 停留 / 淡出 ticks。对标 {@code /title <目标> times}。</p>
     */
    public static void times(int fadeIn, int stay, int fadeOut) {
        TitleOverlay.INSTANCE.setTimes(fadeIn, stay, fadeOut);
    }

    /**
     * Immediately clear the current title and subtitle (times are kept).
     * Mirrors {@code /title <target> clear}.
     * <p>立即清除当前标题与副标题（保留时间配置）。对标 {@code /title <目标> clear}。</p>
     */
    public static void clear() {
        TitleOverlay.INSTANCE.clear();
    }

    /**
     * Clear the display and restore default times.
     * Mirrors {@code /title <target> reset}.
     * <p>清除显示并恢复默认时间。对标 {@code /title <目标> reset}。</p>
     */
    public static void reset() {
        TitleOverlay.INSTANCE.reset();
    }
}
