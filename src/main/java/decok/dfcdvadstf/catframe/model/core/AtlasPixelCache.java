package decok.dfcdvadstf.catframe.model.core;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.ItemModelGenerator;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GPU-side texture atlas readback cache — replaces {@code MixinTextureMap}'s CPU frame data storage.
 * <p>
 * At {@link net.minecraftforge.client.event.TextureStitchEvent.Post} (main thread, GL
 * context alive, atlas fully uploaded) uses {@code glGetTexImage} to read back the entire
 * atlas level-0 in one shot, then slices out sub-rectangles per sprite UV coords into
 * ARGB int arrays.
 * <p>
 * Async bake threads only read this pure CPU cache (thread-safe, same semantics as
 * original {@code preservedFrames} reads), never touch GL context.
 * <p>
 * <b>Channel conversion</b>: {@code glGetTexImage(GL_RGBA, GL_UNSIGNED_BYTE)} byte order is R,G,B,A;
 * but code (e.g. {@link ItemModelGenerator#bakeSideFaces}) works with Java int ARGB format
 * (bits 24-31=A, 16-23=R, 8-15=G, 0-7=B). This class performs RGBA→ARGB reorder on readback.
 * </p>
 */
public final class AtlasPixelCache {

    private AtlasPixelCache() {
    }

    /**
     * Each sprite's level-0 content pixel slice (flat ARGB int[], size = width × height).
     * Key is {@link TextureAtlasSprite#getIconName()}.
     * <p>
     * Slice dimensions derived from sprite UV region (minU~maxU), not getIconWidth()/getIconHeight():
     * with anisotropic filtering, 1.7.10 vanilla expands physical storage to (content+16)x(content+16)
     * and UVs shrink to center content region (see TextureAtlasSprite.loadSprite / initSprite /
     * prepareAnisotropicData). At this point getIconWidth() returns physical storage size; using it
     * directly as slice width would bleed adjacent sprite pixels.
     */
    private static final Map<String, CachedSprite> spriteCache = new ConcurrentHashMap<>();

    /**
     * Sprite pixel slice cropped by UV region (content area, excludes anisotropic filtering padding).
     */
    public static final class CachedSprite {
        /** flat ARGB pixel array (row-major, width × height) */
        public final int[] pixels;
        /** Content region width (pixels) */
        public final int width;
        /** Content region height (pixels) */
        public final int height;

        CachedSprite(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Read back level-0 pixels from GPU for given atlas, slice sub-rects for given sprite set.
     * <p>
     * <b>Must be called on main thread (GL context alive).</b>
     *
     * @param atlas texture atlas (block atlas or item atlas)
     * @param icons sprite set to cache pixels for (typically from
     *              {@link VanillaTextureTracker#textureIcons})
     */
    public static void readAtlas(TextureMap atlas, Collection<IIcon> icons) {
        if (atlas == null || icons == null || icons.isEmpty())
            return;

        int glTexId = atlas.getGlTextureId();
        if (glTexId <= 0) {
            CatFrame.logger.warn("[AtlasPixelCache] atlas GL texture id invalid: {}", glTexId);
            return;
        }

        // Bind and query atlas dimensions
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTexId);
        int atlasW = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int atlasH = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (atlasW <= 0 || atlasH <= 0) {
            CatFrame.logger.warn("[AtlasPixelCache] atlas dimensions invalid: {}x{}", atlasW, atlasH);
            return;
        }

        // One-shot readback of entire atlas level-0 (RGBA byte order)
        ByteBuffer buffer = BufferUtils.createByteBuffer(atlasW * atlasH * 4);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        // Convert to ARGB int array (full atlas)
        int[] atlasARGB = new int[atlasW * atlasH];
        for (int i = 0; i < atlasARGB.length; i++) {
            int r = buffer.get() & 0xFF;
            int g = buffer.get() & 0xFF;
            int b = buffer.get() & 0xFF;
            int a = buffer.get() & 0xFF;
            atlasARGB[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        // Slice per sprite
        int cached = 0;
        for (IIcon icon : icons) {
            if (!(icon instanceof TextureAtlasSprite))
                continue;
            TextureAtlasSprite sprite = (TextureAtlasSprite) icon;

            // Slice dimensions from UV region (content area): anisotropic filtering expands
        // physical storage to (content+16)x(content+16) and UVs shrink to center content.
        // getIconWidth() returns physical storage size; using it directly as slice
        // width would bleed adjacent sprite pixels (see class comment).
            int w = Math.max(1, Math.round((sprite.getMaxU() - sprite.getMinU()) * atlasW));
            int h = Math.max(1, Math.round((sprite.getMaxV() - sprite.getMinV()) * atlasH));
            if (w <= 0 || h <= 0)
                continue;

            // From UV back-calc pixel origin (atlas coords)
            int originX = Math.round(sprite.getMinU() * atlasW);
            int originY = Math.round(sprite.getMinV() * atlasH);

            // Safety bounds check
            if (originX < 0 || originY < 0
                    || originX + w > atlasW || originY + h > atlasH) {
                CatFrame.logger.debug(
                        "[AtlasPixelCache] sprite '{}' out of bounds: origin=({},{}) size={}x{} atlas={}x{}",
                        sprite.getIconName(), originX, originY, w, h, atlasW, atlasH);
                continue;
            }

            // Slice sub-rectangle (flat array, row-major)
            int[] pixels = new int[w * h];
            for (int sy = 0; sy < h; sy++) {
                int srcOffset = (originY + sy) * atlasW + originX;
                System.arraycopy(atlasARGB, srcOffset, pixels, sy * w, w);
            }

            spriteCache.put(sprite.getIconName(), new CachedSprite(pixels, w, h));
            cached++;
        }

        CatFrame.logger.info("[AtlasPixelCache] readAtlas: {}x{} | sprites cached: {} / {}",
                atlasW, atlasH, cached, icons.size());
    }

    /**
     * Get level-0 content pixel slice for specified sprite (flat ARGB int[]).
     *
     * @param iconName sprite name (e.g. "stick", "diamond_sword")
     * @return pixel array, or null if not cached
     */
    public static int[] getPixels(String iconName) {
        CachedSprite cached = spriteCache.get(iconName);
        return cached != null ? cached.pixels : null;
    }

    /**
     * Get content pixel slice for specified sprite (with content width/height).
     *
     * @param iconName sprite name (e.g. "stick", "diamond_sword")
     * @return slice object, or null if not cached
     */
    public static CachedSprite getSprite(String iconName) {
        return spriteCache.get(iconName);
    }

    /**
     * Clear cache (called on resource reload).
     */
    public static void clear() {
        spriteCache.clear();
    }
}
