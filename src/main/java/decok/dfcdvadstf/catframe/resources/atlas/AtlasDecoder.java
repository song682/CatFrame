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
 * Atlas definition decoder — parses {@code assets/<namespace>/atlases/<id>.json} (Wiki-compatible format,
 * plural {@code atlases/} directories, mirrors 26.1.2 {@code SpriteSourceList} JSON shape).
 * <p>
 * Root element {@code sources} is an array; each source is distinguished by a {@code type} key
 * (namespaced type id, e.g. {@code "minecraft:directory"}), with remaining fields as parameters:
 * <ul>
 *   <li>{@code minecraft:directory} — {@code source} directory name, {@code prefix} sprite id prefix;</li>
 *   <li>{@code minecraft:filter} — {@code namespace} / {@code path} regex (defaults to match all);</li>
 *   <li>{@code minecraft:single} — {@code resource} source texture, {@code sprite} publish id (optional);</li>
 *   <li>{@code minecraft:unstitch} — {@code resource} + {@code divisor_x/divisor_y} +
 *       {@code regions} (each region {@code sprite/x/y/width/height}, block coords, Wiki format);</li>
 *   <li>{@code paletted_permutations} — {@code textures} + {@code palette_key} +
 *       {@code permutations} (values = namespace IDs) + {@code separator} (default {@code _}).</li>
 * </ul>
 * Unknown type or missing fields → warn + skip that source (no crash, whole definition still usable).
 *
 * <p>Parses atlas definition JSONs into a source list; unknown types and
 * malformed entries degrade with a warning instead of crashing.
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class AtlasDecoder {

    private static final Gson GSON = new Gson();

    private AtlasDecoder() {
    }

    /**
     * Decode a definition file.
     *
     * @param in definition JSON input stream (caller responsible for closing)
     * @return source list (empty = no valid sources in definition)
     * @throws IOException thrown on JSON parse failure (caller handles fallback)
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

    /** Build source by type suffix; unknown type warns + returns null (skip). */
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
     * Parse unstitch (Wiki format): divisor_x/divisor_y default 1; at least one region,
     * each region requires sprite (missing skips region), x/y/width/height default 0/0/1/1.
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
        // Wiki format: permutations values are namespace IDs directly (palette texture position swaps)
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

    /** Required string field; throws on missing (caught by parse's fallback). */
    private static String require(JsonObject o, String key) {
        String s = str(o, key);
        if (s == null || s.isEmpty()) {
            throw new IllegalArgumentException("missing required field '" + key + "'");
        }
        return s;
    }
}
