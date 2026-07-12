package decok.dfcdvadstf.catframe.component;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * 组件序列化器工厂方法。
 * <p>
 * 提供常用序列化模式的快速构建。
 */
public final class ComponentSerializers {

    private ComponentSerializers() {}

    // ========== 标记组件（仅存储存在/不存在） ==========

    /**
     * 标记型序列化器：值只表示存在性（值为 true/非 null 时存在），不存储实际数据。
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

    // ========== 原生类型 ==========

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

    // ========== 子 Compound ==========

    /**
     * 创建一个序列化器，将值编码为子 Compound 中的特定键。
     *
     * @param key      子 Compound 的键名
     * @param decoder  从子 Compound 解码值
     * @param encoder  将值编码到子 Compound
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
     * 创建一个序列化器，组件值即是一个自包含的 NBTTagCompound。
     *
     * @param decoder 从 NBTTagCompound 解码值
     * @param encoder 将值编码为 NBTTagCompound
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

    // ========== 委托/代理 ==========

    /**
     * 创建一个委托给现有 NBT 字段的序列化器。
     * 用于复用原版 ItemStack NBT 中的已有字段（如 "ench"、"display"）。
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

    // ========== 内部模式 ==========

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

    // ========== 函数式接口 ==========

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
