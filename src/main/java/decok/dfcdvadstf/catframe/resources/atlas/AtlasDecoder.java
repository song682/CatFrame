package decok.dfcdvadstf.catframe.resources.atlas;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.resources.atlas.source.AtlasSource;
import decok.dfcdvadstf.catframe.resources.atlas.source.DirectorySource;
import decok.dfcdvadstf.catframe.resources.atlas.source.FilterSource;
import decok.dfcdvadstf.catframe.resources.atlas.source.PalettedPermutationsSource;
import decok.dfcdvadstf.catframe.resources.atlas.source.SingleSource;
import decok.dfcdvadstf.catframe.resources.atlas.source.UnstitchSource;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图集定义解析器 —— 解析 {@code assets/<namespace>/atlas/<id>.json}（Wiki 兼容格式，
 * 对标 26.1.2 {@code SpriteSourceList} 的 JSON 形态）。
 * <p>
 * 根元素 {@code sources} 为数组；每个 source 以 {@code type} 键区分
 * （带 namespace 的 type id，如 {@code "minecraft:directory"}），其余字段为参数：
 * <ul>
 *   <li>{@code minecraft:directory} —— {@code source} 目录名、{@code prefix} sprite id 前缀；</li>
 *   <li>{@code minecraft:filter} —— {@code namespace} / {@code path} 正则（缺省匹配全部）；</li>
 *   <li>{@code minecraft:single} —— {@code resource} 源纹理、{@code sprite} 发布 id（可缺省）；</li>
 *   <li>{@code minecraft:unstitch} —— {@code resource} + {@code divisor_x/divisor_y} +
 *       {@code regions}（每区域 {@code sprite/x/y/width/height}，块坐标，Wiki 格式）；</li>
 *   <li>{@code paletted_permutations} —— {@code textures} + {@code palette_key} +
 *       {@code permutations}（值 = 命名空间 ID）+ {@code separator}（缺省 {@code _}）。</li>
 * </ul>
 * 未知 type 或字段缺失 → warn + 跳过该源（不崩溃，定义文件整体仍可用）。
 *
 * <p>Parses atlas definition JSONs into a source list; unknown types and
 * malformed entries degrade with a warning instead of crashing.
 */
@SideOnly(Side.CLIENT)
public final class AtlasDecoder {

    private static final Gson GSON = new Gson();

    private AtlasDecoder() {
    }

    /**
     * 解码定义文件。
     *
     * @param in 定义 JSON 输入流（调用方负责关闭）
     * @return source 列表（空 = 定义无有效源）
     * @throws IOException JSON 不可解析时抛出（调用方降级处理）
     */
    public static List<AtlasSource> decode(InputStream in) throws IOException {
        List<AtlasSource> out = new ArrayList<>();
        JsonObject root;
        try {
            root = GSON.fromJson(new InputStreamReader(in, "UTF-8"), JsonObject.class);
        } catch (RuntimeException e) {
            throw new IOException("atlas definition JSON parse failed: " + e.getMessage(), e);
        }
        if (root == null) {
            return out;
        }
        JsonElement sourcesEl = root.get("sources");
        if (sourcesEl == null || !sourcesEl.isJsonArray()) {
            CatFrame.logger.warn("[AtlasDecoder] definition has no 'sources' array");
            return out;
        }
        for (JsonElement el : sourcesEl.getAsJsonArray()) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            JsonElement typeEl = o.get("type");
            if (typeEl == null || !typeEl.isJsonPrimitive()) {
                CatFrame.logger.warn("[AtlasDecoder] source entry without 'type', skipping");
                continue;
            }
            AtlasSource source = parse(o, typeEl.getAsString());
            if (source != null) {
                out.add(source);
            }
        }
        return out;
    }

    /** 按 type 后缀构建源；未知类型 warn + 返回 null（跳过）。 */
    private static AtlasSource parse(JsonObject o, String type) {
        String suffix = type.indexOf(':') >= 0 ? type.substring(type.indexOf(':') + 1) : type;
        try {
            switch (suffix) {
                case "directory":
                    return new DirectorySource(require(o, "source"), require(o, "prefix"));
                case "filter":
                    return new FilterSource(str(o, "namespace"), str(o, "path"));
                case "single":
                    return new SingleSource(new ResourceLocation(require(o, "resource")),
                            o.has("sprite") ? rl(o, "sprite") : null);
                case "unstitch":
                    return parseUnstitch(o);
                case "paletted_permutations":
                    return parsePaletted(o);
                default:
                    CatFrame.logger.warn("[AtlasDecoder] unknown source type '{}', skipping", type);
                    return null;
            }
        } catch (RuntimeException e) {
            CatFrame.logger.warn("[AtlasDecoder] source '{}' malformed ({}), skipping", type, e.getMessage());
            return null;
        }
    }

    /**
     * 解析 unstitch（Wiki 格式）：divisor_x/divisor_y 缺省 1；regions 至少一个元素，
     * 每区域 sprite 必填（缺失跳过该区域），x/y/width/height 缺省 0/0/1/1。
     */
    private static UnstitchSource parseUnstitch(JsonObject o) {
        ResourceLocation resource = new ResourceLocation(require(o, "resource"));
        int divisorX = intOf(o, "divisor_x", 1);
        int divisorY = intOf(o, "divisor_y", 1);
        List<UnstitchSource.Region> regions = new ArrayList<>();
        JsonElement regionsEl = o.get("regions");
        if (regionsEl != null && regionsEl.isJsonArray()) {
            for (JsonElement el : regionsEl.getAsJsonArray()) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject ro = el.getAsJsonObject();
                String sprite = str(ro, "sprite");
                if (sprite == null || sprite.isEmpty()) {
                    continue;
                }
                regions.add(new UnstitchSource.Region(new ResourceLocation(sprite),
                        intOf(ro, "x", 0), intOf(ro, "y", 0),
                        intOf(ro, "width", 1), intOf(ro, "height", 1)));
            }
        }
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("unstitch requires at least one region");
        }
        return new UnstitchSource(resource, divisorX, divisorY, regions);
    }

    private static PalettedPermutationsSource parsePaletted(JsonObject o) {
        JsonElement texEl = o.get("textures");
        List<ResourceLocation> bases = new ArrayList<>();
        if (texEl != null && texEl.isJsonArray()) {
            for (JsonElement e : texEl.getAsJsonArray()) {
                bases.add(new ResourceLocation(e.getAsString()));
            }
        }
        ResourceLocation key = new ResourceLocation(require(o, "palette_key"));
        // Wiki 格式：permutations 的值直接是命名空间 ID（置换调色板纹理位置）
        Map<String, ResourceLocation> perms = new LinkedHashMap<>();
        JsonElement permsEl = o.get("permutations");
        if (permsEl != null && permsEl.isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : permsEl.getAsJsonObject().entrySet()) {
                JsonElement v = en.getValue();
                if (v == null || !v.isJsonPrimitive()) {
                    continue;
                }
                perms.put(en.getKey(), new ResourceLocation(v.getAsString()));
            }
        }
        String separator = str(o, "separator");
        return new PalettedPermutationsSource(bases, key, perms, separator);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonPrimitive()) ? e.getAsString() : null;
    }

    private static int intOf(JsonObject o, String key, int def) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonPrimitive()) ? e.getAsInt() : def;
    }

    private static ResourceLocation rl(JsonObject o, String key) {
        String s = str(o, key);
        return s != null ? new ResourceLocation(s) : null;
    }

    /** 必填字符串字段；缺失抛异常（由 parse 的 catch 降级）。 */
    private static String require(JsonObject o, String key) {
        String s = str(o, key);
        if (s == null || s.isEmpty()) {
            throw new IllegalArgumentException("missing required field '" + key + "'");
        }
        return s;
    }
}
