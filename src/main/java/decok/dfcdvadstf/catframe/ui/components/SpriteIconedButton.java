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
 * 精灵图标按钮 —— 对标高版本 Minecraft 的 {@code SpriteIconButton}。<br>
 * 在 {@link Button} 按钮背景（三态）之上叠加一张状态精灵图标：
 * {@link CenteredIcon} 将图标居中于按钮，{@link TextAndIcon} 将文本置于
 * 按钮左侧并将图标置于右侧。图标纹理由 {@link WidgetSprites} 按
 * 启用 / 悬停状态选择。
 * </p>
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

    /** 状态精灵集合 / state sprites */
    protected final WidgetSprites sprite;
    /** 图标显示宽度 / icon display width */
    protected final int spriteWidth;
    /** 图标显示高度 / icon display height */
    protected final int spriteHeight;

    /**
     * 构造精灵图标按钮（供 {@link Builder} 与子类调用）。
     * <p>
     * Creates a sprite icon button (for the {@link Builder} and subclasses).
     * </p>
     *
     * @param tooltip 可选的 tooltip 文本，为 null 时不显示 tooltip /
     *                optional tooltip text, null disables the tooltip
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
     * 按当前组件状态绘制精灵图标。
     * <p>
     * Draws the sprite icon for the current widget state.
     * </p>
     * <p>
     * 对标高版本 {@code SpriteIconButton#extractSprite}；本实现经
     * {@link TextureStretching#drawStatic} 直接绘制整图纹理，故
     * spriteWidth/spriteHeight 须为纹理原始尺寸的整数倍。graphics 参数
     * 预留以对齐高版本 API 形状，当前绘制不依赖它。
     * </p>
     */
    protected void extractSprite(GuiGraphicsExtractor graphics, int x, int y) {
        ResourceLocation texture = this.sprite.get(this.active, this.isHovered || this.focused);
        TextureStretching.drawStatic(texture, x, y, this.spriteWidth, this.spriteHeight,
                this.spriteWidth, this.spriteHeight, this.alpha);
    }

    /**
     * 创建按钮构建器 —— 对标高版本 {@code SpriteIconButton#builder}。
     *
     * @param iconOnly true 时构建仅图标的 {@link CenteredIcon}，false 时构建
     *                 {@link TextAndIcon} / true builds {@link CenteredIcon},
     *                 false builds {@link TextAndIcon}
     */
    public static Builder builder(Text message, OnPress onPress, boolean iconOnly) {
        return new Builder(message, onPress, iconOnly);
    }

    // ──── Builder ────

    /**
     * Builder for constructing a {@link SpriteIconedButton}.
     * <p>
     * 用于构建 {@link SpriteIconedButton} 的构建器。
     * </p>
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
         * 使用单一纹理精灵（所有状态共用）。
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
         * 使用状态精灵集合。
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
         * 使用按钮文本作为 tooltip（对标高版本 {@code withTootip}，修正拼写）。
         * <p>
         * Uses the button message as the tooltip.
         * </p>
         */
        public Builder withTooltip() {
            this.tooltip = this.message;
            return this;
        }

        /**
         * 使用自定义文本作为 tooltip。
         * <p>
         * Uses a custom text as the tooltip.
         * </p>
         */
        public Builder withTooltip(Text tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        /**
         * 构建按钮；未设置 sprite 时抛出 {@link IllegalStateException}。
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

    // ──── 变体：居中图标 ────

    /**
     * <p>
     * 图标居中于按钮的变体 —— 对标高版本 {@code SpriteIconButton.CenteredIcon}。<br>
     * 渲染按钮背景后，将图标水平 / 垂直居中于按钮内部。
     * </p>
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
            renderBackground(mouseX, mouseY, partialTicks);
            int ix = x + (width - spriteWidth) / 2;
            int iy = y + (height - spriteHeight) / 2;
            extractSprite(graphics, ix, iy);
        }
    }

    // ──── 变体：文本 + 图标 ────

    /**
     * <p>
     * 文本居左、图标居右的变体 —— 对标高版本 {@code SpriteIconButton.TextAndIcon}。<br>
     * 渲染按钮背景后，文本在可用区域（图标左侧）内水平居中，图标垂直居中于右侧。
     * </p>
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
            renderBackground(mouseX, mouseY, partialTicks);

            // 文本布局：居中于按钮中心，可用区域为 [x+2, x+width-spriteWidth-4]。短文本时
            // 中心即落在可用区域内（与高版本 acceptScrolling 的静态居中一致）；超宽文本
            // 退化为按按钮中心对齐（高版本在此滚动显示，1.7.10 省略滚动）。
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

            // 图标垂直居中于按钮右侧
            int ix = x + width - spriteWidth - 2;
            int iy = y + (height - spriteHeight) / 2;
            extractSprite(graphics, ix, iy);
        }
    }
}
