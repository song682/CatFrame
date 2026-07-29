package decok.dfcdvadstf.catframe.core.component.predicates;

import decok.dfcdvadstf.catframe.core.component.ComponentSerializer;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

/**
 * 自定义数据 - 兜底的 NBT 数据容器。
 * <p>
 * 类似 26.1.2 {@code net.minecraft.world.item.component.CustomData}。
 * 用于存储没有对应组件的任意 NBT 数据，确保与旧版 NBT 兼容。
 * <p>
 * Scope: covers the ENTIRE vanilla item NBT (the whole stackTagCompound),
 * mirroring the legacy {@code nbt} predicate semantics — not a nested sub-tag.
 * <br>
 * 作用范围：覆盖整个原版物品 NBT（完整的 stackTagCompound），
 * 对齐旧版 {@code nbt} 谓词语义——不再是嵌套子标签。
 */
public final class CustomData {

    private static final CustomData EMPTY = new CustomData(new NBTTagCompound());

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

    /**
     * Full-scope serializer: read wraps the whole vanilla tag; write merges
     * the held keys back into the root tag (no nested "CustomData" key).
     * <br>
     * 全量作用域序列化器：读取时包装整个原版标签；写入时将持有的键
     * 合并回根标签（不再使用嵌套的 "CustomData" 键）。
     */
    public static final ComponentSerializer<CustomData> SERIALIZER = new ComponentSerializer<CustomData>() {
        @Override
        public void write(NBTTagCompound nbt, CustomData value) {
            // Same live tag already in place — nothing to merge.
            // 与根标签为同一实例，数据已就位，无需合并。
            if (value.isEmpty() || value.tag == nbt) return;
            for (Object keyObj : value.tag.func_150296_c()) {
                String key = (String) keyObj;
                nbt.setTag(key, value.tag.getTag(key).copy());
            }
        }

        @Nullable
        @Override
        public CustomData read(NBTTagCompound nbt) {
            // Whole vanilla NBT is the component value.
            // 整个原版 NBT 即为组件值。
            if (nbt.hasNoTags()) return null;
            return wrap(nbt);
        }

        @Override
        public boolean hasData(NBTTagCompound nbt) {
            return !nbt.hasNoTags();
        }
    };
}
