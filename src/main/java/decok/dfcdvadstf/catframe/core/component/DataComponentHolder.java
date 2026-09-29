package decok.dfcdvadstf.catframe.core.component;

import javax.annotation.Nullable;

/**
 * Writable component container interface.
 * <p>
 * Reference 26.1.2 {@code net.minecraft.core.component.DataComponentHolder}.
 */
public interface DataComponentHolder extends DataComponentGetter {

    /**
     * Set the component value of the specified type.
     *
     * @param <T>   value type
     * @param type  component type
     * @param value value (null to remove component)
     * @return previous value, or null if not present
     */
    @Nullable
    <T> T set(DataComponentType<T> type, @Nullable T value);

    /**
     * Remove the component of the specified type.
     *
     * @return previous value, or null if not present
     */
    @Nullable
    @SuppressWarnings("unchecked")
    default <T> T remove(DataComponentType<? extends T> type) {
        return this.set((DataComponentType<T>) type, null);
    }

    /**
     * Check if a component of the specified type is present.
     */
    default boolean has(DataComponentType<?> type) {
        return this.get(type) != null;
    }
}