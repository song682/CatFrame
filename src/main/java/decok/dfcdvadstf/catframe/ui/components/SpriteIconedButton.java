package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * <p>
 * Sprite icon button — counterpart of the high-version Minecraft
 * {@code SpriteIconButton}. Draws a state-aware sprite icon on top of the
 * {@link Button} background (three states): {@link CenteredIcon} centres the
 * icon inside the button, {@link TextAndIcon} puts the text on the left and
 * the icon on the right. The icon texture is picked by {@link WidgetSprites}
 * from the enabled / hovered states.
 * </p>
 */
public abstract class SpriteIconedButton extends Button {

    /** State sprites */
    protected final WidgetSprites sprite;
    /** Icon display width */
    protected final int spriteWidth;
    /** Icon display height */
    protected final int spriteHeight;

    /**
     * <p>
     * Creates a sprite icon button (for the {@link Builder} and subclasses).
     * </p>
     *
     * @param tooltip optional tooltip text, null disables the tooltip
     */
    protected SpriteIconedButton(int x, int y, int width, int height, Text message,
            int spriteWidth, int spriteHeight, WidgetSprites sprite, OnPress onPress,
            @Nullable Text tooltip) {
        super(x, y, width, height, message, onPress);
        if (spriteWidth <= 0 || spriteHeight <= 0) {
            throw new IllegalArgumentException(
                    "Sprite size must be positive: " + spriteWidth + "x" + spriteHeight);
        }
        this.sprite = sprite;
        this.spriteWidth = spriteWidth;
        this.spriteHeight = spriteHeight;
        if (tooltip != null) {
            this.setTooltip(Tooltip.create(tooltip.getString()));
        }
    }

    /**
     * <p>
     * Draws the sprite icon for the current widget state.
     * </p>
     * <p>
     * Mirrors the modern Minecraft {@code SpriteIconButton#extractSprite}; this
     * implementation draws the whole texture via
     * {@link TextureStretching#drawStatic}, so spriteWidth/spriteHeight must be
     * integer multiples of the texture's original size. The graphics parameter
     * is kept to align with the modern API shape; current drawing does not rely
     * on it.
     * </p>
     */
    protected void extractSprite(GuiGraphicsExtractor graphics, int x, int y) {
        ResourceLocation texture = this.sprite.get(this.active, this.isHovered || this.focused);
        TextureStretching.drawStatic(texture, x, y, this.spriteWidth, this.spriteHeight,
                this.spriteWidth, this.spriteHeight, this.alpha);
    }

    /**
     * Creates the button builder — mirrors the modern Minecraft
     * {@code SpriteIconButton#builder}.
     *
     * @param iconOnly true builds {@link CenteredIcon} (icon only),
     *                 false builds {@link TextAndIcon}
     */
    public static Builder builder(Text message, OnPress onPress, boolean iconOnly) {
        return new Builder(message, onPress, iconOnly);
    }

    // ──── Builder ────

    /**
     * Builder for constructing a {@link SpriteIconedButton}.
     */
    public static class Builder {
        private final Text message;
        private final OnPress onPress;
        private final boolean iconOnly;
        private int x;
        private int y;
        private int width = DEFAULT_WIDTH;
        private int height = DEFAULT_HEIGHT;
        @Nullable
        private WidgetSprites sprite;
        private int spriteWidth;
        private int spriteHeight;
        @Nullable
        private Text tooltip;

        public Builder(Text message, OnPress onPress, boolean iconOnly) {
            this.message = message;
            this.onPress = onPress;
            this.iconOnly = iconOnly;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * <p>
         * Uses a single-texture sprite shared by all states.
         * </p>
         */
        public Builder sprite(ResourceLocation sprite, int spriteWidth, int spriteHeight) {
            this.sprite = new WidgetSprites(sprite);
            this.spriteWidth = spriteWidth;
            this.spriteHeight = spriteHeight;
            return this;
        }

        /**
         * <p>
         * Uses a state-aware sprite collection.
         * </p>
         */
        public Builder sprite(WidgetSprites sprite, int spriteWidth, int spriteHeight) {
            this.sprite = sprite;
            this.spriteWidth = spriteWidth;
            this.spriteHeight = spriteHeight;
            return this;
        }

        /**
         * <p>
         * Uses the button message as the tooltip — mirrors the modern Minecraft
         * {@code withTootip} with the spelling corrected.
         * </p>
         */
        public Builder withTooltip() {
            this.tooltip = this.message;
            return this;
        }

        /**
         * <p>
         * Uses a custom text as the tooltip.
         * </p>
         */
        public Builder withTooltip(Text tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        /**
         * <p>
         * Builds the button; throws {@link IllegalStateException} when no
         * sprite was set.
         * </p>
         */
        public SpriteIconedButton build() {
            if (this.sprite == null) {
                throw new IllegalStateException("Sprite not set");
            }
            return this.iconOnly
                    ? new CenteredIcon(this.x, this.y, this.width, this.height, this.message,
                            this.spriteWidth, this.spriteHeight, this.sprite, this.onPress, this.tooltip)
                    : new TextAndIcon(this.x, this.y, this.width, this.height, this.message,
                            this.spriteWidth, this.spriteHeight, this.sprite, this.onPress, this.tooltip);
        }
    }

    // ──── Variant: centred icon ────

    /**
     * <p>
     * Icon centred inside the button — counterpart of
     * {@code SpriteIconButton.CenteredIcon}. Renders the button background,
     * then centres the icon horizontally / vertically inside the button.
     * </p>
     */
    public static class CenteredIcon extends SpriteIconedButton {
        protected CenteredIcon(int x, int y, int width, int height, Text message,
                int spriteWidth, int spriteHeight, WidgetSprites sprite, OnPress onPress,
                @Nullable Text tooltip) {
            super(x, y, width, height, message, spriteWidth, spriteHeight, sprite, onPress, tooltip);
        }

        @Override
        protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            renderBackground(graphics, mouseX, mouseY, partialTicks);
            int ix = x + (width - spriteWidth) / 2;
            int iy = y + (height - spriteHeight) / 2;
            extractSprite(graphics, ix, iy);
        }
    }

    // ──── Variant: text + icon ────

    /**
     * <p>
     * Text on the left, icon on the right — counterpart of
     * {@code SpriteIconButton.TextAndIcon}. Renders the button background,
     * centres the text within the area left of the icon, and puts the icon
     * vertically centred on the right.
     * </p>
     */
    public static class TextAndIcon extends SpriteIconedButton {
        protected TextAndIcon(int x, int y, int width, int height, Text message,
                int spriteWidth, int spriteHeight, WidgetSprites sprite, OnPress onPress,
                @Nullable Text tooltip) {
            super(x, y, width, height, message, spriteWidth, spriteHeight, sprite, onPress, tooltip);
        }

        @Override
        protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            renderBackground(graphics, mouseX, mouseY, partialTicks);

            // Text layout: centred on the button centre within [x+2,
            // x+width-spriteWidth-4].
            // For short text the centre falls inside the area (matching the high version's
            // static acceptScrolling); overflowing text degrades to button-centre alignment
            // (the high version scrolls here; scrolling is omitted in 1.7.10).
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            Text message = getMessage();
            String text = message != null ? message.getString() : "";
            int centerX = x + width / 2;
            int textW = font.getStringWidth(text);
            int textX = centerX - textW / 2;

            int textColor;
            if (!active) {
                textColor = TEXT_COLOR_DISABLED;
            } else if (isHovered) {
                textColor = TEXT_COLOR_HOVER;
            } else {
                textColor = TEXT_COLOR_ENABLED;
            }
            int textY = y + (height - font.FONT_HEIGHT) / 2;
            font.drawStringWithShadow(text, textX, textY, textColor);

            // Icon vertically centred on the right side of the button
            int ix = x + width - spriteWidth - 2;
            int iy = y + (height - spriteHeight) / 2;
            extractSprite(graphics, ix, iy);
        }
    }
}
