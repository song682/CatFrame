package decok.dfcdvadstf.catframe.ui.components.toast;

import decok.dfcdvadstf.catframe.ui.GuiDrawing;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.components.AbstractComponent;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * Base Toast implementation — extends {@link AbstractComponent} for common background
 * rendering and basic functionality.
 * </p>
 */
public abstract class BaseToast extends AbstractComponent implements Toast {

    /** Default display time (ms) */
    protected static final long DEFAULT_DISPLAY_TIME = 5000L;

    /** Background colour */
    protected static final int BACKGROUND_COLOR = 0xCC000000;

    /** Border colour */
    protected static final int BORDER_COLOR = 0xFF555555;

    /** Default toast background texture */
    protected static final ResourceLocation DEFAULT_TOAST_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/sprites/toast/default.png");

    /**
     * Background texture for this toast. Null = use solid colour fallback.
     */
    protected ResourceLocation backgroundTexture = DEFAULT_TOAST_TEXTURE;

    /** Minecraft instance */
    protected final Minecraft mc = Minecraft.getMinecraft();

    /** Current wanted visibility */
    protected Visibility wantedVisibility = Visibility.HIDE;

    /** Slide-in sound, null = silent */
    protected ResourceLocation showSound;

    /** Slide-out sound, null = silent */
    protected ResourceLocation hideSound;

    /** Slide-in corner */
    protected ToastCorner corner = ToastCorner.TOP_RIGHT;

    /** Last fully-visible duration from update() */
    private long lastFullyVisibleForMs;

    // ──── Texture API ────

    /**
     * Set the background texture for this Toast.
     *
     * @param texture background texture ResourceLocation, or null to use solid colour fallback
     * @return this instance for chaining
     */
    public BaseToast setBackgroundTexture(ResourceLocation texture) {
        this.backgroundTexture = texture;
        return this;
    }

    /**
     * Get the current background texture.
     */
    public ResourceLocation getBackgroundTexture() {
        return backgroundTexture;
    }

    // ──── Sound API ────

    /**
     * Set the sound played when this Toast slides in.
     *
     * @param sound sound ResourceLocation, or null for silence
     * @return this instance for chaining
     */
    public BaseToast setShowSound(ResourceLocation sound) {
        this.showSound = sound;
        return this;
    }

    /**
     * Set the sound played when this Toast slides out.
     *
     * @param sound sound ResourceLocation, or null for silence
     * @return this instance for chaining
     */
    public BaseToast setHideSound(ResourceLocation sound) {
        this.hideSound = sound;
        return this;
    }

    @Override
    public ResourceLocation getShowSound() {
        return showSound;
    }

    @Override
    public ResourceLocation getHideSound() {
        return hideSound;
    }

    // ──── Corner API ────

    /**
     * Set the screen corner this Toast slides in from and stacks at.
     *
     * @param corner the corner; null falls back to {@link ToastCorner#TOP_RIGHT}
     * @return this instance for chaining
     */
    public BaseToast setCorner(ToastCorner corner) {
        this.corner = corner != null ? corner : ToastCorner.TOP_RIGHT;
        return this;
    }

    @Override
    public ToastCorner getCorner() {
        return corner;
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

    /**
     * Draws the Toast background and content — the visibility check is already
     * handled by {@link #extractRenderState}.
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground();

        renderContent(mc.fontRenderer, lastFullyVisibleForMs);
    }

    // ──── Background rendering ────

    /**
     * Render the Toast background.
     * <p>Uses texture-based nine-patch when {@link #backgroundTexture} is set,
     * otherwise falls back to solid colour + border.</p>
     */
    protected void renderBackground() {
        if (backgroundTexture != null) {
            // Auto-load mcmeta, fallback to 4px border at 160x32
            TextureStretching.drawAutoNinePatch(backgroundTexture, 0, 0, width, height,
                    DEFAULT_WIDTH, SLOT_HEIGHT, 4);
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
     */
    protected void drawRect(int left, int top, int right, int bottom, int color) {
        GuiDrawing.drawRect(left, top, right, bottom, color);
    }

    // ──── Abstract content rendering ────

    /**
     * Render the Toast content (implemented by subclasses).
     *
     * @param fontRenderer      font renderer
     * @param fullyVisibleForMs fully visible duration
     */
    protected abstract void renderContent(FontRenderer fontRenderer, long fullyVisibleForMs);
}
