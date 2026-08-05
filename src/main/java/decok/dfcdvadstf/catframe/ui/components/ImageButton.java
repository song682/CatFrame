package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;

/**
 * <p>
 * 图片按钮组件 —— 对标高版本 Minecraft 的 {@code ImageButton}。<br>
 * 继承 {@link Button}，保留完整的按钮交互（点击回调、悬停检测、启用/禁用状态）；
 * 渲染时仅按当前状态经 {@link WidgetSprites} 选择并绘制对应纹理
 * （正常 / 高亮 / 禁用 / 高亮禁用），不绘制按钮背景与文本，适用于图标类按钮。
 * </p>
 * <p>
 * Image button component — counterpart of the high-version Minecraft
 * {@code ImageButton}. Extends {@link Button} with full button interaction
 * (click callback, hover detection, enabled/disabled states); rendering picks
 * the per-state texture via {@link WidgetSprites} (normal / highlighted /
 * disabled / highlighted-disabled) without the button background or text,
 * suited for icon buttons.
 * </p>
 * <p>
 * 纹理拉伸到整个按钮区域（任意尺寸，等价高版本 {@code blitSprite} 的任意缩放）；
 * 需要像素完美整数倍放大的场景请使用 {@link SpriteIconedButton}。<br>
 * The texture is stretched over the whole button area (any size, equivalent
 * to the high version's arbitrary-size {@code blitSprite}); for pixel-perfect
 * integer scaling use {@link SpriteIconedButton} instead.
 * </p>
 */
public class ImageButton extends Button {

    /** 状态精灵集合 / state sprites */
    protected final WidgetSprites sprites;

    /**
     * Creates an image button with an empty message.
     * <p>
     * 创建图片按钮（空消息）。
     * </p>
     */
    public ImageButton(int x, int y, int width, int height, WidgetSprites sprites, OnPress onPress) {
        this(x, y, width, height, sprites, onPress, Text.literal(""));
    }

    /**
     * Creates an image button at position 0,0 with the given message.
     * <p>
     * 创建图片按钮（位置默认为 0,0）。
     * </p>
     */
    public ImageButton(int width, int height, WidgetSprites sprites, OnPress onPress, Text message) {
        this(0, 0, width, height, sprites, onPress, message);
    }

    /**
     * Full constructor.
     * <p>
     * 完整构造器。
     * </p>
     */
    public ImageButton(int x, int y, int width, int height, WidgetSprites sprites, OnPress onPress, Text message) {
        super(x, y, width, height, message, onPress);
        this.sprites = Objects.requireNonNull(sprites, "sprites");
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // 仅绘制状态纹理 —— 不调用 super，避免 Button 的背景与文本渲染。
        // Draw only the per-state texture — no super call, so the Button
        // background/text are skipped.
        ResourceLocation sprite = this.sprites.get(this.active, this.isHovered || this.focused);
        TextureStretching.drawStatic(sprite, x, y, width, height, width, height, alpha);
    }
}
