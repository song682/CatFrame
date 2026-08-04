package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.util.ResourceLocation;

/**
 * <p>
 * 图片按钮组件 —— 对标高版本 Minecraft 的 {@code ImageButton}。<br>
 * 继承 {@link Button}，保留完整的按钮交互（点击回调、悬停检测、启用/禁用状态）；
 * 渲染时仅按当前状态绘制对应纹理（正常 / 高亮 / 禁用），不绘制按钮背景与文本，
 * 适用于图标类按钮。
 * </p>
 * <p>
 * Image button component — counterpart of the high-version Minecraft
 * {@code ImageButton}. Extends {@link Button} with full button interaction
 * (click callback, hover detection, enabled/disabled states); rendering draws
 * only the per-state texture (normal / highlighted / disabled) without the
 * button background or text, suited for icon buttons.
 * </p>
 * <p>
 * 纹理按像素完美整数倍放大：width/height 必须是纹理原始尺寸（texW/texH）的
 * 整数倍，否则构造时抛出 {@link IllegalArgumentException}。<br>
 * The texture is upscaled pixel-perfectly: width/height must be integer
 * multiples of the texture's original size (texW/texH), otherwise an
 * {@link IllegalArgumentException} is thrown at construction.
 * </p>
 */
public class ImageButon extends Button {

    private final ResourceLocation texture;
    private final ResourceLocation highlightedTexture;
    private final ResourceLocation disabledTexture;
    private final int texW;
    private final int texH;

    /**
     * Creates an image button with a single texture for all states.
     * <p>
     * 使用单一纹理创建图片按钮（所有状态共用）。
     * </p>
     */
    public ImageButon(int x, int y, int width, int height,
            ResourceLocation texture, int texW, int texH, OnPress onPress) {
        this(x, y, width, height, texture, texture, texture, texW, texH, onPress);
    }

    /**
     * Creates an image button with separate normal and highlighted textures.
     * <p>
     * 使用普通 / 高亮双纹理创建图片按钮（禁用时回退普通纹理）。
     * </p>
     */
    public ImageButon(int x, int y, int width, int height,
            ResourceLocation texture, ResourceLocation highlightedTexture,
            int texW, int texH, OnPress onPress) {
        this(x, y, width, height, texture, highlightedTexture, texture, texW, texH, onPress);
    }

    /**
     * Creates an image button with full per-state textures.
     * <p>
     * 使用完整三态纹理创建图片按钮。
     * </p>
     */
    public ImageButon(int x, int y, int width, int height,
            ResourceLocation texture, ResourceLocation highlightedTexture,
            ResourceLocation disabledTexture, int texW, int texH, OnPress onPress) {
        super(x, y, width, height, Text.literal(""), onPress);
        if (texW <= 0 || texH <= 0) {
            throw new IllegalArgumentException(
                    "Original texture size must be positive: " + texW + "x" + texH);
        }
        if (width % texW != 0 || height % texH != 0) {
            throw new IllegalArgumentException("Widget size " + width + "x" + height
                    + " is not an integer multiple of texture size " + texW + "x" + texH);
        }
        this.texture = texture;
        this.highlightedTexture = highlightedTexture;
        this.disabledTexture = disabledTexture;
        this.texW = texW;
        this.texH = texH;
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // 仅绘制图片 —— 不调用 super，避免 Button 的背景与文本渲染。
        // Draw only the image — no super call, so the Button background/text are
        // skipped.
        ResourceLocation tex = !active ? disabledTexture : (isHovered ? highlightedTexture : texture);
        TextureStretching.drawStatic(tex, x, y, width, height, texW, texH, alpha);
    }
}
