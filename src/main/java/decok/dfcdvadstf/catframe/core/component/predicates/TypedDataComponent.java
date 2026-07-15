package decok.dfcdvadstf.catframe.core.component.predicates;

import decok.dfcdvadstf.catframe.core.component.DataComponentHolder;
import decok.dfcdvadstf.catframe.core.component.DataComponentType;

import java.util.Map;
import java.util.Objects;

/**
 * 类型化的组件值配对。
 * <p>
 * 参考 26.1.2 {@code net.minecraft.core.component.TypedDataComponent}。
 *
 * @param <T> 值的类型
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
     * 将此组件应用到可写容器。
     */
    public void applyTo(DataComponentHolder holder) {
        holder.set(type, value);
    }

    // ========== 工厂方法 ==========

    /**
     * 从 Map.Entry 创建实例（用于内部迭代）。
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

    // ========== 对象约定 ==========

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
