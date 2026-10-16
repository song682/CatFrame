package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiDrawing;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * Abstract button base — provides hover detection, press state, and sound playback.<br>
 * Counterpart of the high-version Minecraft {@code AbstractButton}.
 * </p>
 * <p>
 * Extends {@link AbstractComponent.WithInactiveMessage}, so the button's message and its
 * inactive graying are managed by the base class — mirrors 26.1.2
 * {@code AbstractButton extends AbstractWidget.WithInactiveMessage}.
 * </p>
 */
public abstract class AbstractButton extends AbstractComponent.WithInactiveMessage {

    protected boolean wasHovered;
    protected long lastClickTime;

    /** Default button text colour */
    protected static final int TEXT_COLOR_ENABLED = 0xE0E0E0;
    protected static final int TEXT_COLOR_DISABLED = 0xA0A0A0;
    protected static final int TEXT_COLOR_HOVER = 0xFFFFA0;

    /** Vanilla button widgets texture */
    protected static final ResourceLocation WIDGETS_TEXTURE = new ResourceLocation("textures/gui/widgets.png");

    /** CatFrame custom button textures */
    protected static final ResourceLocation BUTTON_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/sprites/widgets/button.png");
    protected static final ResourceLocation BUTTON_HIGHLIGHTED_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/sprites/widgets/button_highlighted.png");
    protected static final ResourceLocation BUTTON_DISABLED_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/sprites/widgets/button_disabled.png");

    /** Button default size from mcmeta */
    protected static final int BUTTON_DEFAULT_W = 200;
    protected static final int BUTTON_DEFAULT_H = 20;
    protected static final int BUTTON_EDGE = 2;

    protected final Gui vanillaGui = new Gui();

    /**
     * If true, use vanilla widgets texture instead of CatFrame custom textures.
     */
    protected boolean useVanillaTexture = false;

    public AbstractButton() {
    }

    public AbstractButton(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public AbstractButton(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    /**
     * Called when the button is pressed.
     */
    public abstract void onPress();

    /**
     * Plays the button click sound.
     */
    protected void playPressSound() {
        Minecraft.getMinecraft().getSoundHandler().playSound(
            PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    /**
     * Draw the button background.
     * <p>If {@link #useVanillaTexture} is true, uses vanilla widgets texture.
     * Otherwise uses CatFrame custom textures with three-patch stretching.</p>
     */
    protected void renderBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (useVanillaTexture) {
            renderVanillaBackground();
        } else {
            renderCustomBackground();
        }
    }

    /**
     * Render using vanilla widgets texture.
     */
    private void renderVanillaBackground() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(WIDGETS_TEXTURE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int hoverState = !active ? 0 : (isHovered ? 2 : 1);
        int textureV = 46 + hoverState * 20;

        // Left half
        vanillaGui.drawTexturedModalRect(x, y, 0, textureV, width / 2, height);
        // Right half
        vanillaGui.drawTexturedModalRect(x + width / 2, y, 200 - width / 2, textureV, width / 2, height);
    }

    /**
     * Render using CatFrame custom textures with three-patch stretching.
     */
    private void renderCustomBackground() {
        ResourceLocation tex;
        if (!active) {
            tex = BUTTON_DISABLED_TEXTURE;
        } else if (isHovered) {
            tex = BUTTON_HIGHLIGHTED_TEXTURE;
        } else {
            tex = BUTTON_TEXTURE;
        }

        // Auto-load mcmeta, fallback to three_patch with edge=2, 200x20
        TextureStretching.drawAutoThreePatch(tex, x, y, width, height,
                BUTTON_DEFAULT_W, BUTTON_DEFAULT_H, BUTTON_EDGE);
    }

    /**
     * New render entry — driven uniformly by {@link #extractRenderState}; visibility
     * and hover state are already handled, so only the background is drawn here.
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!active || !visible || mouseButton != 0) return;
        if (isMouseOver(mouseX, mouseY)) {
            lastClickTime = System.currentTimeMillis();
            playPressSound();
            onPress();
        }
    }

    /**
     * Helper: draw a filled rectangle.
     */
    protected static void drawRect(int left, int top, int right, int bottom, int color) {
        GuiDrawing.drawRect(left, top, right, bottom, color);
    }
}
