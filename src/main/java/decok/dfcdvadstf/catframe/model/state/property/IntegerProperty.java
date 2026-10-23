package decok.dfcdvadstf.catframe.model.state.property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Integer range property. The value domain is all integers from min to max (inclusive).
 *
 * <p>Internal index: {@code value - min}, i.e. arranged in ascending order.
 *
 * <p>Usage:
 * <pre>{@code
 * public static final Property<Integer> BITES = IntegerProperty.create("bites", 0, 6);
 * }</pre>
 */
public final class IntegerProperty extends Property<Integer> {

    private final int min;
    private final int max;

    private IntegerProperty(String name, int min, int max) {
        super(name, Integer.class, createValues(min, max));
        this.min = min;
        this.max = max;
    }

    @Override
    public int getInternalIndex(Integer value) {
        int idx = value - min;
        if (idx < 0 || idx > max - min) {
            throw new IllegalArgumentException("Value " + value + " is out of range [" + min + ", " + max + "]");
        }
        return idx;
    }

    @Override
    public String getName(Integer value) {
        return value.toString();
    }

    @Override
    public Integer getValueByName(String valueName) {
        if (valueName == null) return null;
        try {
            int v = Integer.parseInt(valueName);
            return (v >= min && v <= max) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns the minimum value (inclusive).
     */
    public int getMin() {
        return min;
    }

    /**
     * Returns the maximum value (inclusive).
     */
    public int getMax() {
        return max;
    }

    /**
     * Creates an integer range property.
     *
     * @param name the property name (e.g. "level", "bites")
     * @param min  the minimum value (inclusive)
     * @param max  the maximum value (inclusive)
     * @return a new IntegerProperty instance
     * @throws IllegalArgumentException if min > max
     */
    public static IntegerProperty create(String name, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("Min must be <= max for IntegerProperty '" + name + "'");
        }
        return new IntegerProperty(name, min, max);
    }

    private static List<Integer> createValues(int min, int max) {
        List<Integer> values = new ArrayList<>();
        for (int i = min; i <= max; i++) {
            values.add(i);
        }
        return Collections.unmodifiableList(values);
    }
}
