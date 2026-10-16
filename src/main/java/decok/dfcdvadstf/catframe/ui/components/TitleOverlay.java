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
 * Title overlay — a faithful re-implementation of modern Minecraft's screen title
 * ({@code /title}). The title renders as 4x-scaled large text at the screen centre with
 * an optional 2x-scaled subtitle below, following the vanilla fadeIn → stay → fadeOut
 * three-phase timing.
 * </p>
 *
 * <h3>How it renders</h3>
 * <p>
 * Registered with {@link OverlayManager} as a {@link OverlayContext#HUD} overlay. Width and
 * height are both 0, so {@link ScreenAnchor#CENTER} resolves {@link #getX()}/{@link #getY()}
 * to the exact screen centre; {@link #renderWidget(GuiGraphicsExtractor, int, int, float)} translates the GL origin there
 * and draws both scaled lines, matching the vanilla GuiIngame approach.
 * </p>
 *
 * <h3>Timing semantics</h3>
 * <p>
 * <br>The fadeIn / stay / fadeOut times are <b>client-session state</b>: stored as singleton
 * instance fields, never reset on world unload or server switch, hence persisting across
 * saves and servers from the client's point of view; only a client restart (JVM exit) or an
 * explicit {@link #reset()} restores the defaults of 10 / 70 / 20 ticks. This mirrors vanilla
 * {@code /title times}: the values are sent to the client only and never stored server-side.
 * <b>Maintenance contract: never reset these fields on world unload / disconnect.</b>
 * </p>
 *
 * <h3>Mapping to modern Minecraft</h3>
 * <ul>
 *   <li>{@code /title <target> title <text>} → {@link #showTitle(Text)}</li>
 *   <li>{@code /title <target> subtitle <text>} → {@link #setSubtitle(Text)}</li>
 *   <li>{@code /title <target> times <fadeIn> <stay> <fadeOut>} → {@link #setTimes(int, int, int)}</li>
 *   <li>{@code /title <target> clear} → {@link #clear()}</li>
 *   <li>{@code /title <target> reset} → {@link #reset()}</li>
 *   <li>title 4x / subtitle 2x scale, y offset -10 / +5 → matches vanilla {@code GuiIngame} rendering</li>
 * </ul>
 *
 * <h3>Text components</h3>
 * <p>
 * <br>Both lines accept {@link Text} (with {@link Style} and sibling rich-text tree),
 * flattened via {@link Text#getFormattedString()} into a {@code §}-coded string at render
 * time; the root style's exact RGB acts as the base draw colour (white when absent).
 * Raw JSON text can be parsed beforehand with {@link Text#fromJson(String)}.
 * </p>
 */
public class TitleOverlay extends AbstractComponent implements Overlay {

    /** Singleton instance */
    public static final TitleOverlay INSTANCE = new TitleOverlay();

    /** Default fade-in ticks (0.5s), matches vanilla */
    public static final int DEFAULT_FADE_IN_TICKS = 10;

    /** Default stay ticks (3.5s), matches vanilla */
    public static final int DEFAULT_STAY_TICKS = 70;

    /** Default fade-out ticks (1s), matches vanilla */
    public static final int DEFAULT_FADE_OUT_TICKS = 20;

    /** Title scale factor, matches vanilla */
    private static final float TITLE_SCALE = 4.0F;

    /** Subtitle scale factor, matches vanilla */
    private static final float SUBTITLE_SCALE = 2.0F;

    /** Title Y offset in scaled units from centre, matches vanilla */
    private static final int TITLE_Y_OFFSET = -10;

    /** Subtitle Y offset in scaled units from centre, matches vanilla */
    private static final int SUBTITLE_Y_OFFSET = 5;

    /** Minimum visible alpha; below this the frame is skipped (matches vanilla) */
    private static final int MIN_ALPHA = 8;

    /** Current title, or {@code null} when nothing is shown */
    @Nullable
    private Text title;

    /** Current subtitle, or {@code null} for title-only display */
    @Nullable
    private Text subtitle;

    /** Configured fade-in ticks */
    private int fadeInTicks = DEFAULT_FADE_IN_TICKS;

    /** Configured stay ticks */
    private int stayTicks = DEFAULT_STAY_TICKS;

    /** Configured fade-out ticks */
    private int fadeOutTicks = DEFAULT_FADE_OUT_TICKS;

    /** Remaining ticks across all three phases */
    private int remainingTicks;

    private TitleOverlay() {
    }

    // ──── Public state API ────

    /**
     * Show a title, (re)starting the fadeIn → stay → fadeOut countdown with the currently
     * configured times. A previously set subtitle is displayed along with it.
     * Mirrors {@code /title <target> title <text>}.
     *
     * @param title the title text
     */
    public void showTitle(Text title) {
        this.title = title;
        this.remainingTicks = fadeInTicks + stayTicks + fadeOutTicks;
    }

    /**
     * Set the subtitle shown below the title. Like vanilla, the subtitle alone never
     * triggers a display — it appears when a title is (or becomes) active.
     * Mirrors {@code /title <target> subtitle <text>}.
     *
     * @param subtitle the subtitle text, or {@code null} to remove
     */
    public void setSubtitle(@Nullable Text subtitle) {
        this.subtitle = subtitle;
    }

    /**
     * Configure the three-phase timing for subsequent titles. Non-positive totals are
     * ignored per-field by clamping to 0. Mirrors {@code /title <target> times}.
     * <p>
     * <br>New values apply to <b>subsequent</b> {@link #showTitle(Text)} calls; for a title
     * currently on screen the total remaining time is unchanged, but the alpha-curve phase
     * boundaries are reinterpreted with the new values from the next frame on (matches vanilla).
     * </p>
     *
     * @param fadeIn  fade-in ticks
     * @param stay    stay ticks
     * @param fadeOut fade-out ticks
     */
    public void setTimes(int fadeIn, int stay, int fadeOut) {
        this.fadeInTicks = Math.max(0, fadeIn);
        this.stayTicks = Math.max(0, stay);
        this.fadeOutTicks = Math.max(0, fadeOut);
    }

    /** @return configured fade-in ticks */
    public int getFadeInTicks() {
        return fadeInTicks;
    }

    /** @return configured stay ticks */
    public int getStayTicks() {
        return stayTicks;
    }

    /** @return configured fade-out ticks */
    public int getFadeOutTicks() {
        return fadeOutTicks;
    }

    /**
     * Immediately clear the current title and subtitle, keeping the configured times.
     * Mirrors {@code /title <target> clear}.
     */
    public void clear() {
        this.title = null;
        this.subtitle = null;
        this.remainingTicks = 0;
    }

    /**
     * Clear the display and restore the default times.
     * Mirrors {@code /title <target> reset}.
     */
    public void reset() {
        clear();
        this.fadeInTicks = DEFAULT_FADE_IN_TICKS;
        this.stayTicks = DEFAULT_STAY_TICKS;
        this.fadeOutTicks = DEFAULT_FADE_OUT_TICKS;
    }

    /**
     * Advance the countdown by one tick. Call once per client tick while unpaused.
     */
    public void tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
            if (remainingTicks == 0) {
                // Display finished — drop both lines like vanilla does.
                this.title = null;
                this.subtitle = null;
            }
        }
    }

    /** @return remaining ticks across all phases */
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
     */
    @Override
    public int getWidth() {
        return 0;
    }

    /**
     * Height is always 0 for the same centre-resolution reason as {@link #getWidth()}.
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
     * <p>
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
        float age = (float) remainingTicks - partialTicks;
        int alpha = 255;
        if (age > (float) (fadeOutTicks + stayTicks)) {
            // Fade-in phase
            float elapsed = (float) (fadeInTicks + stayTicks + fadeOutTicks) - age;
            alpha = fadeInTicks > 0 ? (int) (elapsed * 255.0F / (float) fadeInTicks) : 255;
        } else if (age <= (float) fadeOutTicks) {
            // Fade-out phase
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
        GL11.glPushMatrix();
        GL11.glScalef(TITLE_SCALE, TITLE_SCALE, TITLE_SCALE);
        int titleArgb = (alpha << 24) | baseColor(title);
        font.drawStringWithShadow(titleStr, -font.getStringWidth(titleStr) / 2, TITLE_Y_OFFSET, titleArgb);
        GL11.glPopMatrix();

        // Subtitle line: 2x scale, centred, only while a title is active (vanilla rule).
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
     */
    private static int baseColor(Text text) {
        Style style = text.getStyle();
        if (style != null && style.getColor() != null) {
            return style.getColor().getRgb() & 0xFFFFFF;
        }
        return 0xFFFFFF;
    }
}
