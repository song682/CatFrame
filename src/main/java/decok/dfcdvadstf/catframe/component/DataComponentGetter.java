package decok.dfcdvadstf.catframe.component;

import decok.dfcdvadstf.catframe.component.predicates.TypedDataComponent;

import javax.annotation.Nullable;

/**
 * 只读的组件容器接口。
 * <p>
 * 参考 26.1.2 {@code net.minecraft.core.component.DataComponentGetter}。
 */
public interface DataComponentGetter {

    /**
     * 获取指定类型的组件值。
     *
     * @return 组件值，若不存在返回 null
     */
    @Nullable
    <T> T get(DataComponentType<? extends T> type);

    /**
     * 获取指定类型的组件值，不存在时返回默认值。
     */
    default <T> T getOrDefault(DataComponentType<? extends T> type, T defaultValue) {
        T value = this.get(type);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取携带类型信息的组件值。
     */
    @Nullable
    default <T> TypedDataComponent<T> getTyped(DataComponentType<T> type) {
        T value = this.get(type);
        return value != null ? new TypedDataComponent<>(type, value) : null;
    }
}
