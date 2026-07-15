package decok.dfcdvadstf.catframe.model.render.extension.ao.light;

import decok.dfcdvadstf.catframe.core.Direction;

/**
 * 方向亮度表 — 定义每个面接收的方向光照系数。
 *
 * <p>对齐 26.1.2 {@code net.minecraft.world.level.CardinalLighting}。
 * 用于替代原 {@code UniformRenderPipeline.shadeByNormal()} 的硬编码 3 档阴影。
 *
 * <p>每组值代表 6 个方向的亮度系数（0.0~1.0），在最终顶点颜色上作为乘数应用：
 * <pre>finalColor *= cardinalLighting.byFace(face)</pre>
 *
 * <p>支持不同维度的差异化光照：
 * <ul>
 *   <li>{@link #DEFAULT} — 主世界/末地标准光照（对齐 1.7.10 原版 RenderBlocks）</li>
 *   <li>{@link #NETHER} — 下界光照（顶底面均匀，无方向差异）</li>
 * </ul>
 */
public final class CardinalLighting {

    /** 主世界/末地标准光照（对齐 1.7.10 原版 shade 值）。 */
    public static final CardinalLighting DEFAULT =
            new CardinalLighting(0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F);

    /** 下界光照（顶底面均匀 0.9，侧面与 DEFAULT 一致）。 */
    public static final CardinalLighting NETHER =
            new CardinalLighting(0.9F, 0.9F, 0.8F, 0.8F, 0.6F, 0.6F);

    private final float down;
    private final float up;
    private final float north;
    private final float south;
    private final float west;
    private final float east;

    public CardinalLighting(float down, float up, float north, float south, float west, float east) {
        this.down = down;
        this.up = up;
        this.north = north;
        this.south = south;
        this.west = west;
        this.east = east;
    }

    /**
     * 按面方向获取亮度系数。
     *
     * @param face 面方向（Direction）
     * @return 该方向的光照系数（0.0~1.0）
     */
    public float byFace(Direction face) {
        if (face == null) return up;
        switch (face) {
            case DOWN:  return down;
            case UP:    return up;
            case NORTH: return north;
            case SOUTH: return south;
            case WEST:  return west;
            case EAST:  return east;
            default:    return up;
        }
    }

    public float down()  { return down; }
    public float up()    { return up; }
    public float north() { return north; }
    public float south() { return south; }
    public float west()  { return west; }
    public float east()  { return east; }

    @Override
    public String toString() {
        return "CardinalLighting{d=" + down + ", u=" + up
                + ", n=" + north + ", s=" + south
                + ", w=" + west + ", e=" + east + "}";
    }
}
