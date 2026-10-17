package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.model.state.BlockState;

import java.util.*;

/**
 * Type-safe blockstate property base class. Similar to 1.21.5's {@code Property<T>}.
 *
 * <p>Each property has a unique name, a value type, and an immutable list of all possible values.
 * {@link #getInternalIndex} maps a value to its internal index, used for {@link BlockState}'s
 * neighbor jump table.
 *
 * <p>Predefined subclasses:
 * <ul>
 *   <li>{@link BooleanProperty} — boolean value (true / false)</li>
 *   <li>{@link IntegerProperty} — integer range (min ~ max)</li>
 *   <li>{@link EnumProperty} — enum value</li>
 * </ul>
 *
 * @param <T> the value type, must be comparable
 */
public abstract class Property<T extends Comparable<T>> {

    private final String name;
    private final Class<T> valueClass;
    private final List<T> values;
    private final Map<T, Integer> indexMap;

    /**
     * @param name       the property name (e.g. "facing", "variant")
     * @param valueClass the value type
     * @param values     the list of all possible values (order determines the internal index)
     */
    protected Property(String name, Class<T> valueClass, List<T> values) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Property name must not be null or empty");
        }
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Property must have at least one value");
        }
        this.name = name;
        this.valueClass = valueClass;
        this.values = Collections.unmodifiableList(new ArrayList<>(values));

        // Build the value → index mapping
        this.indexMap = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            this.indexMap.put(values.get(i), i);
        }
    }

    // ==================== Abstract methods ====================

    /**
     * Returns the internal index of the given value (used for neighbor jump table lookup).
     * The default implementation is based on the position in the values list; subclasses may
     * override to compute it directly in O(1).
     */
    public int getInternalIndex(T value) {
        Integer idx = indexMap.get(value);
        if (idx == null) {
            throw new IllegalArgumentException("Value " + value + " is not in property " + name);
        }
        return idx;
    }

    // ==================== Instance methods ====================

    public String getName() {
        return name;
    }

    public Class<T> getValueClass() {
        return valueClass;
    }

    /**
     * Returns the immutable list of all possible values. The list index positions are consistent
     * with {@link #getInternalIndex}.
     */
    public List<T> getValues() {
        return values;
    }

    /**
     * Returns the list of string representations of all values.
     */
    public List<String> getNames() {
        List<String> names = new ArrayList<>(values.size());
        for (T value : values) {
            names.add(value.toString());
        }
        return names;
    }

    /**
     * Returns the number of values of this property.
     */
    public int getValueCount() {
        return values.size();
    }

    // ==================== Static factory ====================

    /**
     * Creates a simple custom Property (used when there is no dedicated subclass).
     */
    public static <T extends Comparable<T>> Property<T> create(String name, Class<T> clazz, List<T> values) {
        return new SimpleProperty<>(name, clazz, values);
    }

    // ==================== Object ====================

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Property)) return false;
        Property<?> property = (Property<?>) o;
        return name.equals(property.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    // Internal implementation: generic Property
    private static class SimpleProperty<T extends Comparable<T>> extends Property<T> {
        SimpleProperty(String name, Class<T> clazz, List<T> values) {
            super(name, clazz, values);
        }
    }
}
