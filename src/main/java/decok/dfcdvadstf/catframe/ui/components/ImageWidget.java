package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.util.ResourceLocation;

/**
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
     */
    public static ImageWidget texture(int width, int height, ResourceLocation texture, int texW, int texH) {
        return new Texture(0, 0, width, height, texture, texW, texH);
    }

    /**
     * Creates a texture-stretched image widget at the given position and size.
     */
    public static ImageWidget texture(int x, int y, int width, int height,
            ResourceLocation texture, int texW, int texH) {
        return new Texture(x, y, width, height, texture, texW, texH);
    }

    /**
     * Creates a sprite image widget stretched to the given size (position defaults
     * to 0,0).
     */
    public static ImageWidget sprite(int width, int height, ResourceLocation sprite) {
        return new Sprite(0, 0, width, height, sprite);
    }

    /**
     * Creates a sprite image widget at the given position and size.
     */
    public static ImageWidget sprite(int x, int y, int width, int height, ResourceLocation sprite) {
        return new Sprite(x, y, width, height, sprite);
    }

    /**
     * Updates the displayed resource at runtime.
     */
    public abstract void updateResource(ResourceLocation texture);

    /**
     * <p>
     * Display-only widget: never active — counterpart of the high-version
     * {@code ImageWidget#isActive()} override.
     * </p>
     */
    @Override
    public boolean isActive() {
        return false;
    }

    // ──── Variant: whole-texture (pixel-perfect integer upscaling) ────

    /**
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

    // ──── Variant: sprite (any stretch) ────

    /**
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
