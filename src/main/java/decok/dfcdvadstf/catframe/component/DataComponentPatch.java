package decok.dfcdvadstf.catframe.component;

import decok.dfcdvadstf.catframe.component.predicates.TypedDataComponent;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 组件补丁 - 表示一组组件相对于原型的差值。
 * <p>
 * 参考 26.1.2 {@code net.minecraft.core.component.DataComponentPatch}。
 * <p>
 * 补丁是一个 {@code Map<DataComponentType<?>, Optional<?>>}，
 * 其中 {@code Optional.of(value)} 表示设置组件，{@code Optional.empty()} 表示移除组件。
 */
public final class DataComponentPatch {

    /** 空补丁常量 */
    public static final DataComponentPatch EMPTY = new DataComponentPatch(Collections.emptyMap());

    private final Map<DataComponentType<?>, Optional<?>> map;

    /**
     * 包级私有构造函数，由 PatchedDataComponentMap 和 Builder 调用。
     */
    public DataComponentPatch(Map<DataComponentType<?>, Optional<?>> map) {
        this.map = map;
    }

    // ========== 工厂方法 ==========

    public static Builder builder() {
        return new Builder();
    }

    // ========== 查询 ==========

    /**
     * 检查补丁是否为空。
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * 返回补丁的大小。
     */
    public int size() {
        return map.size();
    }

    /**
     * 获取补丁中的值（结合原型回退）。
     *
     * @param prototype 原型映射
     * @param type      组件类型
     * @return 补丁值（存在），否则从原型获取
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
     * 返回补丁的原始映射（仅供内部使用）。
     */
    public Map<DataComponentType<?>, Optional<?>> getRawMap() {
        return map;
    }

    /**
     * 返回所有条目的集合。
     */
    public Set<Map.Entry<DataComponentType<?>, Optional<?>>> entrySet() {
        return map.entrySet();
    }

    /**
     * 拆分补丁为 added（新增）和 removed（移除）两部分。
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

    // ========== 对象约定 ==========

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

    // ========== 拆分结果 ==========

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
