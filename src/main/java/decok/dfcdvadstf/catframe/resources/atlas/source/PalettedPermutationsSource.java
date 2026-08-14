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
 * 调色板置换源（对标 26.1.2 {@code paletted_permutations}，格式与 Wiki 一致）——
 * M4 动态染色变体。
 * <p>
 * 语义（与 26.1.2 一致）：对每个 {@code textures} 基础纹理 × 每个 {@code permutations}
 * 变体，产出 sprite id = {@code <base><separator><permName>}（separator 缺省 {@code _}）；
 * 像素处理 = 凡 base 中颜色与 {@code palette_key} 中某<b>非透明</b>像素颜色相同 →
 * 替换为同位置 permutation overlay 的颜色（palette_key 与 overlay 必须同尺寸）。
 * <p>
 * 定义 JSON 示例（Wiki 格式：permutations 值直接是命名空间 ID）：
 * <pre>{@code {"type": "paletted_permutations",
 * "textures": ["minecraft:item/leather_helmet"],
 * "palette_key": "minecraft:item/leather_helmet_overlay",
 * "permutations": {"red": "minecraft:item/leather_helmet_overlay_red"},
 * "separator": "_"}}</pre>
 * <p>
 * palette_key 与 overlays 在 {@link #list}（主线程）读取并构建颜色映射，像素替换
 * 闭包随后在并行解码线程应用 —— 闭包只读，线程安全。读取时按 1.7.10 复数纹理
 * 目录回退（{@code textures/block/} → {@code textures/blocks/}）。
 *
 * <p>Generates dyed sprite variants by key-colour replacement; the colour map
 * is built on the main thread, the replacement runs on the decode pool.
 */
@SideOnly(Side.CLIENT)
public final class PalettedPermutationsSource implements AtlasSource {

    /** 基础纹理列表（各 × 每 permutation 产出一个变体）。 */
    private final List<ResourceLocation> textures;
    /** 色板模板（非透明像素颜色 = 关键色）。 */
    private final ResourceLocation paletteKey;
    /** 变体名 → overlay 纹理（Wiki 格式：值 = 命名空间 ID）。 */
    private final Map<String, ResourceLocation> permutations;
    /** 变体 id 分隔符（缺省 {@code _}，25w04a 起可自定义）。 */
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
            // 构建颜色映射：关键色 → overlay 同位置颜色（后者覆盖重复关键色）
            final Map<Integer, Integer> colorMap = new HashMap<>();
            for (int i = 0; i < keyPixels.length; i++) {
                int key = keyPixels[i];
                if ((key >>> 24) != 0) { // 透明像素不作为关键色
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

    /** 读取纹理像素（flat ARGB）；失败返回 null（不崩溃）。
     * 1.7.10 纹理在复数目录（textures/blocks|items），单数路径自动回退。 */
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
                // 缺失/解码失败：尝试下一个候选
            }
        }
        return null;
    }

    /** 候选纹理位置：{@code textures/<path>.png} 优先，block/item 单数前缀回退复数目录。 */
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
