package decok.dfcdvadstf.catframe.model.state.property;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Enum property. The value domain is a subset of an enum type.
 *
 * <p>The internal index is mapped through {@code ordinalToIndex[]}: {@code Enum.ordinal() → internal index}.
 *
 * <p>Usage (all enum values):
 * <pre>{@code
 * public enum SlabType { BOTTOM, TOP, DOUBLE }
 * public static final Property<SlabType> TYPE = EnumProperty.create("type", SlabType.class);
 * }</pre>
 *
 * <p>Usage (filtered subset):
 * <pre>{@code
 * public static final Property<Direction> FACING_HORIZONTAL =
 *     EnumProperty.create("facing", Direction.class, Direction.NORTH, Direction.SOUTH, ...);
 * }</pre>
 *
 * @param <T> the enum type
 */
public final class EnumProperty<T extends Enum<T>> extends Property<T> {

    private final int[] ordinalToIndex;

    private EnumProperty(String name, Class<T> clazz, List<T> values) {
        super(name, clazz, values);
        this.ordinalToIndex = new int[clazz.getEnumConstants().length];
        Arrays.fill(this.ordinalToIndex, -1);
        for (int i = 0; i < values.size(); i++) {
            this.ordinalToIndex[values.get(i).ordinal()] = i;
        }
    }

    @Override
    public int getInternalIndex(T value) {
        int idx = ordinalToIndex[value.ordinal()];
        if (idx < 0) {
            throw new IllegalArgumentException("Value " + value + " is not in this property filter");
        }
        return idx;
    }

    /**
     * Creates an enum property containing all values of the enum.
     *
     * @param name  the property name
     * @param clazz the enum class
     * @param <T>   the enum type
     * @return a new EnumProperty instance
     */
    public static <T extends Enum<T>> EnumProperty<T> create(String name, Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        return new EnumProperty<>(name, clazz, Collections.unmodifiableList(Arrays.asList(constants)));
    }

    /**
     * Creates an enum property containing only the specified subset of enum values.
     *
     * @param name   the property name
     * @param clazz  the enum class
     * @param filter the enum values to include
     * @param <T>    the enum type
     * @return a new EnumProperty instance
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T extends Enum<T>> EnumProperty<T> create(String name, Class<T> clazz, T... filter) {
        return new EnumProperty<>(name, clazz, Collections.unmodifiableList(Arrays.asList(filter)));
    }
}
