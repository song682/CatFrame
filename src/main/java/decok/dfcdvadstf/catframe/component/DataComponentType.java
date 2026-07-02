package decok.dfcdvadstf.catframe.component;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 数据组件类型 - 标识一个特定类型的数据组件。
 * <p>
 * 每个组件类型由唯一的 ResourceLocation 标识，并持有对应的 NBT 序列化器。
 * 参考 26.1.2 {@code net.minecraft.core.component.DataComponentType}。
 * <p>
 * 组件类型使用引用标识（reference identity）作为 Map key，
 * 但 equals/hashCode 基于 id 实现以保证逻辑一致性。
 *
 * @param <T> 组件值的 Java 类型
 */
public final class DataComponentType<T> {

    private final ResourceLocation id;
    @Nullable
    private final ComponentSerializer<T> serializer;          // NBT 持久化序列化器
    @Nullable
    private final ComponentSerializer<T> networkSerializer;   // 网络序列化器（可复用 NBT 格式）
    private final boolean cacheEncoding;
    /** 注册时分配的网络 ID（-1 表示未注册） */
    int networkId = -1;

    private DataComponentType(ResourceLocation id,
                              @Nullable ComponentSerializer<T> serializer,
                              @Nullable ComponentSerializer<T> networkSerializer,
                              boolean cacheEncoding) {
        this.id = Objects.requireNonNull(id, "id");
        this.serializer = serializer;
        this.networkSerializer = networkSerializer;
        this.cacheEncoding = cacheEncoding;
    }

    // ========== 工厂方法 ==========

    public static <T> Builder<T> builder(ResourceLocation id) {
        return new Builder<>(id);
    }

    // ========== 访问器 ==========

    public ResourceLocation getId() {
        return id;
    }

    /**
     * @return 该组件是否为瞬态（运行时存在，不持久化到 NBT）
     */
    public boolean isTransient() {
        return serializer == null;
    }

    /**
     * @return NBT 序列化器，瞬态组件返回 null
     */
    @Nullable
    public ComponentSerializer<T> getSerializer() {
        return serializer;
    }

    /**
     * @return 网络序列化器，未指定时回退到 NBT 序列化器
     */
    @Nullable
    public ComponentSerializer<T> getNetworkSerializer() {
        return networkSerializer != null ? networkSerializer : serializer;
    }

    public boolean isCacheEncoding() {
        return cacheEncoding;
    }

    /**
     * @return 注册时分配的网络编码 ID
     */
    public int getNetworkId() {
        if (networkId < 0) {
            throw new IllegalStateException("DataComponentType " + id + " has not been registered");
        }
        return networkId;
    }

    // ========== 对象约定 ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DataComponentType)) return false;
        DataComponentType<?> that = (DataComponentType<?>) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString();
    }

    // ========== Builder ==========

    public static final class Builder<T> {
        private final ResourceLocation id;
        private ComponentSerializer<T> serializer;
        private ComponentSerializer<T> networkSerializer;
        private boolean cacheEncoding;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        /**
         * 设置持久化序列化器。调用此方法表示组件会被写入 NBT 存档。
         */
        public Builder<T> persistent(ComponentSerializer<T> serializer) {
            this.serializer = serializer;
            return this;
        }

        /**
         * 设置网络序列化器。未设置时回退到 persistent serializer。
         */
        public Builder<T> networkSynchronized(ComponentSerializer<T> networkSerializer) {
            this.networkSerializer = networkSerializer;
            return this;
        }

        /**
         * 启用序列化结果缓存（适用于不可变值类型）。
         */
        public Builder<T> cacheEncoding() {
            this.cacheEncoding = true;
            return this;
        }

        public DataComponentType<T> build() {
            return new DataComponentType<>(id, serializer, networkSerializer, cacheEncoding);
        }
    }
}
