package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

/**
 * <p>
 * Abstract slider button — counterpart of the 26.1.2
 * {@code AbstractSliderButton}. Extends
 * {@link AbstractComponent.WithInactiveMessage}; subclasses implement the
 * {@link #updateMessage()} and {@link #applyValue()} hooks, and the normalized
 * value ranges over {@code [0.0, 1.0]}.
 * </p>
 * <p>
 * Porting notes:
 * <ul>
 * <li>Vanilla narration and {@code CursorTypes} cursor requests have no
 * 1.7.10 counterpart and are not ported.</li>
 * <li>Vanilla decides the focus source (mouse / Tab) via {@code InputType};
 * 1.7.10 lacks that mechanism, so any user-driven focus is treated as
 * value-changeable (see {@link #setFocused(boolean)}), and Enter still
 * toggles.</li>
 * <li>The click sound is played on release, mirroring vanilla
 * {@code onRelease}.</li>
 * </ul>
 * </p>
 */
public abstract class AbstractSilderButton extends AbstractComponent.WithInactiveMessage {

    private static final ResourceLocation SLIDER_SPRITE = new ResourceLocation("catframe", "textures/gui/sprites/widgets/silder.png");
    private static final ResourceLocation HIGHLIGHTED_SLIDER_SPRITE = new ResourceLocation("catframe", "textures/gui/sprites/widgets/silder_highlighted.png");
    private static final ResourceLocation SLIDER_HANDLE_SPRITE = new ResourceLocation("catframe", "textures/gui/sprites/widgets/silder_handle.png");
    private static final ResourceLocation HIGHLIGHTED_SLIDER_HANDLE_SPRITE = new ResourceLocation("catframe", "textures/gui/sprites/widgets/silder_handle_highlighted.png");
    protected static final int TEXT_MARGIN = 2;
    public static final int DEFAULT_HEIGHT = 20;
    protected static final int HANDLE_WIDTH = 8;
    private static final int HANDLE_HALF_WIDTH = 4;

    /** Fallback sizes used when the .mcmeta sidecar is missing (must match its definition). */
    private static final int SLIDER_DEFAULT_W = 200;
    private static final int SLIDER_DEFAULT_H = 20;
    private static final int SLIDER_EDGE = 2;
    private static final int HANDLE_DEFAULT_W = 8;
    private static final int HANDLE_DEFAULT_H = 20;

    /** Message colour mirrors the modern-widget default foreground. */
    private static final int TEXT_COLOR_ENABLED = 0xE0E0E0;
    private static final int TEXT_COLOR_DISABLED = 0xA0A0A0;

    protected double value;
    protected boolean canChangeValue;
    private boolean dragging;

    protected AbstractSilderButton(final int x, final int y, final int width, final int height, final Text message, final double initalValue) {
        super(x, y, width, height, message);
        this.value = initalValue;
    }

    private ResourceLocation getSprite() {
        return this.isActive() && this.isFocused() && !this.canChangeValue ? HIGHLIGHTED_SLIDER_SPRITE : SLIDER_SPRITE;
    }

    private ResourceLocation getHandleSprite() {
        return !this.isActive() || !this.isHovered && !this.canChangeValue ? SLIDER_HANDLE_SPRITE : HIGHLIGHTED_SLIDER_HANDLE_SPRITE;
    }

    /**
     * Render the track, the handle and the scrolling message.
     * <p>
     * Counterpart of 26.1.2 {@code AbstractSliderButton#extractWidgetRenderState}:
     * the two blitSprite calls become mcmeta-driven nine-patch stretching, and
     * {@code extractScrollingStringOverContents} becomes centring plus a clamp
     * within the {@code TEXT_MARGIN} insets.
     * </p>
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // Track — full widget width
        TextureStretching.drawAutoNinePatch(this.getSprite(), this.x, this.y, this.width, this.height,
                SLIDER_DEFAULT_W, SLIDER_DEFAULT_H, SLIDER_EDGE);
        // Handle — positioned by the normalized value
        int handleX = this.x + (int) (this.value * (this.width - HANDLE_WIDTH));
        TextureStretching.drawAutoNinePatch(this.getHandleSprite(), handleX, this.y, HANDLE_WIDTH, this.height,
                HANDLE_DEFAULT_W, HANDLE_DEFAULT_H, SLIDER_EDGE);

        // Scrolling message over contents
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String text = getMessage() != null ? getMessage().getString() : "";
        int available = this.width - TEXT_MARGIN * 2;
        int textWidth = font.getStringWidth(text);
        int offset = (this.width - textWidth) / 2;
        if (textWidth > available) {
            // Too wide: scroll within the margins instead of overflowing.
            offset = MathHelper.clamp_int(offset, available - textWidth, 0);
        }
        int textY = this.y + (this.height - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(text, this.x + TEXT_MARGIN + offset, textY,
                this.active ? TEXT_COLOR_ENABLED : TEXT_COLOR_DISABLED);
    }

    /**
     * Begin dragging and jump to the clicked position.
     * <p>
     * Counterpart of 26.1.2 {@code onClick}. On 1.7.10 a click also triggers
     * focus ({@code dispatchMouseClicked} calls {@code setFocused} right
     * after), so {@code canChangeValue} is not enabled explicitly here.
     * </p>
     */
    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!this.active || mouseButton != 0) {
            return;
        }
        this.dragging = true;
        this.setValueFromMouse(mouseX);
    }

    /**
     * Track-following drag while the button is held.
     * <p>
     * Updates the value from the mouse while held — counterpart of 26.1.2
     * {@code onDrag}.
     * </p>
     */
    @Override
    public void mouseDrag(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        if (this.dragging) {
            this.setValueFromMouse(mouseX);
        }
    }

    /**
     * Stop dragging and play the click sound on release.
     * <p>
     * Counterpart of 26.1.2 {@code onRelease}: vanilla suppresses the sound in
     * {@code onClick} and only plays it once the button is released.
     * </p>
     */
    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        this.dragging = false;
        if (mouseButton == 0) {
            Minecraft.getMinecraft().getSoundHandler().playSound(
                    PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
        }
    }

    /**
     * Resolve whether the slider may change its value while focused.
     * <p>
     * Counterpart of 26.1.2 {@code setFocused}. Vanilla gates value-changing on
     * the focus source ({@code InputType}: MOUSE / KEYBOARD_TAB); 1.7.10 has no
     * such mechanism, so any focus is treated as user-driven and
     * value-changeable, and Enter still toggles the state.
     * </p>
     */
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        this.canChangeValue = focused;
    }

    /**
     * Keyboard value stepping.
     * <p>
     * Counterpart of 26.1.2 {@code keyPressed}: Enter toggles
     * {@code canChangeValue}; left/right arrows step by the track width.
     * {@code isSelection()} is approximated by {@code KEY_RETURN} on 1.7.10.
     * </p>
     */
    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == Keyboard.KEY_RETURN) {
            this.canChangeValue = !this.canChangeValue;
            return true;
        }
        if (this.canChangeValue) {
            boolean left = keyCode == Keyboard.KEY_LEFT;
            boolean right = keyCode == Keyboard.KEY_RIGHT;
            if (left || right) {
                double direction = left ? -1.0D : 1.0D;
                this.setValue(this.value + direction / (this.width - HANDLE_WIDTH));
                return true;
            }
        }
        return false;
    }

    /**
     * Map an absolute mouse X to the normalized value — counterpart of
     * {@code setValueFromMouse}.
     */
    private void setValueFromMouse(int mouseX) {
        this.setValue((mouseX - (this.getX() + HANDLE_HALF_WIDTH)) / (double) (this.width - HANDLE_WIDTH));
    }

    /**
     * Set the normalized value, clamped to {@code [0, 1]}.
     * <p>
     * Clamps the value to {@code [0, 1]}; {@link #applyValue()} fires only when
     * the value actually changes, while the message is always refreshed —
     * counterpart of 26.1.2 {@code setValue}.
     * </p>
     */
    protected void setValue(final double newValue) {
        double oldValue = this.value;
        this.value = MathHelper.clamp_double(newValue, 0.0D, 1.0D);
        if (oldValue != this.value) {
            this.applyValue();
        }
        this.updateMessage();
    }

    /**
     * @return the current normalized value
     */
    public double getVal() {
        return this.value;
    }

    /** Rebuild the displayed message from the current value. */
    protected abstract void updateMessage();

    /** Push the current value to the backing setting. */
    protected abstract void applyValue();
}
