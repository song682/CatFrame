package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 精灵表拆分源（对标 26.1.2 {@code minecraft:unstitch}，格式与 Wiki 一致）。
 * <p>
 * 把一张大图按 {@code divisor_x × divisor_y} 网格切块，从 {@code regions} 列表
 * 逐个截取区域作为 sprite：区域坐标 x/y/width/height 为<b>块坐标</b>，
 * 像素坐标按 Wiki 公式换算 —— {@code px = ⌊x·srcW/divisor_x⌋}、
 * {@code pw = ⌊width·srcW/divisor_x⌋}（y/height 同理）。sprite id 由每个 region
 * 的 {@code sprite} 字段显式指定（不再用 base_row/base_column/count 旧格式）。
 * <p>
 * 定义 JSON 示例（把 64×64 精灵表切成 16 个 16×16，取前两个）：
 * <pre>{@code {"type": "minecraft:unstitch", "resource": "minecraft:item/sheet",
 * "divisor_x": 4, "divisor_y": 4,
 * "regions": [
 *   {"sprite": "minecraft:item/sheet_0", "x": 0, "y": 0, "width": 1, "height": 1},
 *   {"sprite": "minecraft:item/sheet_1", "x": 1, "y": 0, "width": 1, "height": 1}
 * ]}}</pre>
 * 越界区域（起点超出源图）输出透明占位，不崩溃；像素裁剪经 {@link PixelTransform}
 * 在解码期施加。
 *
 * <p>Splits a spritesheet into grid regions declared by the definition;
 * each region is cropped at decode time via a pixel transform.
 */
@SideOnly(Side.CLIENT)
public final class UnstitchSource implements AtlasSource {

    /** 单个截取区域（块坐标，Wiki 语义：像素 = ⌊块坐标×源尺寸/divisor⌋）。 */
    public static final class Region {
        /** 此区域生成的精灵图命名空间 ID。 */
        public final ResourceLocation sprite;
        /** 左上角 X（块坐标）。 */
        public final int x;
        /** 左上角 Y（块坐标）。 */
        public final int y;
        /** 区域宽度（块坐标）。 */
        public final int width;
        /** 区域高度（块坐标）。 */
        public final int height;

        public Region(ResourceLocation sprite, int x, int y, int width, int height) {
            this.sprite = sprite;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private final ResourceLocation resource;
    private final int divisorX;
    private final int divisorY;
    private final List<Region> regions;

    public UnstitchSource(ResourceLocation resource, int divisorX, int divisorY, List<Region> regions) {
        this.resource = resource;
        this.divisorX = Math.max(1, divisorX);
        this.divisorY = Math.max(1, divisorY);
        this.regions = regions;
    }

    @Override
    public String type() {
        return "minecraft:unstitch";
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
        List<SpriteRef> out = new ArrayList<>(regions.size());
        for (final Region r : regions) {
            out.add(SpriteRef.of(r.sprite, resource, null, new PixelTransform() {
                @Override
                public Result apply(int[] src, int srcWidth, int srcHeight) {
                    // Wiki 公式：像素坐标 = floor(块坐标 × 源尺寸 / divisor)（Java 正数除法即 floor）
                    int px = r.x * srcWidth / divisorX;
                    int py = r.y * srcHeight / divisorY;
                    int pw = Math.max(1, r.width * srcWidth / divisorX);
                    int ph = Math.max(1, r.height * srcHeight / divisorY);
                    // 越界区域：输出透明占位（不崩溃，与 26.1.2 语义一致）
                    if (px >= srcWidth || py >= srcHeight) {
                        return new Result(new int[pw * ph], pw, ph);
                    }
                    // 边缘区域：宽度/高度收窄到源图边界内
                    pw = Math.min(pw, srcWidth - px);
                    ph = Math.min(ph, srcHeight - py);
                    int[] outPx = new int[pw * ph];
                    for (int y = 0; y < ph; y++) {
                        System.arraycopy(src, (py + y) * srcWidth + px, outPx, y * pw, pw);
                    }
                    return new Result(outPx, pw, ph);
                }
            }));
        }
        if (!regions.isEmpty()) {
            CatFrame.logger.debug("[UnstitchSource] '{}' -> {} regions ({}x{} grid)",
                    resource, regions.size(), divisorX, divisorY);
        }
        return out;
    }

    @Override
    public String toString() {
        return "UnstitchSource{" + resource + " " + divisorX + "x" + divisorY + " x" + regions.size() + "}";
    }
}
