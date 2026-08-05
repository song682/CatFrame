package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.util.ResourceLocation;

/**
 * <p>
 * 图片显示组件 —— 对标高版本 Minecraft 的 {@code ImageWidget}。<br>
 * 抽象基类：继承 {@link AbstractComponent}，纯展示用途，不响应任何交互
 * （不可点击、不参与焦点，{@link #isActive()} 恒为 false）；提供两个变体：
 * {@code texture}（整图纹理，按原始尺寸像素完美整数倍放大）与
 * {@code sprite}（整图任意拉伸），均支持运行时更新纹理。
 * </p>
 * <p>
 * Image display widget — counterpart of the high-version Minecraft
 * {@code ImageWidget}. Abstract base extending {@link AbstractComponent};
 * display-only: it does not respond to any interaction (not clickable, no
 * focus, {@link #isActive()} is always false). Two variants are provided:
 * {@code texture} (whole texture, pixel-perfect integer upscaling from the
 * original size) and {@code sprite} (whole texture stretched to any size);
 * both support runtime texture updates.
 * </p>
 */
public abstract class ImageWidget extends AbstractComponent {

    private ImageWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    /**
     * Creates a texture-stretched image widget (position defaults to 0,0).
     * <p>
     * 以给定尺寸创建整图纹理组件（位置默认为 0,0）。
     * </p>
     */
    public static ImageWidget texture(int width, int height, ResourceLocation texture, int texW, int texH) {
        return new Texture(0, 0, width, height, texture, texW, texH);
    }

    /**
     * Creates a texture-stretched image widget at the given position and size.
     * <p>
     * 以给定位置和尺寸创建整图纹理组件。
     * </p>
     */
    public static ImageWidget texture(int x, int y, int width, int height,
            ResourceLocation texture, int texW, int texH) {
        return new Texture(x, y, width, height, texture, texW, texH);
    }

    /**
     * Creates a sprite image widget stretched to the given size (position defaults
     * to 0,0).
     * <p>
     * 以给定尺寸创建精灵图片组件（位置默认为 0,0，任意拉伸）。
     * </p>
     */
    public static ImageWidget sprite(int width, int height, ResourceLocation sprite) {
        return new Sprite(0, 0, width, height, sprite);
    }

    /**
     * Creates a sprite image widget at the given position and size.
     * <p>
     * 以给定位置和尺寸创建精灵图片组件（任意拉伸）。
     * </p>
     */
    public static ImageWidget sprite(int x, int y, int width, int height, ResourceLocation sprite) {
        return new Sprite(x, y, width, height, sprite);
    }

    /**
     * Updates the displayed resource at runtime.
     * <p>
     * 运行时更新显示的纹理。
     * </p>
     */
    public abstract void updateResource(ResourceLocation texture);

    /**
     * 纯展示组件不响应交互 —— 对标高版本 {@code ImageWidget#isActive()} 覆写。
     * <p>
     * Display-only widget: never active — counterpart of the high-version
     * {@code ImageWidget#isActive()} override.
     * </p>
     */
    @Override
    public boolean isActive() {
        return false;
    }

    // ──── 变体：整图纹理（像素完美整数倍放大） ────

    /**
     * <p>
     * 整图纹理变体 —— 对标高版本 {@code ImageWidget.Texture}。<br>
     * 纹理按像素完美整数倍放大：width/height 必须是纹理原始尺寸（texW/texH）的
     * 整数倍，否则构造时抛出 {@link IllegalArgumentException}。
     * </p>
     * <p>
     * Whole-texture variant — counterpart of {@code ImageWidget.Texture}. The
     * texture is upscaled pixel-perfectly: width/height must be integer
     * multiples of the texture's original size (texW/texH), otherwise an
     * {@link IllegalArgumentException} is thrown at construction.
     * </p>
     */
    private static class Texture extends ImageWidget {
        private ResourceLocation texture;
        private final int texW;
        private final int texH;

        Texture(int x, int y, int width, int height, ResourceLocation texture, int texW, int texH) {
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

        @Override
        protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            if (texture == null)
                return;
            TextureStretching.drawStatic(texture, x, y, width, height, texW, texH, alpha);
        }

        @Override
        public void updateResource(ResourceLocation texture) {
            this.texture = texture;
        }
    }

    // ──── 变体：精灵（任意拉伸） ────

    /**
     * <p>
     * 精灵变体 —— 对标高版本 {@code ImageWidget.Sprite}。<br>
     * 整图拉伸到组件区域（任意尺寸），对应高版本图集精灵的任意缩放语义。
     * </p>
     * <p>
     * Sprite variant — counterpart of {@code ImageWidget.Sprite}. The whole
     * texture is stretched over the widget area (any size), matching the
     * arbitrary-scale semantics of the high version's atlas sprite.
     * </p>
     */
    private static class Sprite extends ImageWidget {
        private ResourceLocation sprite;

        Sprite(int x, int y, int width, int height, ResourceLocation sprite) {
            super(x, y, width, height);
            this.sprite = sprite;
        }

        @Override
        protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
            if (sprite == null)
                return;
            TextureStretching.drawStatic(sprite, x, y, width, height, width, height, alpha);
        }

        @Override
        public void updateResource(ResourceLocation sprite) {
            this.sprite = sprite;
        }
    }
}
