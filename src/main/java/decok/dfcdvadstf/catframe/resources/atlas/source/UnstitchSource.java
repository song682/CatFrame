package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Spritesheet splitting source (mirrors 26.1.2 {@code minecraft:unstitch}, matching
 * the Wiki format).
 * <p>
 * Slices a large image into a {@code divisor_x × divisor_y} grid and crops one
 * region per entry of the {@code regions} list as a sprite: the region
 * x/y/width/height are <b>tile coordinates</b>, converted to pixel coordinates by
 * the Wiki formula — {@code px = ⌊x·srcW/divisor_x⌋},
 * {@code pw = ⌊width·srcW/divisor_x⌋} (likewise for y/height). The sprite id is
 * explicitly given by each region's {@code sprite} field (the legacy
 * base_row/base_column/count format is no longer used).
 * <p>
 * Definition JSON example (slice a 64×64 spritesheet into 16 tiles of 16×16,
 * taking the first two):
 * <pre>{@code {"type": "minecraft:unstitch", "resource": "minecraft:item/sheet",
 * "divisor_x": 4, "divisor_y": 4,
 * "regions": [
 *   {"sprite": "minecraft:item/sheet_0", "x": 0, "y": 0, "width": 1, "height": 1},
 *   {"sprite": "minecraft:item/sheet_1", "x": 1, "y": 0, "width": 1, "height": 1}
 * ]}}</pre>
 * Out-of-range regions (origin beyond the source image) yield transparent
 * placeholders instead of crashing; pixel cropping is applied at decode time via
 * a {@link PixelTransform}.
 */
@SideOnly(Side.CLIENT)
public final class UnstitchSource implements AtlasSource {

    /** One crop region (tile coordinates; Wiki semantics: pixels = ⌊tile×srcSize/divisor⌋). */
    public static final class Region {
        /** Namespace id of the sprite generated from this region. */
        public final ResourceLocation sprite;
        /** Top-left X (tile coordinates). */
        public final int x;
        /** Top-left Y (tile coordinates). */
        public final int y;
        /** Region width (tile coordinates). */
        public final int width;
        /** Region height (tile coordinates). */
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
                    // Wiki formula: pixel coord = floor(tile coord × srcSize / divisor) (integer division is floor for positives in Java)
                    int px = r.x * srcWidth / divisorX;
                    int py = r.y * srcHeight / divisorY;
                    int pw = Math.max(1, r.width * srcWidth / divisorX);
                    int ph = Math.max(1, r.height * srcHeight / divisorY);
                    // Out-of-range region: emit a transparent placeholder (no crash, matching 26.1.2 semantics)
                    if (px >= srcWidth || py >= srcHeight) {
                        return new Result(new int[pw * ph], pw, ph);
                    }
                    // Edge region: narrow width/height to the source image bounds
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
