package decok.dfcdvadstf.catframe.ui.components.toast;

import decok.dfcdvadstf.catframe.ui.components.AbstractComponent;
import decok.dfcdvadstf.catframe.ui.components.GuiDrawing;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * Toast 基础实现类<br>
 * 继承 {@link AbstractComponent}，提供通用的背景渲染和基础功能。
 * </p>
 * <p>
 * Base Toast implementation — extends {@link AbstractComponent} for common background
 * rendering and basic functionality.
 * </p>
 */
public abstract class BaseToast extends AbstractComponent implements Toast {

    /** Default display time (ms) / 默认显示时间(毫秒) */
    protected static final long DEFAULT_DISPLAY_TIME = 5000L;

    /** Background colour / 背景颜色 */
    protected static final int BACKGROUND_COLOR = 0xCC000000;

    /** Border colour / 边框颜色 */
    protected static final int BORDER_COLOR = 0xFF555555;

    /** Default toast background texture / 默认 Toast 背景纹理 */
    protected static final ResourceLocation DEFAULT_TOAST_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/toast/default.png");

    /**
     * Background texture for this toast. Null = use solid colour fallback.
     * <p>此 Toast 的背景纹理。null = 回退到纯色。</p>
     */
    protected ResourceLocation backgroundTexture = DEFAULT_TOAST_TEXTURE;

    /** Minecraft instance / Minecraft 实例 */
    protected final Minecraft mc = Minecraft.getMinecraft();

    /** Current wanted visibility / 当前期望的可见性 */
    protected Visibility wantedVisibility = Visibility.HIDE;

    /** Last fully-visible duration from update() / 最近一次 update() 的完全可见时长 */
    private long lastFullyVisibleForMs;

    // ──── Texture API ────

    /**
     * Set the background texture for this Toast.
     * <p>设置此 Toast 的背景纹理。</p>
     *
     * @param texture background texture ResourceLocation, or null to use solid colour fallback
     *                / 背景纹理 ResourceLocation，null 则回退到纯色
     * @return this instance for chaining / 返回自身以支持链式调用
     */
    public BaseToast setBackgroundTexture(ResourceLocation texture) {
        this.backgroundTexture = texture;
        return this;
    }

    /**
     * Get the current background texture.
     * <p>获取当前背景纹理。</p>
     */
    public ResourceLocation getBackgroundTexture() {
        return backgroundTexture;
    }

    // ──── Constructors ────

    public BaseToast() {
        this.width = DEFAULT_WIDTH;
        this.height = SLOT_HEIGHT;
    }

    public BaseToast(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    // ──── Toast interface ────

    @Override
    public Visibility getWantedVisibility() {
        return wantedVisibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        this.lastFullyVisibleForMs = fullyVisibleForMs;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        renderBackground();

        renderContent(mc.fontRenderer, lastFullyVisibleForMs);
    }

    // ──── Background rendering ────

    /**
     * Render the Toast background.
     * <p>渲染 Toast 背景。</p>
     * <p>Uses texture-based nine-patch when {@link #backgroundTexture} is set,
     * otherwise falls back to solid colour + border.</p>
     */
    protected void renderBackground() {
        if (backgroundTexture != null) {
            // Nine-patch with 4px border, assuming 160x32 default texture
            TextureStretching.drawNinePatch(backgroundTexture, 0, 0, width, height,
                    4, 4, 4, 4, DEFAULT_WIDTH, SLOT_HEIGHT);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            drawRect(0, 0, width, height, BACKGROUND_COLOR);
            drawBorder();

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }

    /**
     * Draw the border lines.
     * <p>绘制边框。</p>
     */
    protected void drawBorder() {
        int borderColor = BORDER_COLOR;
        drawRect(0, 0, width, 1, borderColor);                     // top
        drawRect(0, height - 1, width, height, borderColor);       // bottom
        drawRect(0, 0, 1, height, borderColor);                    // left
        drawRect(width - 1, 0, width, height, borderColor);        // right
    }

    /**
     * Draw a rectangle using the Tessellator.
     * <p>使用 Tessellator 绘制矩形。</p>
     */
    protected void drawRect(int left, int top, int right, int bottom, int color) {
        GuiDrawing.drawRect(left, top, right, bottom, color);
    }

    // ──── Abstract content rendering ────

    /**
     * Render the Toast content (implemented by subclasses).
     * <p>渲染 Toast 内容(由子类实现)。</p>
     *
     * @param fontRenderer      font renderer / 字体渲染器
     * @param fullyVisibleForMs fully visible duration / 完全可见的持续时间
     */
    protected abstract void renderContent(FontRenderer fontRenderer, long fullyVisibleForMs);
}
