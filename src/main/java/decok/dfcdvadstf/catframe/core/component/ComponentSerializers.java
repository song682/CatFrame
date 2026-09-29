package decok.dfcdvadstf.catframe.core.component;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Factory methods for component serializers.
 * <p>
 * Provides quick construction of the common serialization patterns.
 */
public final class ComponentSerializers {

    private ComponentSerializers() {}

    // ========== Marker components (only presence/absence is stored) ==========

    /**
     * Marker serializer: the value only expresses presence (present when true / non-null); no actual
     * data is stored.
     */
    public static ComponentSerializer<Boolean> ofUnit(String key) {
        return new ComponentSerializer<Boolean>() {
            @Override
            public void write(NBTTagCompound nbt, Boolean value) {
                if (Boolean.TRUE.equals(value)) {
                    nbt.setBoolean(key, true);
                }
            }

            @Nullable
            @Override
            public Boolean read(NBTTagCompound nbt) {
                return nbt.hasKey(key) ? Boolean.TRUE : null;
            }
        };
    }

    // ========== Primitive types ==========

    public static ComponentSerializer<Integer> ofInt(String key) {
        return primitive(key, NBTTagCompound::getInteger, NBTTagCompound::setInteger);
    }

    public static ComponentSerializer<Boolean> ofBoolean(String key) {
        return primitive(key, NBTTagCompound::getBoolean, NBTTagCompound::setBoolean);
    }

    public static ComponentSerializer<String> ofString(String key) {
        return primitive(key, NBTTagCompound::getString, NBTTagCompound::setString);
    }

    public static ComponentSerializer<Byte> ofByte(String key) {
        return primitive(key, NBTTagCompound::getByte, NBTTagCompound::setByte);
    }

    public static ComponentSerializer<Short> ofShort(String key) {
        return primitive(key, NBTTagCompound::getShort, NBTTagCompound::setShort);
    }

    public static ComponentSerializer<Long> ofLong(String key) {
        return primitive(key, NBTTagCompound::getLong, NBTTagCompound::setLong);
    }

    public static ComponentSerializer<Float> ofFloat(String key) {
        return primitive(key, NBTTagCompound::getFloat, NBTTagCompound::setFloat);
    }

    public static ComponentSerializer<Double> ofDouble(String key) {
        return primitive(key, NBTTagCompound::getDouble, NBTTagCompound::setDouble);
    }

    // ========== Sub-compound ==========

    /**
     * Creates a serializer that encodes the value under a specific key of a sub-compound.
     *
     * @param key      key name of the sub-compound
     * @param decoder  decodes the value from the sub-compound
     * @param encoder  encodes the value into the sub-compound
     */
    public static <T> ComponentSerializer<T> ofSubCompound(String key,
                                                            Function<NBTTagCompound, T> decoder,
                                                            WriteConsumer<T> encoder) {
        return new ComponentSerializer<T>() {
            @Override
            public void write(NBTTagCompound nbt, T value) {
                NBTTagCompound sub = new NBTTagCompound();
                encoder.accept(sub, value);
                nbt.setTag(key, sub);
            }

            @Nullable
            @Override
            public T read(NBTTagCompound nbt) {
                if (!nbt.hasKey(key, 10)) return null;
                return decoder.apply(nbt.getCompoundTag(key));
            }
        };
    }

    /**
     * Creates a serializer whose component value is a self-contained NBTTagCompound.
     *
     * @param decoder decodes the value from an NBTTagCompound
     * @param encoder encodes the value into an NBTTagCompound
     */
    public static <T> ComponentSerializer<T> ofCompound(Function<NBTTagCompound, T> decoder,
                                                         WriteConsumer<T> encoder) {
        return new ComponentSerializer<T>() {
            @Override
            public void write(NBTTagCompound nbt, T value) {
                encoder.accept(nbt, value);
            }

            @Nullable
            @Override
            public T read(NBTTagCompound nbt) {
                return decoder.apply(nbt);
            }
        };
    }

    // ========== Delegation ==========

    /**
     * Creates a serializer that delegates to an existing NBT field.
     * Used to reuse existing fields in the vanilla ItemStack NBT (such as "ench", "display").
     */
    public static <T> ComponentSerializer<T> delegate(String key,
                                                       Function<NBTTagCompound, T> reader,
                                                       WriteConsumer<T> writer) {
        return new ComponentSerializer<T>() {
            @Override
            public void write(NBTTagCompound nbt, T value) {
                writer.accept(nbt, value);
            }

            @Nullable
            @Override
            public T read(NBTTagCompound nbt) {
                if (!hasKey(nbt, key)) return null;
                return reader.apply(nbt);
            }

            @Override
            public boolean hasData(NBTTagCompound nbt) {
                return hasKey(nbt, key);
            }

            private boolean hasKey(NBTTagCompound nbt, String key) {
                return nbt.hasKey(key);
            }
        };
    }

    // ========== Internal patterns ==========

    private static <T> ComponentSerializer<T> primitive(String key,
                                                         NBTSupplier<T> supplier,
                                                         NBTBiConsumer<T> consumer) {
        return new ComponentSerializer<T>() {
            @Override
            public void write(NBTTagCompound nbt, T value) {
                consumer.accept(nbt, key, value);
            }

            @Nullable
            @Override
            public T read(NBTTagCompound nbt) {
                if (!nbt.hasKey(key)) return null;
                return supplier.get(nbt, key);
            }
        };
    }

    // ========== Functional interfaces ==========

    @FunctionalInterface
    public interface WriteConsumer<T> {
        void accept(NBTTagCompound nbt, T value);
    }

    @FunctionalInterface
    private interface NBTSupplier<T> {
        T get(NBTTagCompound nbt, String key);
    }

    @FunctionalInterface
    private interface NBTBiConsumer<T> {
        void accept(NBTTagCompound nbt, String key, T value);
    }
}
