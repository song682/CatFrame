package decok.dfcdvadstf.catframe.model.render.extension.ao.light;

import decok.dfcdvadstf.catframe.core.Direction;

/**
 * Directional brightness table — defines the directional lighting coefficient each face receives.
 *
 * <p>Aligned with 26.1.2 {@code net.minecraft.world.level.CardinalLighting}.
 * Replaces the hardcoded 3-level shading of the former {@code UniformRenderPipeline.shadeByNormal()}.
 *
 * <p>Each set of values represents the brightness coefficient (0.0~1.0) for the 6 directions, applied as a multiplier on the final vertex color:
 * <pre>finalColor *= cardinalLighting.byFace(face)</pre>
 *
 * <p>Supports differentiated lighting for different dimensions:
 * <ul>
 *   <li>{@link #DEFAULT} — overworld/end standard lighting (aligned with vanilla 1.7.10 RenderBlocks)</li>
 *   <li>{@link #NETHER} — nether lighting (top/bottom faces uniform, no directional difference)</li>
 * </ul>
 */
public final class CardinalLighting {

    /** Overworld/end standard lighting (aligned with vanilla 1.7.10 shade values). */
    public static final CardinalLighting DEFAULT =
            new CardinalLighting(0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F);

    /** Nether lighting (top/bottom faces uniform at 0.9; side faces match DEFAULT). */
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
     * Gets the brightness coefficient for a face direction.
     *
     * @param face the face direction (Direction)
     * @return the lighting coefficient for that direction (0.0~1.0)
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
