package decok.dfcdvadstf.catframe.core.component;

import decok.dfcdvadstf.catframe.core.component.predicates.TypedDataComponent;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Mutable component map using the prototype + patch delta model.
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.core.component.PatchedDataComponentMap}.
 * <p>
 * The prototype holds the default components shared by all items of the same type; the patch holds
 * the overrides of a single instance relative to that prototype.
 * When an instance value equals the prototype value it takes no space in the patch.
 */
public final class PatchedDataComponentMap implements DataComponentMap, DataComponentHolder {

    private final DataComponentMap prototype;
    private final Map<DataComponentType<?>, Optional<?>> patch;

    public PatchedDataComponentMap(DataComponentMap prototype) {
        this.prototype = prototype;
        this.patch = new IdentityHashMap<>();
    }

    // ========== Read operations ==========

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(DataComponentType<? extends T> type) {
        Optional<?> patchValue = patch.get(type);
        if (patchValue != null) {
            return (T) patchValue.orElse(null);
        }
        return prototype.get(type);
    }

    /**
     * Checks whether the component of the given type is overridden by the patch (differs from the
     * prototype).
     */
    public boolean hasNonDefault(DataComponentType<?> type) {
        return patch.containsKey(type);
    }

    // ========== Write operations ==========

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T set(DataComponentType<T> type, @Nullable T value) {
        T defaultValue = prototype.get(type);
        T oldValue = get(type);

        if (Objects.equals(value, defaultValue)) {
            // Value equals the default: drop it from the patch (restore the default)
            patch.remove(type);
        } else if (value != null) {
            // Value differs from the default: write it into the patch
            patch.put(type, Optional.of(value));
        } else {
            // Explicitly set to null
            if (defaultValue != null) {
                // The prototype has a value: mark it as removed
                patch.put(type, Optional.empty());
            } else {
                // The prototype has none either: nothing to record
                patch.remove(type);
            }
        }
        return oldValue;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T remove(DataComponentType<? extends T> type) {
        return this.set((DataComponentType<T>) type, null);
    }

    /**
     * Applies a patch.
     */
    public void applyPatch(DataComponentPatch patch) {
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            DataComponentType<?> type = entry.getKey();
            Optional<?> value = entry.getValue();
            applyPatchEntry(type, value);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void applyPatchEntry(DataComponentType<T> type, Optional<?> value) {
        T defaultValue = prototype.get(type);
        if (value.isPresent()) {
            if (Objects.equals(value.get(), defaultValue)) {
                patch.remove(type);
            } else {
                patch.put(type, value);
            }
        } else if (defaultValue != null) {
            patch.put(type, Optional.empty());
        } else {
            patch.remove(type);
        }
    }

    /**
     * Replaces the whole patch.
     */
    public void restorePatch(DataComponentPatch patch) {
        this.patch.clear();
        this.patch.putAll(patch.getRawMap());
    }

    /**
     * Clears all patches (back to the pure prototype state).
     */
    public void clearPatch() {
        this.patch.clear();
    }

    /**
     * Sets all components in bulk.
     */
    @SuppressWarnings("unchecked")
    public void setAll(DataComponentMap components) {
        for (TypedDataComponent<?> entry : components) {
            DataComponentType<Object> type = (DataComponentType<Object>) entry.getType();
            set(type, entry.getValue());
        }
    }

    // ========== View conversion ==========

    /**
     * Exports the current state as a patch.
     */
    public DataComponentPatch asPatch() {
        if (patch.isEmpty()) {
            return DataComponentPatch.EMPTY;
        }
        return new DataComponentPatch(new IdentityHashMap<>(patch));
    }

    /**
     * Clones this map.
     */
    public PatchedDataComponentMap copy() {
        PatchedDataComponentMap result = new PatchedDataComponentMap(prototype);
        result.patch.putAll(patch);
        return result;
    }

    /**
     * Converts to an immutable map (prototype merged with the patch).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public DataComponentMap toImmutableMap() {
        if (patch.isEmpty()) {
            return prototype;
        }
        DataComponentMap.Builder builder = DataComponentMap.builder();
        for (TypedDataComponent<?> entry : this) {
            DataComponentType type = entry.getType();
            builder.set(type, entry.getValue());
        }
        return builder.build();
    }

    // ========== Iteration view ==========

    @Override
    public Set<DataComponentType<?>> keySet() {
        if (patch.isEmpty()) {
            return prototype.keySet();
        }
        Set<DataComponentType<?>> keys = new HashSet<>(prototype.keySet());
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            Optional<?> value = entry.getValue();
            if (value != null && value.isPresent()) {
                keys.add(entry.getKey());
            } else {
                keys.remove(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    @Override
    public Iterator<TypedDataComponent<?>> iterator() {
        if (patch.isEmpty()) {
            return prototype.iterator();
        }
        List<TypedDataComponent<?>> list = new ArrayList<>(patch.size() + prototype.size());
        // First walk the explicit values in the patch
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isPresent()) {
                list.add(TypedDataComponent.unchecked(entry.getKey(), entry.getValue().get()));
            }
        }
        // Then walk the prototype entries not overridden by the patch
        for (TypedDataComponent<?> component : prototype) {
            if (!patch.containsKey(component.getType())) {
                list.add(component);
            }
        }
        return list.iterator();
    }

    @Override
    public int size() {
        int size = prototype.size();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            boolean inPatch = entry.getValue() != null && entry.getValue().isPresent();
            boolean inPrototype = prototype.has(entry.getKey());
            if (inPatch != inPrototype) {
                size += inPatch ? 1 : -1;
            }
        }
        return Math.max(0, size);
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean has(DataComponentType<?> type) {
        return get(type) != null;
    }

    // ========== Object contract ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PatchedDataComponentMap)) return false;
        PatchedDataComponentMap that = (PatchedDataComponentMap) o;
        return prototype.equals(that.prototype) && patch.equals(that.patch);
    }

    @Override
    public int hashCode() {
        return prototype.hashCode() + patch.hashCode() * 31;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (TypedDataComponent<?> entry : this) {
            if (first) first = false;
            else sb.append(", ");
            sb.append(entry);
        }
        sb.append("}");
        return sb.toString();
    }
}
