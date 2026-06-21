package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * 按钮抽象基类 —— 提供悬停检测、按下状态和音效播放等通用按钮功能。<br>
 * 对标高版本 Minecraft 的 {@code AbstractButton}。
 * </p>
 * <p>
 * Abstract button base — provides hover detection, press state, and sound playback.<br>
 * Counterpart of the high-version Minecraft {@code AbstractButton}.
 * </p>
 */
public abstract class AbstractButton extends AbstractComponent {

    protected boolean wasHovered;
    protected long lastClickTime;

    /** Default button text colour / 默认按钮文本颜色 */
    protected static final int TEXT_COLOR_ENABLED = 0xE0E0E0;
    protected static final int TEXT_COLOR_DISABLED = 0xA0A0A0;
    protected static final int TEXT_COLOR_HOVER = 0xFFFFA0;

    /** Vanilla button widgets texture / 原版按钮部件纹理 */
    protected static final ResourceLocation WIDGETS_TEXTURE = new ResourceLocation("textures/gui/widgets.png");

    /** CatFrame custom button textures / CatFrame 自定义按钮纹理 */
    protected static final ResourceLocation BUTTON_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/widgets/button.png");
    protected static final ResourceLocation BUTTON_HIGHLIGHTED_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/widgets/button_highlighted.png");
    protected static final ResourceLocation BUTTON_DISABLED_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/widgets/button_disabled.png");

    /** Button default size from mcmeta / 按钮默认尺寸 */
    protected static final int BUTTON_DEFAULT_W = 200;
    protected static final int BUTTON_DEFAULT_H = 20;
    protected static final int BUTTON_EDGE = 2;

    protected final Gui vanillaGui = new Gui();

    /**
     * If true, use vanilla widgets texture instead of CatFrame custom textures.
     * <p>为 true 时使用原版部件纹理而非 CatFrame 自定义纹理。</p>
     */
    protected boolean useVanillaTexture = false;

    public AbstractButton() {
    }

    public AbstractButton(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    /**
     * Called when the button is pressed.
     * <p>按钮被按下时调用。</p>
     */
    public abstract void onPress();

    /**
     * Plays the button click sound.
     * <p>播放按钮点击音效。</p>
     */
    protected void playPressSound() {
        Minecraft.getMinecraft().getSoundHandler().playSound(
            PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    /**
     * Draw the button background.
     * <p>绘制按钮背景。</p>
     * <p>If {@link #useVanillaTexture} is true, uses vanilla widgets texture.
     * Otherwise uses CatFrame custom textures with three-patch stretching.</p>
     */
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
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

        // three_patch: left edge=2, right edge=2, middle tiled, texture 200x20
        TextureStretching.drawFixedEndRepeat(tex, x, y, width, height,
                BUTTON_EDGE, BUTTON_EDGE, BUTTON_DEFAULT_W - 2 * BUTTON_EDGE,
                BUTTON_DEFAULT_W, BUTTON_DEFAULT_H);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        updateHoverState(mouseX, mouseY);
        renderBackground(mouseX, mouseY, partialTicks);
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
     * <p>辅助：绘制填充矩形。</p>
     */
    protected static void drawRect(int left, int top, int right, int bottom, int color) {
        GuiDrawing.drawRect(left, top, right, bottom, color);
    }
}
