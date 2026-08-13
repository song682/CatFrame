package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.overlay.Overlay;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayContext;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayManager;
import decok.dfcdvadstf.catframe.ui.overlay.ScreenAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

/**
 * <p>
 * Title 覆盖层 —— 复刻高版本 Minecraft 的屏幕标题（{@code /title}）。<br>
 * 标题以 4 倍缩放的大号文字显示在屏幕中央，其下可附加 2 倍缩放的副标题；
 * 显示节奏遵循原版的 淡入(fadeIn) → 停留(stay) → 淡出(fadeOut) 三段式计时。
 * </p>
 * <p>
 * Title overlay — a faithful re-implementation of modern Minecraft's screen title
 * ({@code /title}). The title renders as 4x-scaled large text at the screen centre with
 * an optional 2x-scaled subtitle below, following the vanilla fadeIn → stay → fadeOut
 * three-phase timing.
 * </p>
 *
 * <h3>渲染方式 / How it renders</h3>
 * <p>
 * 本类作为 HUD 上下文（{@link OverlayContext#HUD}）的 {@link Overlay} 注册进
 * {@link OverlayManager}。宽高恒为 0，因此 {@link ScreenAnchor#CENTER} 解析出的
 * {@link #getX()}/{@link #getY()} 恰好是屏幕中心点；{@link #renderWidget(GuiGraphicsExtractor, int, int, float)}
 * 以该点为原点做 GL 平移后再按缩放绘制两行文字（对齐原版 GuiIngame 的做法）。
 * </p>
 * <p>
 * Registered with {@link OverlayManager} as a {@link OverlayContext#HUD} overlay. Width and
 * height are both 0, so {@link ScreenAnchor#CENTER} resolves {@link #getX()}/{@link #getY()}
 * to the exact screen centre; {@link #renderWidget(GuiGraphicsExtractor, int, int, float)} translates the GL origin there
 * and draws both scaled lines, matching the vanilla GuiIngame approach.
 * </p>
 *
 * <h3>时间语义 / Timing semantics</h3>
 * <p>
 * 淡入 / 停留 / 淡出 时间是<b>纯客户端会话状态</b>：作为单例实例字段保存，不随世界
 * 卸载或服务器切换而重置，因此对客户端而言跨存档、跨服务器持续生效；仅客户端重启
 * （JVM 结束）或显式调用 {@link #reset()} 才会恢复默认值 10 / 70 / 20 ticks。
 * 这一行为对齐原版 {@code /title times}：时间值只下发到客户端而不存储在服务端。
 * <b>维护约定：不得在世界卸载、断开连接等时机重置这些字段。</b>
 * <br>The fadeIn / stay / fadeOut times are <b>client-session state</b>: stored as singleton
 * instance fields, never reset on world unload or server switch, hence persisting across
 * saves and servers from the client's point of view; only a client restart (JVM exit) or an
 * explicit {@link #reset()} restores the defaults of 10 / 70 / 20 ticks. This mirrors vanilla
 * {@code /title times}: the values are sent to the client only and never stored server-side.
 * <b>Maintenance contract: never reset these fields on world unload / disconnect.</b>
 * </p>
 *
 * <h3>对照高版本 / Mapping to modern Minecraft</h3>
 * <ul>
 *   <li>{@code /title <目标> title <文本>} → {@link #showTitle(Text)}</li>
 *   <li>{@code /title <目标> subtitle <文本>} → {@link #setSubtitle(Text)}</li>
 *   <li>{@code /title <目标> times <淡入> <停留> <淡出>} → {@link #setTimes(int, int, int)}</li>
 *   <li>{@code /title <目标> clear} → {@link #clear()}</li>
 *   <li>{@code /title <目标> reset} → {@link #reset()}</li>
 *   <li>标题 4x / 副标题 2x 缩放、y 偏移 -10 / +5 → 对齐原版 {@code GuiIngame} 渲染</li>
 * </ul>
 *
 * <h3>文本组件 / Text components</h3>
 * <p>
 * 标题与副标题均接受 {@link Text}（含 {@link Style} 与 siblings 富文本树）。渲染时经
 * {@link Text#getFormattedString()} 拍平为 {@code §} 格式码字符串；根节点 Style 的精确
 * RGB 颜色作为基础绘制色（未指定时为白色）。原始 JSON 文本可先经
 * {@link Text#fromJson(String)} 解析。
 * <br>Both lines accept {@link Text} (with {@link Style} and sibling rich-text tree),
 * flattened via {@link Text#getFormattedString()} into a {@code §}-coded string at render
 * time; the root style's exact RGB acts as the base draw colour (white when absent).
 * Raw JSON text can be parsed beforehand with {@link Text#fromJson(String)}.
 * </p>
 */
public class TitleOverlay extends AbstractComponent implements Overlay {

    /** Singleton instance / 单例实例 */
    public static final TitleOverlay INSTANCE = new TitleOverlay();

    /** Default fade-in ticks (0.5s), matches vanilla / 默认淡入 ticks（0.5 秒），对齐原版 */
    public static final int DEFAULT_FADE_IN_TICKS = 10;

    /** Default stay ticks (3.5s), matches vanilla / 默认停留 ticks（3.5 秒），对齐原版 */
    public static final int DEFAULT_STAY_TICKS = 70;

    /** Default fade-out ticks (1s), matches vanilla / 默认淡出 ticks（1 秒），对齐原版 */
    public static final int DEFAULT_FADE_OUT_TICKS = 20;

    /** Title scale factor, matches vanilla / 标题缩放倍数，对齐原版 */
    private static final float TITLE_SCALE = 4.0F;

    /** Subtitle scale factor, matches vanilla / 副标题缩放倍数，对齐原版 */
    private static final float SUBTITLE_SCALE = 2.0F;

    /** Title Y offset in scaled units from centre, matches vanilla / 标题相对中心的缩放坐标 Y 偏移，对齐原版 */
    private static final int TITLE_Y_OFFSET = -10;

    /** Subtitle Y offset in scaled units from centre, matches vanilla / 副标题相对中心的缩放坐标 Y 偏移，对齐原版 */
    private static final int SUBTITLE_Y_OFFSET = 5;

    /** Minimum visible alpha; below this the frame is skipped (matches vanilla) / 最小可见透明度，低于此跳过绘制（对齐原版） */
    private static final int MIN_ALPHA = 8;

    /** Current title, or {@code null} when nothing is shown / 当前标题，无则为 {@code null} */
    @Nullable
    private Text title;

    /** Current subtitle, or {@code null} for title-only display / 当前副标题，无则仅显示标题 */
    @Nullable
    private Text subtitle;

    /** Configured fade-in ticks / 已配置的淡入 ticks */
    private int fadeInTicks = DEFAULT_FADE_IN_TICKS;

    /** Configured stay ticks / 已配置的停留 ticks */
    private int stayTicks = DEFAULT_STAY_TICKS;

    /** Configured fade-out ticks / 已配置的淡出 ticks */
    private int fadeOutTicks = DEFAULT_FADE_OUT_TICKS;

    /** Remaining ticks across all three phases / 三段计时的总剩余 ticks */
    private int remainingTicks;

    private TitleOverlay() {
    }

    // ──── Public state API ────

    /**
     * Show a title, (re)starting the fadeIn → stay → fadeOut countdown with the currently
     * configured times. A previously set subtitle is displayed along with it.
     * Mirrors {@code /title <target> title <text>}.
     * <p>显示标题，按当前配置的时间重新开始 淡入 → 停留 → 淡出 计时。
     * 先前设置的副标题会随之一起显示。对标 {@code /title <目标> title <文本>}。</p>
     *
     * @param title the title text / 标题文本
     */
    public void showTitle(Text title) {
        this.title = title;
        this.remainingTicks = fadeInTicks + stayTicks + fadeOutTicks;
    }

    /**
     * Set the subtitle shown below the title. Like vanilla, the subtitle alone never
     * triggers a display — it appears when a title is (or becomes) active.
     * Mirrors {@code /title <target> subtitle <text>}.
     * <p>设置显示在标题下方的副标题。与原版一致，单独设置副标题不会触发显示——
     * 它随激活中（或随后激活）的标题一起出现。对标 {@code /title <目标> subtitle <文本>}。</p>
     *
     * @param subtitle the subtitle text, or {@code null} to remove / 副标题文本，{@code null} 表示移除
     */
    public void setSubtitle(@Nullable Text subtitle) {
        this.subtitle = subtitle;
    }

    /**
     * Configure the three-phase timing for subsequent titles. Non-positive totals are
     * ignored per-field by clamping to 0. Mirrors {@code /title <target> times}.
     * <p>配置后续标题的三段计时。各字段负值收敛为 0。对标 {@code /title <目标> times}。</p>
     * <p>
     * 新值对<b>之后的</b> {@link #showTitle(Text)} 生效；对正在显示中的标题，剩余总时长
     * 不变，但透明度曲线的阶段划分从下一帧起按新值解释（与原版一致）。
     * <br>New values apply to <b>subsequent</b> {@link #showTitle(Text)} calls; for a title
     * currently on screen the total remaining time is unchanged, but the alpha-curve phase
     * boundaries are reinterpreted with the new values from the next frame on (matches vanilla).
     * </p>
     *
     * @param fadeIn  fade-in ticks / 淡入 ticks
     * @param stay    stay ticks / 停留 ticks
     * @param fadeOut fade-out ticks / 淡出 ticks
     */
    public void setTimes(int fadeIn, int stay, int fadeOut) {
        this.fadeInTicks = Math.max(0, fadeIn);
        this.stayTicks = Math.max(0, stay);
        this.fadeOutTicks = Math.max(0, fadeOut);
    }

    /** @return configured fade-in ticks / 当前配置的淡入 ticks */
    public int getFadeInTicks() {
        return fadeInTicks;
    }

    /** @return configured stay ticks / 当前配置的停留 ticks */
    public int getStayTicks() {
        return stayTicks;
    }

    /** @return configured fade-out ticks / 当前配置的淡出 ticks */
    public int getFadeOutTicks() {
        return fadeOutTicks;
    }

    /**
     * Immediately clear the current title and subtitle, keeping the configured times.
     * Mirrors {@code /title <target> clear}.
     * <p>立即清除当前标题与副标题，保留已配置的时间。对标 {@code /title <目标> clear}。</p>
     */
    public void clear() {
        this.title = null;
        this.subtitle = null;
        this.remainingTicks = 0;
    }

    /**
     * Clear the display and restore the default times.
     * Mirrors {@code /title <target> reset}.
     * <p>清除显示并恢复默认时间。对标 {@code /title <目标> reset}。</p>
     */
    public void reset() {
        clear();
        this.fadeInTicks = DEFAULT_FADE_IN_TICKS;
        this.stayTicks = DEFAULT_STAY_TICKS;
        this.fadeOutTicks = DEFAULT_FADE_OUT_TICKS;
    }

    /**
     * Advance the countdown by one tick. Call once per client tick while unpaused.
     * <p>将倒计时推进一个 tick。未暂停时每客户端 tick 调用一次。</p>
     */
    public void tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
            if (remainingTicks == 0) {
                // Display finished — drop both lines like vanilla does.
                // 显示结束——像原版一样同时丢弃两行文本。
                this.title = null;
                this.subtitle = null;
            }
        }
    }

    /** @return remaining ticks across all phases / 三段计时的总剩余 ticks */
    public int getRemainingTicks() {
        return remainingTicks;
    }

    // ──── Overlay interface ────

    @Override
    public OverlayContext getContext() {
        return OverlayContext.HUD;
    }

    @Override
    public void update() {
        tick();
    }

    @Override
    public boolean isVisible() {
        return title != null && remainingTicks > 0;
    }

    @Override
    public ScreenAnchor getAnchor() {
        return ScreenAnchor.CENTER;
    }

    @Override
    public int getOffsetX() {
        return 0;
    }

    @Override
    public int getOffsetY() {
        return 0;
    }

    /**
     * Width is always 0 so the CENTER anchor resolves {@link #getX()} to the exact
     * screen centre; the render pass centres each line itself under GL scaling.
     * <p>宽度恒为 0，使 CENTER 锚点把 {@link #getX()} 解析为屏幕正中；
     * 渲染阶段在 GL 缩放下自行对每行文字做居中。</p>
     */
    @Override
    public int getWidth() {
        return 0;
    }

    /**
     * Height is always 0 for the same centre-resolution reason as {@link #getWidth()}.
     * <p>高度恒为 0，理由同 {@link #getWidth()} 的中心解析约定。</p>
     */
    @Override
    public int getHeight() {
        return 0;
    }

    // ──── Rendering ────

    /**
     * Render the title (4x) and optional subtitle (2x) around the manager-assigned centre
     * point ({@link #getX()}, {@link #getY()}). Alpha follows the vanilla three-phase curve:
     * ramp up over fadeIn, hold at 255 during stay, ramp down over fadeOut.
     * <p>以管理器指定的中心点（{@link #getX()}、{@link #getY()}）为原点渲染标题（4 倍）与
     * 可选副标题（2 倍）。透明度遵循原版三段曲线：淡入期爬升、停留期保持 255、淡出期回落。</p>
     * <p>
     * 绘制坐标系是 {@code ScaledResolution} 解析后的 GUI 缩放空间，因此标题实际像素尺寸
     * 随“界面尺寸”设置变化；文本按单行绘制、不自动换行，过长时从中心向两侧对称
     * 溢出屏幕之外（均对齐原版行为）。
     * <br>Drawing happens in the {@code ScaledResolution}-resolved GUI-scale space, so the
     * on-screen title size follows the GUI Scale setting; text is drawn as a single line with
     * no wrapping — over-long titles overflow symmetrically off both screen edges (both
     * matching vanilla behaviour).
     * </p>
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) {
            return;
        }

        String titleStr = title.getFormattedString();
        if (titleStr == null || titleStr.isEmpty()) {
            return;
        }

        // Three-phase alpha, mirroring vanilla GuiIngame's title timing math.
        // 三段式透明度，对齐原版 GuiIngame 的标题计时算法。
        float age = (float) remainingTicks - partialTicks;
        int alpha = 255;
        if (age > (float) (fadeOutTicks + stayTicks)) {
            // Fade-in phase / 淡入阶段
            float elapsed = (float) (fadeInTicks + stayTicks + fadeOutTicks) - age;
            alpha = fadeInTicks > 0 ? (int) (elapsed * 255.0F / (float) fadeInTicks) : 255;
        } else if (age <= (float) fadeOutTicks) {
            // Fade-out phase / 淡出阶段
            alpha = fadeOutTicks > 0 ? (int) (age * 255.0F / (float) fadeOutTicks) : 0;
        }
        if (alpha > 255) {
            alpha = 255;
        }
        if (alpha <= MIN_ALPHA) {
            return;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y, 0.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Title line: 4x scale, centred, base colour from the root style (white fallback).
        // 标题行：4 倍缩放、居中，基础颜色取根节点样式（缺省白色）。
        GL11.glPushMatrix();
        GL11.glScalef(TITLE_SCALE, TITLE_SCALE, TITLE_SCALE);
        int titleArgb = (alpha << 24) | baseColor(title);
        font.drawStringWithShadow(titleStr, -font.getStringWidth(titleStr) / 2, TITLE_Y_OFFSET, titleArgb);
        GL11.glPopMatrix();

        // Subtitle line: 2x scale, centred, only while a title is active (vanilla rule).
        // 副标题行：2 倍缩放、居中，仅在标题激活期间显示（原版规则）。
        if (subtitle != null) {
            String subtitleStr = subtitle.getFormattedString();
            if (subtitleStr != null && !subtitleStr.isEmpty()) {
                GL11.glPushMatrix();
                GL11.glScalef(SUBTITLE_SCALE, SUBTITLE_SCALE, SUBTITLE_SCALE);
                int subtitleArgb = (alpha << 24) | baseColor(subtitle);
                font.drawStringWithShadow(subtitleStr, -font.getStringWidth(subtitleStr) / 2, SUBTITLE_Y_OFFSET, subtitleArgb);
                GL11.glPopMatrix();
            }
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    /**
     * Base RGB draw colour for a line — the root style's exact colour when present,
     * otherwise white. Segment-level colours are already embedded as {@code §} codes
     * by {@link Text#getFormattedString()}.
     * <p>一行文字的基础 RGB 绘制色 —— 根节点样式有颜色时取其精确值，否则白色。
     * 片段级颜色已由 {@link Text#getFormattedString()} 以 {@code §} 码内嵌。</p>
     */
    private static int baseColor(Text text) {
        Style style = text.getStyle();
        if (style != null && style.getColor() != null) {
            return style.getColor().getRgb() & 0xFFFFFF;
        }
        return 0xFFFFFF;
    }
}
