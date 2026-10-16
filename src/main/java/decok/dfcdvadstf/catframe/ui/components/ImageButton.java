package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;

/**
 * <p>
 * Image button component — counterpart of the high-version Minecraft
 * {@code ImageButton}. Extends {@link Button} with full button interaction
 * (click callback, hover detection, enabled/disabled states); rendering picks
 * the per-state texture via {@link WidgetSprites} (normal / highlighted /
 * disabled / highlighted-disabled) without the button background or text,
 * suited for icon buttons.
 * </p>
 * <p>
 * The texture is stretched over the whole button area (any size, equivalent
 * to the high version's arbitrary-size {@code blitSprite}); for pixel-perfect
 * integer scaling use {@link SpriteIconedButton} instead.
 * </p>
 */
public class ImageButton extends Button {

    /** State sprites */
    protected final WidgetSprites sprites;

    /**
     * Creates an image button with an empty message.
     */
    public ImageButton(int x, int y, int width, int height, WidgetSprites sprites, OnPress onPress) {
        this(x, y, width, height, sprites, onPress, Text.literal(""));
    }

    /**
     * Creates an image button at position 0,0 with the given message.
     */
    public ImageButton(int width, int height, WidgetSprites sprites, OnPress onPress, Text message) {
        this(0, 0, width, height, sprites, onPress, message);
    }

    /**
     * Full constructor.
     */
    public ImageButton(int x, int y, int width, int height, WidgetSprites sprites, OnPress onPress, Text message) {
        super(x, y, width, height, message, onPress);
        this.sprites = Objects.requireNonNull(sprites, "sprites");
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // Draw only the per-state texture — no super call, so the Button
        // background/text are skipped.
        ResourceLocation sprite = this.sprites.get(this.active, this.isHovered || this.focused);
        TextureStretching.drawStatic(sprite, x, y, width, height, width, height, alpha);
    }
}
