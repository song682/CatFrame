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
 *   <li>{@code minecraft:unstitch} —— {@code resource} + {@code divisor_x/divisor_y/base_row/base_column/count}；</li>
 *   <li>{@code paletted_permutations} —— {@code textures} + {@code palette_key} + {@code permutations}。</li>
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
                    return new UnstitchSource(new ResourceLocation(require(o, "resource")),
                            intOf(o, "divisor_x", 1), intOf(o, "divisor_y", 1),
                            intOf(o, "base_row", 0), intOf(o, "base_column", 0),
                            intOf(o, "count", 0));
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

    private static PalettedPermutationsSource parsePaletted(JsonObject o) {
        JsonElement texEl = o.get("textures");
        List<ResourceLocation> bases = new ArrayList<>();
        if (texEl != null && texEl.isJsonArray()) {
            for (JsonElement e : texEl.getAsJsonArray()) {
                bases.add(new ResourceLocation(e.getAsString()));
            }
        }
        ResourceLocation key = new ResourceLocation(require(o, "palette_key"));
        Map<String, ResourceLocation> perms = new LinkedHashMap<>();
        JsonElement permsEl = o.get("permutations");
        if (permsEl != null && permsEl.isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : permsEl.getAsJsonObject().entrySet()) {
                JsonElement poEl = en.getValue();
                if (poEl == null || !poEl.isJsonObject()) {
                    continue;
                }
                JsonObject po = poEl.getAsJsonObject();
                JsonElement ptEl = po.get("textures");
                if (ptEl == null || !ptEl.isJsonArray() || ptEl.getAsJsonArray().size() == 0) {
                    continue;
                }
                JsonElement mapEl = ptEl.getAsJsonArray().get(0);
                if (mapEl == null || !mapEl.isJsonObject()) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> me : mapEl.getAsJsonObject().entrySet()) {
                    perms.put(en.getKey(), new ResourceLocation(me.getValue().getAsString()));
                }
            }
        }
        return new PalettedPermutationsSource(bases, key, perms);
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
