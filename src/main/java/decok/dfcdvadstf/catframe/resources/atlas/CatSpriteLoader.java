package decok.dfcdvadstf.catframe.resources.atlas;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.resources.atlas.source.PixelTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Atlas sprite decoder (mirrors 26.1.2 {@code SpriteResourceLoader}, adapted to
 * 1.7.10).
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>read the PNG directly by {@link ResourceLocation} (path = the projection of
 *       the data-driven sprite id, i.e. {@code textures/<path>.png}, with no singular
 *       / plural directory fallback; decoded via ImageIO, matching the 1.7.10 vanilla
 *       TextureMap);</li>
 *   <li>read the same-named {@code .png.mcmeta} to parse mojang-format animation
 *       ({@code animation.frametime} unit = tick × 50ms; {@code frames} accepts int
 *       indices and {@code {index, time}} objects; defaults to line-by-line frames),
 *       with frame count = height / width (mojang assumes square frames);</li>
 *   <li>apply the {@link PixelTransform} to every frame (unstitch clipping /
 *       paletted key-color replacement); if transformed frames end up with
 *       different sizes, degrade to a single frame (the transform result of frame 1);</li>
 *   <li>produce a {@link CatSprite} (single frame or animation frames); any failure
 *       returns null and the caller falls back to missing (texture not found →
 *       missingno, matching the vanilla missing semantics).</li>
 * </ul>
 * Runs on the {@link RenderExecutors} parallel pool; failure semantics follow the
 * Wiki: a missing texture / decode failure is recorded by the caller as an error
 * sprite (missing) without crashing.
 */
@SideOnly(Side.CLIENT)
public final class CatSpriteLoader {

    private static final Gson GSON = new Gson();
    /** Duration per tick (ms); the mojang frametime unit = tick. */
    private static final int TICK_MS = 50;

    private CatSpriteLoader() {
    }

    /**
     * Decodes one sprite.
     *
     * @param resource  source texture location (the data-driven sprite id, e.g.
     *                  {@code minecraft:blocks/ladder}; the texture file is always at
     *                  {@code textures/<path>.png}, no directory fallback)
     * @param spriteId  the publish key (full texture path form {@code ns:path}, e.g.
     *                  {@code minecraft:blocks/ladder} or an unstitch product {@code ..._3})
     * @param atlasId   the owning atlas id (passed through to CatSprite)
     * @param transform pixel transform (may be null)
     * @return the CatSprite (single frame or multiple frames); null on failure
     */
    public static CatSprite load(ResourceLocation resource, String spriteId, String atlasId,
                                 PixelTransform transform) {
        // The icon name is the publish key itself; the data-driven key is used
        // verbatim, with no prefix rewriting or directory fallback.
        String iconName = spriteId;
        IResourceManager mgr = Minecraft.getMinecraft().getResourceManager();
        ResourceLocation rl = new ResourceLocation(resource.getResourceDomain(),
                "textures/" + resource.getResourcePath() + ".png");
        try {
            IResource res = mgr.getResource(rl);
            BufferedImage image = ImageIO.read(res.getInputStream());
            if (image == null) {
                // Resource exists but ImageIO cannot decode it (e.g. non-PNG/JPG content) → missing semantics, caller falls back to missing
                CatFrame.logger.warn("[SpriteLoader] '{}' exists but ImageIO cannot decode it", rl);
                return null;
            }
            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
            // Animation metadata: a same-named .png.mcmeta next to the PNG (mojang format)
            int[] frameTimes = readAnimation(mgr, metaLocation(rl), w, h);
            return buildSprite(spriteId, iconName, pixels, w, h, atlasId, frameTimes, transform);
        } catch (IOException | RuntimeException ex) {
            // Texture missing / decode failure → texture error, caller falls back to missing (missingno)
            CatFrame.logger.warn("[SpriteLoader] texture error: '{}' not found / failed to decode ({}: {})",
                    spriteId, ex.getClass().getSimpleName(), ex.getMessage());
        }
        return null;
    }

    /** PNG location → same-named .mcmeta location (same namespace, same directory). */
    private static ResourceLocation metaLocation(ResourceLocation png) {
        return new ResourceLocation(png.getResourceDomain(), png.getResourcePath() + ".mcmeta");
    }

    /**
     * Reads mojang-format animation metadata; null when there is no animation key
     * or the file is missing (a static sprite).
     * <p>
     * Frame count = height / width (mojang assumes square frames; extra lines are
     * ignored); frametime defaults to 1 tick; frames defaults to incrementing
     * line indices; frames elements accept an int (duration = frametime) or an
     * object {@code {index, time}}. {@code interpolate} is not supported and is
     * ignored with a debug log.
     *
     * @return per-frame durations (ms, length = frame count); null when static
     */
    private static int[] readAnimation(IResourceManager mgr, ResourceLocation metaRl,
                                       int width, int height) {
        IResource meta;
        try {
            meta = mgr.getResource(metaRl);
        } catch (IOException e) {
            return null; // No .mcmeta = static single frame
        }
        JsonObject root;
        try {
            root = GSON.fromJson(new InputStreamReader(meta.getInputStream(), "UTF-8"), JsonObject.class);
        } catch (IOException | RuntimeException e) {
            CatFrame.logger.warn("[SpriteLoader] '{}' malformed, treated as static: {}",
                    metaRl, e.getMessage());
            return null;
        }
        if (root == null) {
            return null;
        }
        JsonElement animEl = root.get("animation");
        if (animEl == null || !animEl.isJsonObject()) {
            return null;
        }
        JsonObject anim = animEl.getAsJsonObject();
        if (anim.has("interpolate") && anim.get("interpolate").getAsBoolean()) {
            CatFrame.logger.debug("[SpriteLoader] '{}' requests interpolate, not supported, ignored", metaRl);
        }
        int frameCount = height / width;
        if (frameCount <= 1) {
            return null;
        }
        int defaultTime = intOf(anim, "frametime", 1) * TICK_MS;
        int[] times = new int[frameCount];
        for (int i = 0; i < frameCount; i++) {
            times[i] = defaultTime;
        }
        JsonElement framesEl = anim.get("frames");
        if (framesEl != null && framesEl.isJsonArray()) {
            JsonArray frames = framesEl.getAsJsonArray();
            for (int i = 0; i < frames.size() && i < frameCount; i++) {
                JsonElement f = frames.get(i);
                if (f.isJsonPrimitive() && f.getAsJsonPrimitive().isNumber()) {
                    int idx = f.getAsInt();
                    if (idx >= 0 && idx < frameCount) {
                        times[i] = defaultTime;
                    }
                } else if (f.isJsonObject()) {
                    JsonObject fo = f.getAsJsonObject();
                    if (fo.has("index")) {
                        int idx = fo.get("index").getAsInt();
                        if (idx >= 0 && idx < frameCount) {
                            times[i] = intOf(fo, "time", 1) * TICK_MS;
                        }
                    }
                }
            }
        }
        return times;
    }

    /**
     * Assembles the CatSprite: no animation → single frame; animation → split per
     * frame + per-frame transform. If transformed frame sizes disagree (abnormal
     * clipping) → degrade to a single frame (the transform result of frame 1).
     */
    private static CatSprite buildSprite(String spriteId, String iconName, int[] pixels,
                                         int w, int h, String atlasId,
                                         int[] frameTimes, PixelTransform transform) {
        if (frameTimes == null) {
            if (transform == null) {
                return new CatSprite(spriteId, iconName, pixels, w, h, atlasId);
            }
            PixelTransform.Result r = transform.apply(pixels, w, h);
            return new CatSprite(spriteId, iconName, r.pixels, r.width, r.height, atlasId);
        }
        // Multi-frame: frame height = total height / frame count (mojang convention), split frame by frame
        int frameCount = frameTimes.length;
        int frameH = h / frameCount;
        int[][] frames = new int[frameCount][];
        for (int f = 0; f < frameCount; f++) {
            int[] src = new int[w * frameH];
            for (int y = 0; y < frameH; y++) {
                System.arraycopy(pixels, (f * frameH + y) * w, src, y * w, w);
            }
            frames[f] = src;
        }
        if (transform != null) {
            // Apply the transform per frame; the first frame defines the target size, and if a later frame disagrees the whole sprite degrades to a single frame
            int tw = -1, th = -1;
            for (int f = 0; f < frameCount; f++) {
                PixelTransform.Result r = transform.apply(frames[f], w, frameH);
                if (f == 0) {
                    tw = r.width;
                    th = r.height;
                    frames[f] = r.pixels;
                } else if (r.width == tw && r.height == th) {
                    frames[f] = r.pixels;
                } else {
                    CatFrame.logger.warn(
                            "[SpriteLoader] '{}' transformed frame {} size {}x{} != first frame {}x{}, "
                                    + "falling back to static first frame",
                            spriteId, f, r.width, r.height, tw, th);
                    return new CatSprite(spriteId, iconName, frames[0], tw, th, atlasId);
                }
            }
        }
        return new CatSprite(spriteId, iconName, frames, w, frameH, atlasId, frameTimes);
    }

    private static int intOf(JsonObject o, String key, int def) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonPrimitive()) ? e.getAsInt() : def;
    }
}
