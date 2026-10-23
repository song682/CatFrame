package decok.dfcdvadstf.catframe.model.state;

import decok.dfcdvadstf.catframe.model.state.property.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blockstate holder. Similar to 1.21.5's {@code BlockState} (which internally extends {@code StateHolder}).
 *
 * <p>Each CatBlockState instance corresponds to a definite combination of property values,
 * pre-created as a singleton at build time via {@link BlockStateDefinition.Builder}. All runtime
 * state transitions are completed in O(1) through the neighbor jump table, with no new object allocation.
 *
 * <p><b>Neighbor jump table mechanism</b>:
 * <pre>
 *              neighbor[0] (TYPE dimension)
 *              ┌────────┬────────┬────────┐
 *              │ BOTTOM │  TOP   │ DOUBLE │
 * neighbor[1]  ┼────────┼────────┼────────┤
 * (WATERLOGGED)│ state0 │ state1 │ state2 │  ← true
 *              │ state3 │ state4 │ state5 │  ← false
 *              └────────┴────────┴────────┘
 *
 * state0.setValue(TYPE, TOP)    → state1  (neighbor[0][1])
 * state0.setValue(WATERLOGGED, true) → state3  (neighbor[1][0])
 * </pre>
 */
public final class BlockState {

    // [C2 fix] non-final; back-filled by BlockStateDefinition.Builder.create()
    BlockStateDefinition<?> definition;

    /**
     * The property value array of the current state. Indices correspond to positions in definition's properties array.
     */
    final Comparable<?>[] values; // package-private

    /**
     * Neighbor jump table. neighbors[p][v] is the target state reached by setting property p to value v (internal index).
     */
    BlockState[][] neighbors; // package-private

    // Called only by BlockStateDefinition.Builder.create()
    BlockState(BlockStateDefinition<?> definition, Comparable<?>[] values) {
        this.definition = definition;
        this.values = values;
    }

    // ==================== Query ====================

    /**
     * Gets the current value of the specified property.
     *
     * @param prop the property to query
     * @param <T>  the value type
     * @return the current property value
     */
    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> T getValue(Property<T> prop) {
        int idx = definition.getPropertyIndex(prop);
        return (T) values[idx];
    }

    /**
     * O(1) state transition: sets the specified property to a new value and returns the target
     * state through the neighbor jump table. The current state instance is not modified.
     *
     * @param prop  the property to set
     * @param value the new value
     * @param <T>   the value type
     * @return the target CatBlockState after the property is set
     */
    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> BlockState setValue(Property<T> prop, T value) {
        int propIdx = definition.getPropertyIndex(prop);
        int valIdx = prop.getInternalIndex(value);
        return neighbors[propIdx][valIdx];
    }

    /**
     * Returns the definition of this CatBlockState.
     */
    public BlockStateDefinition<?> getDefinition() {
        return definition;
    }

    // ==================== Variant key matching ====================

    /**
     * Generates the variant matching key (same format as the variant key in blockstate JSON).
     *
     * <p>For example, for property "bites=3" → {@code "bites=3"},
     * and for multiple properties "facing=north,half=upper" → {@code "facing=north,half=upper"} (after sorting).
     *
     * @return the variant key string. Returns "normal" when there are no properties.
     */
    public String toVariantKey() {
        Property<?>[] properties = definition.getProperties();
        if (properties.length == 0) return "normal";

        // Collect non-default property values
        BlockState anyState = definition.any();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < properties.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(properties[i].getName()).append('=').append(valueName(properties[i], values[i]));
        }
        return sb.toString();
    }

    /**
     * Returns the serialized variant-key form of a property value, delegating to the property's
     * {@link Property#getName(Comparable)} so enum-backed values emit their
     * {@link decok.dfcdvadstf.catframe.core.StringRepresentable} name instead of the raw
     * {@code toString()}. Uses the raw type to bridge the property's captured value type.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String valueName(Property prop, Comparable value) {
        return prop.getName(value);
    }

    /**
     * Checks whether this state matches the given {@code Map<String, String>} property mapping
     * (used for multipart when-condition matching). Every key-value pair in the mapping must match
     * the corresponding property value of this state. This state may have properties not present
     * in the mapping.
     *
     * @param properties the property mapping to match (key = property name, value = string representation of the property value)
     * @return true if all specified property values match
     */
    public boolean matches(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) return true;
        Property<?>[] props = definition.getProperties();
        for (int i = 0; i < props.length; i++) {
            String expected = properties.get(props[i].getName());
            if (expected != null) {
                if (!valueName(props[i], values[i]).equals(expected)) return false;
            }
        }
        return true;
    }

    /**
     * Checks whether this state matches the value condition of a single property (OR, pipe-separated).
     *
     * @param propertyName the property name
     * @param allowedValues pipe-separated allowed values (e.g. "north|south")
     * @return true if the current property value is among allowedValues
     */
    public boolean matchesProperty(String propertyName, String allowedValues) {
        Property<?>[] props = definition.getProperties();
        String[] allowed = allowedValues.split("\\|");
        for (int i = 0; i < props.length; i++) {
            if (props[i].getName().equals(propertyName)) {
                String current = valueName(props[i], values[i]);
                for (String a : allowed) {
                    if (a.equals(current)) return true;
                }
                return false;
            }
        }
        return false;
    }

    // ==================== Value access ====================

    /**
     * Gets the list of string representations of all property values in the current state,
     * in index order. Used for debugging and serialization.
     */
    public List<String> getValueNames() {
        Property<?>[] props = definition.getProperties();
        List<String> names = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            names.add(valueName(props[i], values[i]));
        }
        return names;
    }

    /**
     * Returns the number of property values in this state.
     */
    public int getValueCount() {
        return values.length;
    }

    // ==================== Object ====================

    @Override
    public String toString() {
        return "CatBlockState{" + toVariantKey() + "}";
    }

    @Override
    public int hashCode() {
        // Since all states are singletons, use the object reference directly
        return super.hashCode();
    }
}
