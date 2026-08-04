package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.util.ResourceLocation;

/**
 * <p>
 * 图片显示组件 —— 对标高版本 Minecraft 的 {@code ImageWidget}（texture 变体）。<br>
 * 继承 {@link AbstractComponent}，将整张纹理拉伸绘制到组件区域内；纯展示用途，
 * 不响应任何交互（不可点击、不参与焦点），支持运行时更新纹理。
 * </p>
 * <p>
 * Image display widget — counterpart of the high-version Minecraft
 * {@code ImageWidget} (texture variant). Extends {@link AbstractComponent};
 * stretches the whole texture over the widget area. Display-only: it does not
 * respond to any interaction (not clickable, no focus); supports runtime
 * texture updates.
 * </p>
 * <p>
 * 纹理按像素完美整数倍放大：width/height 必须是纹理原始尺寸（texW/texH）的
 * 整数倍，否则构造时抛出 {@link IllegalArgumentException}。<br>
 * The texture is upscaled pixel-perfectly: width/height must be integer
 * multiples of the texture's original size (texW/texH), otherwise an
 * {@link IllegalArgumentException} is thrown at construction.
 * </p>
 */
public class ImageWidget extends AbstractComponent {

    private ResourceLocation texture;
    private final int texW;
    private final int texH;

    private ImageWidget(int x, int y, int width, int height, ResourceLocation texture, int texW, int texH) {
        super(x, y, width, height);
        if (texW <= 0 || texH <= 0) {
            throw new IllegalArgumentException(
                    "Original texture size must be positive: " + texW + "x" + texH);
        }
        if (width % texW != 0 || height % texH != 0) {
            throw new IllegalArgumentException("Widget size " + width + "x" + height
                    + " is not an integer multiple of texture size " + texW + "x" + texH);
        }
        this.texture = texture;
        this.texW = texW;
        this.texH = texH;
    }

    /**
     * Creates an image widget with the given size (position defaults to 0,0).
     * <p>以给定尺寸创建图片组件（位置默认为 0,0）。</p>
     */
    public static ImageWidget texture(int width, int height, ResourceLocation texture, int texW, int texH) {
        return new ImageWidget(0, 0, width, height, texture, texW, texH);
    }

    /**
     * Creates an image widget at the given position and size.
     * <p>以给定位置和尺寸创建图片组件。</p>
     */
    public static ImageWidget texture(int x, int y, int width, int height,
                                      ResourceLocation texture, int texW, int texH) {
        return new ImageWidget(x, y, width, height, texture, texW, texH);
    }

    /**
     * Updates the displayed texture at runtime.
     * <p>运行时更新显示的纹理。</p>
     */
    public void updateResource(ResourceLocation texture) {
        this.texture = texture;
    }

    /**
     * Returns the currently displayed texture.
     * <p>返回当前显示的纹理。</p>
     */
    public ResourceLocation getTexture() {
        return texture;
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (texture == null) return;
        TextureStretching.drawStatic(texture, x, y, width, height, texW, texH, alpha);
    }
}
