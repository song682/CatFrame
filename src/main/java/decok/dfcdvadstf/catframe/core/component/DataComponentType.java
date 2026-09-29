package decok.dfcdvadstf.catframe.core.component;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Data component type - identifies one specific kind of data component.
 * <p>
 * Every component type is identified by a unique ResourceLocation and holds its NBT serializers.
 * Mirrors 26.1.2 {@code net.minecraft.core.component.DataComponentType}.
 * <p>
 * Component types use reference identity as their Map key,
 * while equals/hashCode are based on the id to keep logical consistency.
 *
 * @param <T> the Java type of the component value
 */
public final class DataComponentType<T> {

    private final ResourceLocation id;
    @Nullable
    private final ComponentSerializer<T> serializer;          // NBT persistence serializer
    @Nullable
    private final ComponentSerializer<T> networkSerializer;   // network serializer (may reuse the NBT format)
    private final boolean cacheEncoding;
    /** Network ID assigned at registration time (-1 means not registered) */
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

    // ========== Factory methods ==========

    public static <T> Builder<T> builder(ResourceLocation id) {
        return new Builder<>(id);
    }

    // ========== Accessors ==========

    public ResourceLocation getId() {
        return id;
    }

    /**
     * @return whether this component is transient (exists at runtime, not persisted to NBT)
     */
    public boolean isTransient() {
        return serializer == null;
    }

    /**
     * @return the NBT serializer; null for transient components
     */
    @Nullable
    public ComponentSerializer<T> getSerializer() {
        return serializer;
    }

    /**
     * @return the network serializer, falling back to the NBT serializer when unspecified
     */
    @Nullable
    public ComponentSerializer<T> getNetworkSerializer() {
        return networkSerializer != null ? networkSerializer : serializer;
    }

    public boolean isCacheEncoding() {
        return cacheEncoding;
    }

    /**
     * @return the network encoding ID assigned at registration time
     */
    public int getNetworkId() {
        if (networkId < 0) {
            throw new IllegalStateException("DataComponentType " + id + " has not been registered");
        }
        return networkId;
    }

    // ========== Object contract ==========

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
         * Sets the persistence serializer. Calling this marks the component as saved to NBT.
         */
        public Builder<T> persistent(ComponentSerializer<T> serializer) {
            this.serializer = serializer;
            return this;
        }

        /**
         * Sets the network serializer. Falls back to the persistent serializer when unset.
         */
        public Builder<T> networkSynchronized(ComponentSerializer<T> networkSerializer) {
            this.networkSerializer = networkSerializer;
            return this;
        }

        /**
         * Enables caching of the serialization result (suited to immutable value types).
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
