package decok.dfcdvadstf.catframe.core.component;

import decok.dfcdvadstf.catframe.core.component.predicates.TypedDataComponent;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Component patch - a set of component deltas relative to the prototype.
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.core.component.DataComponentPatch}.
 * <p>
 * A patch is a {@code Map<DataComponentType<?>, Optional<?>>}, where
 * {@code Optional.of(value)} sets a component and {@code Optional.empty()} removes it.
 */
public final class DataComponentPatch {

    /** Empty patch constant */
    public static final DataComponentPatch EMPTY = new DataComponentPatch(Collections.emptyMap());

    private final Map<DataComponentType<?>, Optional<?>> map;

    /**
     * Constructor used internally, called by PatchedDataComponentMap and Builder.
     */
    public DataComponentPatch(Map<DataComponentType<?>, Optional<?>> map) {
        this.map = map;
    }

    // ========== Factory methods ==========

    public static Builder builder() {
        return new Builder();
    }

    // ========== Queries ==========

    /**
     * Checks whether the patch is empty.
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Returns the size of the patch.
     */
    public int size() {
        return map.size();
    }

    /**
     * Gets a value from the patch (with prototype fallback).
     *
     * @param prototype the prototype map
     * @param type      the component type
     * @return the patch value when present, otherwise the prototype value
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(DataComponentGetter prototype, DataComponentType<? extends T> type) {
        Optional<?> value = map.get(type);
        if (value != null) {
            return (T) value.orElse(null);
        }
        return prototype.get(type);
    }

    /**
     * Returns the patch's raw map (internal use only).
     */
    public Map<DataComponentType<?>, Optional<?>> getRawMap() {
        return map;
    }

    /**
     * Returns the set of all entries.
     */
    public Set<Map.Entry<DataComponentType<?>, Optional<?>>> entrySet() {
        return map.entrySet();
    }

    /**
     * Splits the patch into added and removed parts.
     */
    public SplitResult split() {
        if (isEmpty()) {
            return SplitResult.EMPTY;
        }
        DataComponentMap.Builder added = DataComponentMap.builder();
        Set<DataComponentType<?>> removed = new HashSet<>();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : map.entrySet()) {
            Optional<?> value = entry.getValue();
            if (value != null && value.isPresent()) {
                @SuppressWarnings("unchecked")
                DataComponentType<Object> type = (DataComponentType<Object>) entry.getKey();
                added.set(type, value.get());
            } else {
                removed.add(entry.getKey());
            }
        }
        return new SplitResult(added.build(), removed);
    }

    // ========== Object contract ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DataComponentPatch)) return false;
        DataComponentPatch that = (DataComponentPatch) o;
        return map.equals(that.map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : map.entrySet()) {
            if (first) first = false;
            else sb.append(", ");
            Optional<?> value = entry.getValue();
            if (value != null && value.isPresent()) {
                sb.append(entry.getKey()).append("=>").append(value.get());
            } else {
                sb.append("!").append(entry.getKey());
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // ========== Builder ==========

    public static final class Builder {
        private final Map<DataComponentType<?>, Optional<?>> map = new IdentityHashMap<>();

        private Builder() {}

        public <T> Builder set(DataComponentType<T> type, T value) {
            map.put(type, Optional.of(value));
            return this;
        }

        public <T> Builder remove(DataComponentType<T> type) {
            map.put(type, Optional.empty());
            return this;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public Builder add(TypedDataComponent<?> component) {
            map.put(component.getType(), Optional.of(component.getValue()));
            return this;
        }

        public DataComponentPatch build() {
            return map.isEmpty() ? EMPTY : new DataComponentPatch(new IdentityHashMap<>(map));
        }
    }

    // ========== Split result ==========

    public static final class SplitResult {
        public static final SplitResult EMPTY = new SplitResult(DataComponentMap.EMPTY, Collections.emptySet());

        private final DataComponentMap added;
        private final Set<DataComponentType<?>> removed;

        public SplitResult(DataComponentMap added, Set<DataComponentType<?>> removed) {
            this.added = added;
            this.removed = removed;
        }

        public DataComponentMap getAdded() {
            return added;
        }

        public Set<DataComponentType<?>> getRemoved() {
            return removed;
        }
    }
}
