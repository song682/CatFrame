package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import javax.vecmath.Vector3d;
import java.util.List;

/**
 * GUI 模型边界工具 — 测量一个 {@link BlockStateModelPart} 的几何是否超出 16x16 槽位。
 * <p>
 * 槽位在块空间中对应 {@code [0,1]^3}（1.0 == 16px）。模型几何完全落在该盒内时视为
 * "不超大"；否则（考虑 {@code min<0} / {@code max>1} 的双向偏移）计算需要装入槽位的
 * 总跨度作为 {@code maxExtent}，供 {@link decok.dfcdvadstf.catframe.exception.WrongUIOverScaleException}
 * 的捕获方按 {@code 1/maxExtent} 钳制回槽位。
 */
public final class GuiModelBounds {

    /**
     * 溢出判定容差：{@code maxExtent > 1.0 + OVERSIZE_TOLERANCE} 才判为超大，
     * 避免槽位边缘的浮点误差误报（阈值默认 1.05）。
     */
    public static final float OVERSIZE_TOLERANCE = 0.05f;

    private GuiModelBounds() {
    }

    /**
     * 计算模型在块空间中相对槽位 {@code [0,1]} 的最大轴向跨度。
     * <p>
     * 每个轴取 {@code max(maxCoord, 1.0) - min(minCoord, 0.0)}（把 &lt;0 与 &gt;1 的溢出计入），
     * 三轴取最大值。模型完全落在槽位内时返回 {@code 1.0}。空模型返回 {@code 1.0}。
     */
    public static float computeMaxExtent(BlockStateModelPart part) {
        if (part == null) return 1.0f;
        List<BakedQuad> quads = part.getAllQuads();
        if (quads == null || quads.isEmpty()) return 1.0f;

        double minX = 0.0, minY = 0.0, minZ = 0.0;
        double maxX = 1.0, maxY = 1.0, maxZ = 1.0;
        for (BakedQuad quad : quads) {
            if (quad == null || quad.vertices == null) continue;
            for (Vector3d v : quad.vertices) {
                if (v == null) continue;
                if (v.x < minX) minX = v.x;
                if (v.y < minY) minY = v.y;
                if (v.z < minZ) minZ = v.z;
                if (v.x > maxX) maxX = v.x;
                if (v.y > maxY) maxY = v.y;
                if (v.z > maxZ) maxZ = v.z;
            }
        }

        double spanX = maxX - minX;
        double spanY = maxY - minY;
        double spanZ = maxZ - minZ;
        double max = Math.max(spanX, Math.max(spanY, spanZ));
        return (float) max;
    }

    /**
     * 模型几何是否超出槽位（带容差）。
     */
    public static boolean isOversized(BlockStateModelPart part) {
        return computeMaxExtent(part) > 1.0f + OVERSIZE_TOLERANCE;
    }
}
