package decok.dfcdvadstf.catframe.core.component.predicates;

import decok.dfcdvadstf.catframe.core.component.DataComponentHolder;
import decok.dfcdvadstf.catframe.core.component.DataComponentType;

import java.util.Map;
import java.util.Objects;

/**
 * A typed component-value pair.
 * <p>
 * References 26.1.2 {@code net.minecraft.core.component.TypedDataComponent}.
 *
 * @param <T> the type of the value
 */
public final class TypedDataComponent<T> {

    private final DataComponentType<T> type;
    private final T value;

    public TypedDataComponent(DataComponentType<T> type, T value) {
        this.type = Objects.requireNonNull(type, "type");
        this.value = Objects.requireNonNull(value, "value");
    }

    public DataComponentType<T> getType() {
        return type;
    }

    public T getValue() {
        return value;
    }

    /**
     * Applies this component to a writable holder.
     */
    public void applyTo(DataComponentHolder holder) {
        holder.set(type, value);
    }

    // ========== Factory methods ==========

    /**
     * Creates an instance from a Map.Entry (used for internal iteration).
     */
    @SuppressWarnings("unchecked")
    public static <T> TypedDataComponent<T> fromEntry(Map.Entry<DataComponentType<?>, Object> entry) {
        return new TypedDataComponent<>(
                (DataComponentType<T>) entry.getKey(),
                (T) entry.getValue()
        );
    }

    @SuppressWarnings("unchecked")
    public static <T> TypedDataComponent<T> unchecked(DataComponentType<T> type, Object value) {
        return new TypedDataComponent<>(type, (T) value);
    }

    // ========== Object contract ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypedDataComponent)) return false;
        TypedDataComponent<?> that = (TypedDataComponent<?>) o;
        return type.equals(that.type) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + value.hashCode();
    }

    @Override
    public String toString() {
        return type + "=>" + value;
    }
}
