package decok.dfcdvadstf.catframe.core;

import javax.vecmath.Vector3d;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Six-face direction enum — aligned with modern {@code net.minecraft.core.Direction},
 * removing dependency on Forge {@code EnumFacing}. Primarily adapted for modern model
 * loading driven by vectors.
 *
 * <p>Each enum value precomputes its normal vector (int step + {@link Vector3d})
 * and provides core methods for opposite, clockwise rotation, lookup by name,
 * nearest direction, etc.
 */
public enum Direction {
    DOWN(0, 1, -1, "down",  AxisDirection.NEGATIVE, Axis.Y,  0, -1,  0),
    UP  (1, 0, -1, "up",    AxisDirection.POSITIVE, Axis.Y,  0,  1,  0),
    NORTH(2, 3, 2, "north", AxisDirection.NEGATIVE, Axis.Z,  0,  0, -1),
    SOUTH(3, 2, 0, "south", AxisDirection.POSITIVE, Axis.Z,  0,  0,  1),
    WEST (4, 5, 1, "west",  AxisDirection.NEGATIVE, Axis.X, -1,  0,  0),
    EAST (5, 4, 3, "east",  AxisDirection.POSITIVE, Axis.X,  1,  0,  0);

    // ==================== Static lookup tables ====================

    private static final Direction[] VALUES = values();
    private static final Direction[] BY_3D_DATA = new Direction[6];
    private static final Direction[] BY_2D_DATA = new Direction[4];

    static {
        for (Direction d : VALUES) {
            BY_3D_DATA[d.data3d] = d;
        }
        for (Direction d : VALUES) {
            if (d.axis.isHorizontal()) {
                BY_2D_DATA[d.data2d] = d;
            }
        }
    }

    // ==================== Instance fields ====================

    private final int data3d;
    private final int oppositeIndex;
    private final int data2d;
    private final String name;
    private final Axis axis;
    private final AxisDirection axisDirection;
    private final int normalX;
    private final int normalY;
    private final int normalZ;
    /** Precomputed double-precision normal vector (VecMath). */
    private final Vector3d normalVec3d;

    Direction(int data3d, int oppositeIndex, int data2d, String name,
              AxisDirection axisDirection, Axis axis,
              int nx, int ny, int nz) {
        this.data3d = data3d;
        this.oppositeIndex = oppositeIndex;
        this.data2d = data2d;
        this.name = name;
        this.axisDirection = axisDirection;
        this.axis = axis;
        this.normalX = nx;
        this.normalY = ny;
        this.normalZ = nz;
        this.normalVec3d = new Vector3d(nx, ny, nz);
    }

    // ==================== Basic accessors ====================

    public int get3DDataValue() { return data3d; }
    public int get2DDataValue() { return data2d; }
    public String getName()     { return name; }
    public Axis getAxis()       { return axis; }
    public AxisDirection getAxisDirection() { return axisDirection; }

    public int getStepX() { return normalX; }
    public int getStepY() { return normalY; }
    public int getStepZ() { return normalZ; }

    /** Returns an immutable Vector3d copy of the normal vector. */
    public Vector3d getNormalVec3d() { return new Vector3d(normalVec3d); }

    /** Returns a reference to the precomputed normal vector (caller must not modify). */
    public Vector3d getNormalVec3dRef() { return normalVec3d; }

    @Override
    public String toString() { return name; }

    // ==================== Direction operations ====================

    /** Opposite direction (O(1)). */
    public Direction getOpposite() {
        return BY_3D_DATA[oppositeIndex];
    }

    /** Rotate 90° clockwise around Y axis (horizontal directions only). */
    public Direction getClockWise() {
        switch (this) {
            case NORTH: return EAST;
            case SOUTH: return WEST;
            case WEST:  return NORTH;
            case EAST:  return SOUTH;
            default: throw new IllegalStateException("Unable to get Y-rotated facing of " + this);
        }
    }

    /** Rotate 90° counter-clockwise around Y axis (horizontal directions only). */
    public Direction getCounterClockWise() {
        switch (this) {
            case NORTH: return WEST;
            case SOUTH: return EAST;
            case WEST:  return SOUTH;
            case EAST:  return NORTH;
            default: throw new IllegalStateException("Unable to get CCW facing of " + this);
        }
    }

    /** Rotate 90° clockwise around given axis. */
    public Direction getClockWise(Axis axis) {
        switch (axis) {
            case X: return this != WEST && this != EAST ? this.getClockWiseX() : this;
            case Y: return this != UP && this != DOWN ? this.getClockWise() : this;
            case Z: return this != NORTH && this != SOUTH ? this.getClockWiseZ() : this;
            default: return this;
        }
    }

    /** Rotate 90° counter-clockwise around given axis. */
    public Direction getCounterClockWise(Axis axis) {
        switch (axis) {
            case X: return this != WEST && this != EAST ? this.getCounterClockWiseX() : this;
            case Y: return this != UP && this != DOWN ? this.getCounterClockWise() : this;
            case Z: return this != NORTH && this != SOUTH ? this.getCounterClockWiseZ() : this;
            default: return this;
        }
    }

    private Direction getClockWiseX() {
        switch (this) {
            case DOWN:  return SOUTH;
            case UP:    return NORTH;
            case NORTH: return DOWN;
            case SOUTH: return UP;
            default: throw new IllegalStateException("Unable to get X-rotated facing of " + this);
        }
    }

    private Direction getCounterClockWiseX() {
        switch (this) {
            case DOWN:  return NORTH;
            case UP:    return SOUTH;
            case NORTH: return UP;
            case SOUTH: return DOWN;
            default: throw new IllegalStateException("Unable to get X-rotated facing of " + this);
        }
    }

    private Direction getClockWiseZ() {
        switch (this) {
            case DOWN:  return WEST;
            case UP:    return EAST;
            case WEST:  return UP;
            case EAST:  return DOWN;
            default: throw new IllegalStateException("Unable to get Z-rotated facing of " + this);
        }
    }

    private Direction getCounterClockWiseZ() {
        switch (this) {
            case DOWN:  return EAST;
            case UP:    return WEST;
            case WEST:  return DOWN;
            case EAST:  return UP;
            default: throw new IllegalStateException("Unable to get Z-rotated facing of " + this);
        }
    }

    // ==================== Static factory methods ====================

    /** Lookup by name (e.g. "north", "up"), returns null if not found. */
    public static Direction byName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Direction d : VALUES) {
            if (d.name.equals(name)) return d;
        }
        return null;
    }

    /** Lookup by 3D data value. */
    public static Direction from3DDataValue(int data) {
        return BY_3D_DATA[Math.abs(data % BY_3D_DATA.length)];
    }

    /** Lookup by 2D data value (horizontal directions only). */
    public static Direction from2DDataValue(int data) {
        return BY_2D_DATA[Math.abs(data % BY_2D_DATA.length)];
    }

    /** Gets direction from axis + axis direction combination. */
    public static Direction fromAxisAndDirection(Axis axis, AxisDirection axisDirection) {
        for (Direction d : VALUES) {
            if (d.axis == axis && d.axisDirection == axisDirection) return d;
        }
        throw new IllegalArgumentException("No such direction: " + axisDirection + " " + axis);
    }

    /** Determines nearest direction from normal vector components (dot product comparison). */
    public static Direction getApproximateNearest(double dx, double dy, double dz) {
        Direction result = NORTH;
        double highestDot = -Double.MAX_VALUE;
        for (Direction d : VALUES) {
            double dot = dx * d.normalX + dy * d.normalY + dz * d.normalZ;
            if (dot > highestDot) {
                highestDot = dot;
                result = d;
            }
        }
        return result;
    }

    /** Determines nearest direction from normal vector components (float overload). */
    public static Direction getApproximateNearest(float dx, float dy, float dz) {
        return getApproximateNearest((double) dx, dy, dz);
    }

    /** Determines nearest direction from integer components (largest absolute component wins). */
    public static Direction getNearest(int x, int y, int z) {
        int absX = Math.abs(x), absY = Math.abs(y), absZ = Math.abs(z);
        if (absX > absZ && absX > absY) return x < 0 ? WEST : EAST;
        if (absZ > absX && absZ > absY) return z < 0 ? NORTH : SOUTH;
        if (absY > absX && absY > absZ) return y < 0 ? DOWN : UP;
        return NORTH;
    }

    // ==================== Inner enum: Axis ====================

    /** Three-axis enum. */
    public enum Axis {
        X("x") {
            @Override public int choose(int x, int y, int z) { return x; }
            @Override public double choose(double x, double y, double z) { return x; }
            @Override public Direction getPositive() { return EAST; }
            @Override public Direction getNegative() { return WEST; }
        },
        Y("y") {
            @Override public int choose(int x, int y, int z) { return y; }
            @Override public double choose(double x, double y, double z) { return y; }
            @Override public Direction getPositive() { return UP; }
            @Override public Direction getNegative() { return DOWN; }
        },
        Z("z") {
            @Override public int choose(int x, int y, int z) { return z; }
            @Override public double choose(double x, double y, double z) { return z; }
            @Override public Direction getPositive() { return SOUTH; }
            @Override public Direction getNegative() { return NORTH; }
        };

        private final String name;

        Axis(String name) { this.name = name; }

        public String getName() { return name; }
        public boolean isHorizontal() { return this == X || this == Z; }
        public boolean isVertical() { return this == Y; }

        public abstract Direction getPositive();
        public abstract Direction getNegative();
        public abstract int choose(int x, int y, int z);
        public abstract double choose(double x, double y, double z);

        public Direction[] getDirections() {
            return new Direction[]{ getPositive(), getNegative() };
        }

        @Override
        public String toString() { return name; }
    }

    // ==================== Inner enum: AxisDirection ====================

    /** Axis positive/negative direction. */
    public enum AxisDirection {
        POSITIVE(1, "Towards positive"),
        NEGATIVE(-1, "Towards negative");

        private final int step;
        private final String name;

        AxisDirection(int step, String name) {
            this.step = step;
            this.name = name;
        }

        public int getStep() { return step; }
        public String getName() { return name; }

        public AxisDirection opposite() {
            return this == POSITIVE ? NEGATIVE : POSITIVE;
        }

        @Override
        public String toString() { return name; }
    }

    // ==================== Inner enum: Plane ====================

    /** Horizontal/vertical plane grouping. */
    public enum Plane implements Iterable<Direction> {
        HORIZONTAL(new Direction[]{ NORTH, EAST, SOUTH, WEST }, new Axis[]{ Axis.X, Axis.Z }),
        VERTICAL  (new Direction[]{ UP, DOWN },                new Axis[]{ Axis.Y });

        private final Direction[] faces;
        private final Axis[] axis;

        Plane(Direction[] faces, Axis[] axis) {
            this.faces = faces;
            this.axis = axis;
        }

        public Direction getRandomDirection(java.util.Random random) {
            return faces[random.nextInt(faces.length)];
        }

        public Axis getRandomAxis(java.util.Random random) {
            return axis[random.nextInt(axis.length)];
        }

        @Override
        public Iterator<Direction> iterator() {
            return new Iterator<Direction>() {
                private int idx = 0;
                @Override public boolean hasNext() { return idx < faces.length; }
                @Override public Direction next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return faces[idx++];
                }
            };
        }

        public int length() { return faces.length; }
    }
}
