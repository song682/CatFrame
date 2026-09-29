package decok.dfcdvadstf.catframe.core.component;

import decok.dfcdvadstf.catframe.core.component.predicates.TypedDataComponent;

import javax.annotation.Nullable;

/**
 * Read-only component container interface.
 * <p>
 * Reference: 26.1.2 {@code net.minecraft.core.component.DataComponentGetter}.
 */
public interface DataComponentGetter {

    /**
     * Gets the component value of the specified type.
     *
     * @return component value, or null if not present
     */
    @Nullable
    <T> T get(DataComponentType<? extends T> type);

    /**
     * Gets the component value of the specified type, returning default value if not present.
     */
    default <T> T getOrDefault(DataComponentType<? extends T> type, T defaultValue) {
        T value = this.get(type);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets the typed component value carrying type information.
     */
    @Nullable
    default <T> TypedDataComponent<T> getTyped(DataComponentType<T> type) {
        T value = this.get(type);
        return value != null ? new TypedDataComponent<>(type, value) : null;
    }
}
