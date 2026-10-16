package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiDrawing;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.overlay.Overlay;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayContext;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayManager;
import decok.dfcdvadstf.catframe.ui.overlay.ScreenAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;

import java.awt.*;

/**
 * <p>
 * ActionBar overlay — a faithful re-implementation of Minecraft's floating text above
 * the hotbar (the {@code /title actionbar} message). Holds a single active message with
 * a 60-tick countdown that fades out over the final 20 ticks.
 * </p>
 *
 * <h3>How it renders</h3>
 * <p>
 * Registered with {@link OverlayManager} as a {@link OverlayContext#HUD} overlay. The pure-Forge
 * {@code ClientOverlayHandler} advances the countdown via {@link OverlayManager#updateAll()} each
 * tick and draws it via {@link OverlayManager#renderHud(float)} from {@code RenderGameOverlayEvent}.
 * The manager resolves the {@link ScreenAnchor#BOTTOM_CENTER} position into {@link #getX()}/
 * {@link #getY()}, so {@link #renderWidget(GuiGraphicsExtractor, int, int, float)} simply draws at those coordinates.
 * </p>
 *
 * <h3>Mapping to modern Minecraft</h3>
 * <ul>
 *   <li>{@code Gui.setOverlayMessage()} → {@link #setMessage(Text, boolean)}</li>
 *   <li>{@code overlayMessageTime = 60} → {@link #DISPLAY_TICKS}</li>
 *   <li>fade-out {@code alpha = t * 255 / 20} → {@link #FADE_TICKS}</li>
 *   <li>{@code Gui.extractOverlayMessage()} → {@link #renderWidget(GuiGraphicsExtractor, int, int, float)}</li>
 * </ul>
 */
public class ActionBarOverlay extends AbstractComponent implements Overlay {

    /** Singleton instance */
    public static final ActionBarOverlay INSTANCE = new ActionBarOverlay();

    /** Total display duration in ticks (3s) */
    public static final int DISPLAY_TICKS = 60;

    /** Fade-out window in ticks (last 1s) */
    public static final int FADE_TICKS = 20;

    /** Distance from screen bottom to the text top */
    public static final int BOTTOM_OFFSET = 68;

    /** Minimum visible alpha; below this the frame is skipped (matches vanilla) */
    private static final int MIN_ALPHA = 8;

    /** Backdrop opacity as a fraction of text alpha */
    private static final float BACKDROP_ALPHA_RATIO = 0.25F;

    /** Current message, or {@code null} when nothing is shown */
    private Text message;

    /** Remaining ticks until the message disappears */
    private int remainingTicks;

    /** Whether to use the HSV rainbow animation (record-style) */
    private boolean animate;

    private ActionBarOverlay() {
    }

    // ──── Public state API ────

    /**
     * Show a message with the given animation flag, resetting the countdown to
     * {@link #DISPLAY_TICKS}. Mirrors {@code Gui.setOverlayMessage()}.
     *
     * @param message the text to display
     * @param animate {@code true} for the HSV rainbow animation, {@code false} for plain white
     */
    public void setMessage(Text message, boolean animate) {
        this.message = message;
        this.remainingTicks = DISPLAY_TICKS;
        this.animate = animate;

        // Cache size so OverlayManager can centre the text against BOTTOM_CENTER.
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String str = message != null ? message.getString() : "";
        this.width = str != null ? font.getStringWidth(str) : 0;
        this.height = font.FONT_HEIGHT;
    }

    /**
     * Show a plain white message (equivalent to {@code /title actionbar}).
     */
    public void setMessage(Text message) {
        setMessage(message, false);
    }

    /**
     * Immediately clear any active message.
     */
    public void clear() {
        this.message = null;
        this.remainingTicks = 0;
    }

    /**
     * Advance the countdown by one tick. Call once per client tick while unpaused.
     */
    public void tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    /** @return remaining ticks */
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
        return message != null && remainingTicks > 0;
    }

    @Override
    public ScreenAnchor getAnchor() {
        return ScreenAnchor.BOTTOM_CENTER;
    }

    @Override
    public int getOffsetX() {
        return 0;
    }

    @Override
    public int getOffsetY() {
        // BOTTOM_CENTER resolves y = screenHeight - height - offset, so subtracting the height
        // places the text top exactly at screenHeight - BOTTOM_OFFSET.
        return BOTTOM_OFFSET - getHeight();
    }

    // ──── Rendering ────

    /**
     * Render the action bar at the manager-assigned position ({@link #getX()}, {@link #getY()}).
     * Computes fade alpha from {@code partialTicks} and picks white or the HSV rainbow colour.
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) {
            return;
        }

        String str = message.getString();
        if (str == null || str.isEmpty()) {
            return;
        }

        // Fade alpha: full opacity until the final FADE_TICKS, then ramps to 0.
        float t = (float) remainingTicks - partialTicks;
        int alpha = (int) (t * 255.0F / (float) FADE_TICKS);
        if (alpha > 255) {
            alpha = 255;
        }
        if (alpha <= MIN_ALPHA) {
            return;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Colour: plain white, or the record-style HSV rainbow when animate == true.
        int rgb;
        if (animate) {
            float hue = (float) remainingTicks / 50.0F;
            rgb = Color.HSBtoRGB(hue, 0.7F, 0.6F) & 0xFFFFFF;
        } else {
            rgb = 0xFFFFFF;
        }
        int argb = (alpha << 24) | rgb;

        int textWidth = font.getStringWidth(str);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Translucent backdrop behind the text (fades with the message).
        int backdropAlpha = (int) (alpha * BACKDROP_ALPHA_RATIO);
        if (backdropAlpha > 0) {
            int backdrop = backdropAlpha << 24;
            GuiDrawing.drawRect(x - 2, y - 2, x + textWidth + 2, y + font.FONT_HEIGHT + 1, backdrop);
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        font.drawStringWithShadow(str, x, y, argb);

        GL11.glDisable(GL11.GL_BLEND);
    }
}
