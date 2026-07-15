package decok.dfcdvadstf.catframe.core.component;

import javax.annotation.Nullable;

/**
 * 可写的组件容器接口。
 * <p>
 * 参考 26.1.2 {@code net.minecraft.core.component.DataComponentHolder}。
 */
public interface DataComponentHolder extends DataComponentGetter {

    /**
     * 设置指定类型的组件值。
     *
     * @param <T>   值的类型
     * @param type  组件类型
     * @param value 值（null 表示移除组件）
     * @return 之前的值，若之前不存在返回 null
     */
    @Nullable
    <T> T set(DataComponentType<T> type, @Nullable T value);

    /**
     * 移除指定类型的组件。
     *
     * @return 之前的值，若之前不存在返回 null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    default <T> T remove(DataComponentType<? extends T> type) {
        return this.set((DataComponentType<T>) type, null);
    }

    /**
     * 检查是否包含指定类型的组件。
     */
    default boolean has(DataComponentType<?> type) {
        return this.get(type) != null;
    }
}
