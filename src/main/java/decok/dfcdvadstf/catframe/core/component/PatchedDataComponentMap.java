package decok.dfcdvadstf.catframe.core.component;

import decok.dfcdvadstf.catframe.core.component.predicates.TypedDataComponent;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 可变的组件映射，使用原型 + 补丁差值模式。
 * <p>
 * 参考 26.1.2 {@code net.minecraft.core.component.PatchedDataComponentMap}。
 * <p>
 * 原型是所有同类型物品共享的默认组件，补丁是单个实例相对原型的覆写。
 * 当实例值与原型值相同时，不会在补丁中占空间。
 */
public final class PatchedDataComponentMap implements DataComponentMap, DataComponentHolder {

    private final DataComponentMap prototype;
    private final Map<DataComponentType<?>, Optional<?>> patch;

    public PatchedDataComponentMap(DataComponentMap prototype) {
        this.prototype = prototype;
        this.patch = new IdentityHashMap<>();
    }

    // ========== 读操作 ==========

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
     * 检查指定类型的组件是否被补丁覆写（不同于原型）。
     */
    public boolean hasNonDefault(DataComponentType<?> type) {
        return patch.containsKey(type);
    }

    // ========== 写操作 ==========

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T set(DataComponentType<T> type, @Nullable T value) {
        T defaultValue = prototype.get(type);
        T oldValue = get(type);

        if (Objects.equals(value, defaultValue)) {
            // 值等于默认值，从补丁中移除（恢复默认）
            patch.remove(type);
        } else if (value != null) {
            // 值不等于默认值，写入补丁
            patch.put(type, Optional.of(value));
        } else {
            // 显式设为 null
            if (defaultValue != null) {
                // 原型有值，标记为移除
                patch.put(type, Optional.empty());
            } else {
                // 原型也没有，无需记录
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
     * 应用一个补丁。
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
     * 替换整个补丁。
     */
    public void restorePatch(DataComponentPatch patch) {
        this.patch.clear();
        this.patch.putAll(patch.getRawMap());
    }

    /**
     * 清除所有补丁（恢复为纯原型状态）。
     */
    public void clearPatch() {
        this.patch.clear();
    }

    /**
     * 批量设置所有组件。
     */
    @SuppressWarnings("unchecked")
    public void setAll(DataComponentMap components) {
        for (TypedDataComponent<?> entry : components) {
            DataComponentType<Object> type = (DataComponentType<Object>) entry.getType();
            set(type, entry.getValue());
        }
    }

    // ========== 视图转换 ==========

    /**
     * 将以当前状态导出为补丁。
     */
    public DataComponentPatch asPatch() {
        if (patch.isEmpty()) {
            return DataComponentPatch.EMPTY;
        }
        return new DataComponentPatch(new IdentityHashMap<>(patch));
    }

    /**
     * 克隆此映射。
     */
    public PatchedDataComponentMap copy() {
        PatchedDataComponentMap result = new PatchedDataComponentMap(prototype);
        result.patch.putAll(patch);
        return result;
    }

    /**
     * 转为不可变映射（合并原型和补丁）。
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

    // ========== 迭代视图 ==========

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
        // 先遍历补丁中的显式值
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isPresent()) {
                list.add(TypedDataComponent.unchecked(entry.getKey(), entry.getValue().get()));
            }
        }
        // 再遍历原型中未被补丁覆盖的
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

    // ========== 对象约定 ==========

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
