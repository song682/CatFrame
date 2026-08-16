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
 * 图集 sprite 解码器（对标 26.1.2 {@code SpriteResourceLoader}，适配 1.7.10）。
 * <p>
 * 职责：
 * <ul>
 *   <li>按 {@link ResourceLocation} 直读 PNG（路径 = 数据驱动定义产出的 sprite id
 *       投影，即 {@code textures/<path>.png}，不做任何单复数目录回退；ImageIO 解码，
 *       与 1.7.10 原版 TextureMap 一致）；</li>
 *   <li>读取同名 {@code .png.mcmeta} 解析 mojang 格式动画（{@code animation.frametime} 单位 =
 *       tick × 50ms；{@code frames} 支持 int 索引与 {@code {index, time}} 对象；缺省 = 逐行帧），
 *       帧数 = height / width（mojang 约定帧为正方形）；</li>
 *   <li>像素经 {@link PixelTransform} 逐帧应用（unstitch 裁剪 / paletted 关键色替换）；
 *       transform 后各帧尺寸不一致时降级为单帧（transform 第 1 帧结果）；</li>
 *   <li>产出 {@link CatSprite}（单帧或动画多帧）；任何失败返回 null，调用方用 missing 兜底
 *       （找不到纹理 → missingno，与原版缺失语义一致）。</li>
 * </ul>
 * 运行于 {@link RenderExecutors} 并行池；失败语义按 Wiki：纹理缺失/解码失败 → 调用方记录为
 * 错误 sprite（missing），不崩溃。
 *
 * <p>Decodes atlas sprites: PNG + mojang-format animation metadata, applying the
 * per-sprite pixel transform on every frame. Returns {@code null} on failure.
 */
@SideOnly(Side.CLIENT)
public final class CatSpriteLoader {

    private static final Gson GSON = new Gson();
    /** 每 tick 时长（ms）；mojang frametime 单位 = tick。 */
    private static final int TICK_MS = 50;

    private CatSpriteLoader() {
    }

    /**
     * 解码一个 sprite。
     *
     * @param resource  源纹理位置（数据驱动定义产出的 sprite id，如 {@code minecraft:blocks/ladder}；
     *                  纹理文件恒在 {@code textures/<path>.png}，不做目录回退）
     * @param spriteId  发布键（完整纹理路径格式 {@code ns:path}，如
     *                  {@code minecraft:blocks/ladder} 或 unstitch 产物 {@code ..._3}）
     * @param atlasId   所属图集 id（透传给 CatSprite）
     * @param transform 像素变换（可为 null）
     * @return CatSprite（单帧或多帧）；失败返回 null
     */
    public static CatSprite load(ResourceLocation resource, String spriteId, String atlasId,
                                 PixelTransform transform) {
        // icon 名称 = 发布键本身（数据驱动键，如 "minecraft:blocks/ladder"），无前缀改写。
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
                // 资源存在但 ImageIO 无法解码（如非 PNG/JPG 内容）→ 缺失语义，调用方用 missing 兜底
                CatFrame.logger.warn("[SpriteLoader] '{}' exists but ImageIO cannot decode it", rl);
                return null;
            }
            int w = image.getWidth();
            int h = image.getHeight();
            int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
            // 动画元数据：与 PNG 同目录同名 .png.mcmeta（mojang 格式）
            int[] frameTimes = readAnimation(mgr, metaLocation(rl), w, h);
            return buildSprite(spriteId, iconName, pixels, w, h, atlasId, frameTimes, transform);
        } catch (IOException | RuntimeException ex) {
            // 纹理不存在/解码失败 → 纹理错误，调用方用 missing（missingno）兜底
            CatFrame.logger.warn("[SpriteLoader] texture error: '{}' not found / failed to decode ({}: {})",
                    spriteId, ex.getClass().getSimpleName(), ex.getMessage());
        }
        return null;
    }

    /** PNG 位置 → 同名 .mcmeta 位置（同命名空间同目录）。 */
    private static ResourceLocation metaLocation(ResourceLocation png) {
        return new ResourceLocation(png.getResourceDomain(), png.getResourcePath() + ".mcmeta");
    }

    /**
     * 读取 mojang 格式动画元数据；无 animation 键或文件缺失 → 返回 null（单帧）。
     * <p>
     * 帧数 = height / width（mojang 约定帧为正方形，多余行忽略）；
     * frametime 缺省 1 tick；frames 缺省 = 逐行递增索引；
     * frames 元素支持 int（时长 = frametime）与对象 {@code {index, time}}。
     * {@code interpolate}（补间）不支持，忽略并记 debug。
     *
     * @return 每帧时长（ms，长度 = 帧数）；无动画返回 null
     */
    private static int[] readAnimation(IResourceManager mgr, ResourceLocation metaRl,
                                       int width, int height) {
        IResource meta;
        try {
            meta = mgr.getResource(metaRl);
        } catch (IOException e) {
            return null; // 无 .mcmeta = 单帧
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
     * 组装 CatSprite：无动画 → 单帧；有动画 → 逐帧切分 + 逐帧 transform。
     * transform 后各帧尺寸不一致（异常裁剪）→ 降级单帧（transform 第 1 帧结果）。
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
        // 多帧：帧高 = 总高 / 帧数（mojang 约定），逐帧切分
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
            // 逐帧应用变换；首帧结果定义目标尺寸，后续帧尺寸不一致则整体降级单帧
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
