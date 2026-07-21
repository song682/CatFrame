package decok.dfcdvadstf.catframe.ui.components;

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
 * ActionBar 覆盖层 —— 复刻 Minecraft 的物品栏上方浮动文字（{@code /title actionbar}）。<br>
 * 单例状态：一条当前消息 + 60 ticks 倒计时，最后 20 ticks 内线性淡出。
 * </p>
 * <p>
 * ActionBar overlay — a faithful re-implementation of Minecraft's floating text above
 * the hotbar (the {@code /title actionbar} message). Holds a single active message with
 * a 60-tick countdown that fades out over the final 20 ticks.
 * </p>
 *
 * <h3>渲染方式 / How it renders</h3>
 * <p>
 * 本类作为 HUD 上下文（{@link OverlayContext#HUD}）的 {@link Overlay} 注册进
 * {@link OverlayManager}。由 {@code ClientOverlayHandler}（纯 Forge）每 tick 调用
 * {@link OverlayManager#updateAll()} 推进倒计时，并在 {@code RenderGameOverlayEvent.Post}
 * 中调用 {@link OverlayManager#renderHud(float)} 完成绘制。位置由管理器按
 * {@link ScreenAnchor#BOTTOM_CENTER} 解析后写入 {@link #getX()}/{@link #getY()}，
 * 因此 {@link #render(int, int, float)} 直接在该坐标绘制。
 * </p>
 * <p>
 * Registered with {@link OverlayManager} as a {@link OverlayContext#HUD} overlay. The pure-Forge
 * {@code ClientOverlayHandler} advances the countdown via {@link OverlayManager#updateAll()} each
 * tick and draws it via {@link OverlayManager#renderHud(float)} from {@code RenderGameOverlayEvent}.
 * The manager resolves the {@link ScreenAnchor#BOTTOM_CENTER} position into {@link #getX()}/
 * {@link #getY()}, so {@link #render(int, int, float)} simply draws at those coordinates.
 * </p>
 *
 * <h3>对照高版本 / Mapping to modern Minecraft</h3>
 * <ul>
 *   <li>{@code Gui.setOverlayMessage()} → {@link #setMessage(Text, boolean)}</li>
 *   <li>{@code overlayMessageTime = 60} → {@link #DISPLAY_TICKS}</li>
 *   <li>淡出 {@code alpha = t * 255 / 20} → {@link #FADE_TICKS}</li>
 *   <li>{@code Gui.extractOverlayMessage()} → {@link #render(int, int, float)}</li>
 * </ul>
 */
public class ActionBarOverlay extends AbstractComponent implements Overlay {

    /** Singleton instance / 单例实例 */
    public static final ActionBarOverlay INSTANCE = new ActionBarOverlay();

    /** Total display duration in ticks (3s) / 总显示时长(ticks，3秒) */
    public static final int DISPLAY_TICKS = 60;

    /** Fade-out window in ticks (last 1s) / 淡出窗口(ticks，最后1秒) */
    public static final int FADE_TICKS = 20;

    /** Distance from screen bottom to the text top / 文字顶部距屏幕底部的像素距离 */
    public static final int BOTTOM_OFFSET = 68;

    /** Minimum visible alpha; below this the frame is skipped (matches vanilla) / 最小可见透明度，低于此跳过绘制（对齐原版） */
    private static final int MIN_ALPHA = 8;

    /** Backdrop opacity as a fraction of text alpha / 背景不透明度（相对文字透明度的比例） */
    private static final float BACKDROP_ALPHA_RATIO = 0.25F;

    /** Current message, or {@code null} when nothing is shown / 当前消息，无则为 {@code null} */
    private Text message;

    /** Remaining ticks until the message disappears / 距消息消失的剩余 ticks */
    private int remainingTicks;

    /** Whether to use the HSV rainbow animation (record-style) / 是否使用 HSV 彩虹动画（唱片风格） */
    private boolean animate;

    private ActionBarOverlay() {
    }

    // ──── Public state API ────

    /**
     * Show a message with the given animation flag, resetting the countdown to
     * {@link #DISPLAY_TICKS}. Mirrors {@code Gui.setOverlayMessage()}.
     * <p>显示一条消息并将倒计时重置为 {@link #DISPLAY_TICKS}。对标 {@code Gui.setOverlayMessage()}。</p>
     *
     * @param message the text to display / 要显示的文本
     * @param animate {@code true} for the HSV rainbow animation, {@code false} for plain white
     *                / {@code true} 为 HSV 彩虹动画，{@code false} 为纯白
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
     * <p>显示一条纯白消息（等价于 {@code /title actionbar}）。</p>
     */
    public void setMessage(Text message) {
        setMessage(message, false);
    }

    /**
     * Immediately clear any active message.
     * <p>立即清除当前消息。</p>
     */
    public void clear() {
        this.message = null;
        this.remainingTicks = 0;
    }

    /**
     * Advance the countdown by one tick. Call once per client tick while unpaused.
     * <p>将倒计时推进一个 tick。未暂停时每客户端 tick 调用一次。</p>
     */
    public void tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    /** @return remaining ticks / 剩余 ticks */
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
     * <p>在管理器指定的位置（{@link #getX()}、{@link #getY()}）渲染。根据 {@code partialTicks}
     * 计算淡出透明度，选择纯白或 HSV 彩虹色。</p>
     */
    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
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
