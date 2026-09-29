package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Palette permutation source (mirrors 26.1.2 {@code paletted_permutations},
 * matching the Wiki format) — M4 dynamic dyed variants.
 * <p>
 * Semantics (identical to 26.1.2): for every {@code textures} base texture ×
 * every {@code permutations} variant, emit sprite id =
 * {@code <base><separator><permName>} (separator defaults to {@code _});
 * pixel handling = any color in base equal to a <b>non-transparent</b> pixel
 * color of {@code palette_key} is replaced with the color at the same position
 * in the permutation overlay (palette_key and overlay must have the same size).
 * <p>
 * Definition JSON example (Wiki format: permutation values are namespace ids
 * directly):
 * <pre>{@code {"type": "paletted_permutations",
 * "textures": ["minecraft:item/leather_helmet"],
 * "palette_key": "minecraft:item/leather_helmet_overlay",
 * "permutations": {"red": "minecraft:item/leather_helmet_overlay_red"},
 * "separator": "_"}}</pre>
 * <p>
 * palette_key and overlays are read on the main thread in {@link #list} and
 * turned into a color map; the pixel-replacement closure then runs on the
 * parallel decode threads — the closure is read-only and thread-safe. Reads
 * fall back across the 1.7.10 plural texture directories
 * ({@code textures/block/} → {@code textures/blocks/}).
 */
@SideOnly(Side.CLIENT)
public final class PalettedPermutationsSource implements AtlasSource {

    /** Base texture list (each yields one variant per permutation). */
    private final List<ResourceLocation> textures;
    /** Palette template (non-transparent pixel colors = key colors). */
    private final ResourceLocation paletteKey;
    /** Variant name → overlay texture (Wiki format: value = namespace id). */
    private final Map<String, ResourceLocation> permutations;
    /** Variant id separator (defaults to {@code _}; customizable since 25w04a). */
    private final String separator;

    public PalettedPermutationsSource(List<ResourceLocation> textures,
                                      ResourceLocation paletteKey,
                                      Map<String, ResourceLocation> permutations,
                                      String separator) {
        this.textures = textures;
        this.paletteKey = paletteKey;
        this.permutations = permutations;
        this.separator = (separator == null || separator.isEmpty()) ? "_" : separator;
    }

    @Override
    public String type() {
        return "paletted_permutations";
    }

    @Override
    public boolean removesCollected() {
        return false;
    }

    @Override
    public boolean shouldRemove(String spriteId) {
        return false;
    }

    @Override
    public List<SpriteRef> list(IResourceManager manager) {
        List<SpriteRef> out = new ArrayList<>();
        int[] keyPixels = readPixels(manager, paletteKey);
        if (keyPixels == null) {
            CatFrame.logger.warn("[PalettedPermutations] palette_key '{}' unreadable, source yields nothing",
                    paletteKey);
            return out;
        }
        for (Map.Entry<String, ResourceLocation> perm : permutations.entrySet()) {
            int[] overlay = readPixels(manager, perm.getValue());
            if (overlay == null) {
                CatFrame.logger.warn("[PalettedPermutations] overlay '{}' unreadable, skipping permutation '{}'",
                        perm.getValue(), perm.getKey());
                continue;
            }
            if (overlay.length != keyPixels.length) {
                CatFrame.logger.warn("[PalettedPermutations] overlay '{}' size {} != palette_key size {}, skipping",
                        perm.getValue(), overlay.length, keyPixels.length);
                continue;
            }
            // Build the color map: key color → overlay color at the same position (later entries override duplicate keys)
            final Map<Integer, Integer> colorMap = new HashMap<>();
            for (int i = 0; i < keyPixels.length; i++) {
                int key = keyPixels[i];
                if ((key >>> 24) != 0) { // Transparent pixels are not used as key colors
                    colorMap.put(key, overlay[i]);
                }
            }
            final String permName = perm.getKey();
            for (final ResourceLocation base : textures) {
                ResourceLocation spriteId = new ResourceLocation(base.getResourceDomain(),
                        base.getResourcePath() + separator + permName);
                out.add(SpriteRef.of(spriteId, base, null, new PixelTransform() {
                    @Override
                    public Result apply(int[] src, int srcWidth, int srcHeight) {
                        int[] px = new int[src.length];
                        for (int i = 0; i < src.length; i++) {
                            Integer replacement = colorMap.get(src[i]);
                            px[i] = replacement != null ? replacement : src[i];
                        }
                        return new Result(px, srcWidth, srcHeight);
                    }
                }));
            }
        }
        return out;
    }

    /** Reads texture pixels (flat ARGB); returns null on failure (no crash).
     * 1.7.10 textures live in plural directories (textures/blocks|items); single-form paths fall back automatically. */
    private static int[] readPixels(IResourceManager manager, ResourceLocation rl) {
        for (ResourceLocation candidate : candidates(rl)) {
            try {
                IResource res = manager.getResource(candidate);
                BufferedImage image = ImageIO.read(res.getInputStream());
                if (image == null) {
                    continue;
                }
                return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            } catch (IOException | RuntimeException e) {
                // Missing / decode failure: try the next candidate
            }
        }
        return null;
    }

    /** Candidate texture locations: {@code textures/<path>.png} first; singular block/item prefixes fall back to plural directories. */
    private static List<ResourceLocation> candidates(ResourceLocation rl) {
        List<ResourceLocation> out = new ArrayList<>(2);
        String ns = rl.getResourceDomain();
        String path = rl.getResourcePath();
        out.add(new ResourceLocation(ns, "textures/" + path + ".png"));
        if (path.startsWith("block/")) {
            out.add(new ResourceLocation(ns, "textures/blocks/" + path.substring("block/".length()) + ".png"));
        } else if (path.startsWith("item/")) {
            out.add(new ResourceLocation(ns, "textures/items/" + path.substring("item/".length()) + ".png"));
        }
        return out;
    }

    @Override
    public String toString() {
        return "PalettedPermutationsSource{" + textures + " key=" + paletteKey + " x" + permutations.keySet() + "}";
    }
}
