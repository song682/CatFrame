package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 精灵表拆分源（对标 26.1.2 {@code minecraft:unstitch}）。
 * <p>
 * 把一张大图按 {@code divisor_x × divisor_y} 网格切成 count 个 sprite：
 * 单元尺寸 = 源图尺寸 / divisor（向下取整）；第 i 个单元的行列 =
 * {@code row = base_row + i / divisor_x}、{@code col = base_column + i % divisor_x}
 * （row-major）。sprite id = {@code <resource>_<i>}，像素裁剪经
 * {@link PixelTransform} 在解码期施加。
 * <p>
 * 定义 JSON 示例（把 64×64 精灵表切成 16 个 16×16）：
 * <pre>{@code {"type": "minecraft:unstitch", "resource": "minecraft:item/sheet",
 * "divisor_x": 4, "divisor_y": 4, "base_row": 0, "base_column": 0, "count": 16}}</pre>
 *
 * <p>Splits a spritesheet into a divisor grid of individual sprites, each
 * cropped at decode time via a pixel transform.
 */
@SideOnly(Side.CLIENT)
public final class UnstitchSource implements AtlasSource {

    private final ResourceLocation resource;
    private final int divisorX;
    private final int divisorY;
    private final int baseRow;
    private final int baseColumn;
    private final int count;

    public UnstitchSource(ResourceLocation resource, int divisorX, int divisorY,
                          int baseRow, int baseColumn, int count) {
        this.resource = resource;
        this.divisorX = Math.max(1, divisorX);
        this.divisorY = Math.max(1, divisorY);
        this.baseRow = baseRow;
        this.baseColumn = baseColumn;
        this.count = Math.max(0, count);
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
        List<SpriteRef> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final int row = baseRow + i / divisorX;
            final int col = baseColumn + i % divisorX;
            ResourceLocation spriteId =
                    new ResourceLocation(resource.getResourceDomain(), resource.getResourcePath() + "_" + i);
            out.add(SpriteRef.of(spriteId, resource, null, new PixelTransform() {
                @Override
                public Result apply(int[] src, int srcWidth, int srcHeight) {
                    int cellW = Math.max(1, srcWidth / divisorX);
                    int cellH = Math.max(1, srcHeight / divisorY);
                    int srcX = col * cellW;
                    int srcY = row * cellH;
                    // 越界单元：输出透明占位（不崩溃，与 26.1.2 语义一致）
                    if (srcX >= srcWidth || srcY >= srcHeight) {
                        return new Result(new int[cellW * cellH], cellW, cellH);
                    }
                    int w = Math.min(cellW, srcWidth - srcX);
                    int h = Math.min(cellH, srcHeight - srcY);
                    int[] px = new int[w * h];
                    for (int y = 0; y < h; y++) {
                        System.arraycopy(src, (srcY + y) * srcWidth + srcX, px, y * w, w);
                    }
                    return new Result(px, w, h);
                }
            }));
        }
        if (count > 0) {
            CatFrame.logger.debug("[UnstitchSource] '{}' -> {} sprites ({}x{} grid)",
                    resource, count, divisorX, divisorY);
        }
        return out;
    }

    @Override
    public String toString() {
        return "UnstitchSource{" + resource + " " + divisorX + "x" + divisorY + " x" + count + "}";
    }
}
