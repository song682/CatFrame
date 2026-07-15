package decok.dfcdvadstf.catframe.core.component;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

/**
 * 组件值的 NBT 序列化器。
 * <p>
 * 对应 26.1.2 中 Codec 的角色，负责组件值 ↔ NBT 的双向转换。
 *
 * @param <T> 组件值的 Java 类型
 */
public interface ComponentSerializer<T> {

    /**
     * 将组件值写入 NBT 标签。
     */
    void write(NBTTagCompound nbt, T value);

    /**
     * 从 NBT 标签读取组件值。
     *
     * @return 解析后的值，若标签不存在或格式异常返回 null
     */
    @Nullable
    T read(NBTTagCompound nbt);

    /**
     * 检查 NBT 中是否包含此组件的数据。
     */
    default boolean hasData(NBTTagCompound nbt) {
        return true;
    }
}
