package decok.dfcdvadstf.catframe.component;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 自定义数据 - 兜底的 NBT 数据容器。
 * <p>
 * 类似 26.1.2 {@code net.minecraft.world.item.component.CustomData}。
 * 用于存储没有对应组件的任意 NBT 数据，确保与旧版 NBT 兼容。
 */
public final class CustomData {

    private static final CustomData EMPTY = new CustomData(new NBTTagCompound());

    private static final String CUSTOM_DATA_KEY = "CustomData";

    private final NBTTagCompound tag;
    private final int cachedHash;

    private CustomData(NBTTagCompound tag) {
        this.tag = tag;
        this.cachedHash = tag.hashCode();
    }

    /**
     * 创建包含指定数据的 CustomData。
     */
    public static CustomData of(NBTTagCompound tag) {
        return tag.hasNoTags() ? EMPTY : new CustomData((NBTTagCompound) tag.copy());
    }

    /**
     * 创建包装已有 NBT 的 CustomData（不复制）。
     */
    public static CustomData wrap(NBTTagCompound tag) {
        return tag.hasNoTags() ? EMPTY : new CustomData(tag);
    }

    /**
     * 返回空数据实例。
     */
    public static CustomData empty() {
        return EMPTY;
    }

    /**
     * 获取内部 NBT 的副本。
     */
    public NBTTagCompound copyTag() {
        return (NBTTagCompound) tag.copy();
    }

    /**
     * 获取内部 NBT（只读）。
     */
    public NBTTagCompound getTag() {
        return tag;
    }

    /**
     * 更新数据。
     */
    public CustomData update(NBTTagCompound newTag) {
        return newTag.equals(tag) ? this : new CustomData((NBTTagCompound) newTag.copy());
    }

    public boolean isEmpty() {
        return tag.hasNoTags();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomData)) return false;
        CustomData that = (CustomData) o;
        return tag.equals(that.tag);
    }

    @Override
    public int hashCode() {
        return cachedHash;
    }

    @Override
    public String toString() {
        return "CustomData" + tag;
    }

    // ========== 序列化器 ==========

    public static final ComponentSerializer<CustomData> SERIALIZER = new ComponentSerializer<CustomData>() {
        @Override
        public void write(NBTTagCompound nbt, CustomData value) {
            if (!value.isEmpty()) {
                nbt.setTag(CUSTOM_DATA_KEY, value.tag);
            } else if (nbt.hasKey(CUSTOM_DATA_KEY)) {
                nbt.removeTag(CUSTOM_DATA_KEY);
            }
        }

        @Nullable
        @Override
        public CustomData read(NBTTagCompound nbt) {
            if (!nbt.hasKey(CUSTOM_DATA_KEY, 10)) return null;
            return wrap(nbt.getCompoundTag(CUSTOM_DATA_KEY));
        }

        @Override
        public boolean hasData(NBTTagCompound nbt) {
            return nbt.hasKey(CUSTOM_DATA_KEY, 10);
        }
    };
}
