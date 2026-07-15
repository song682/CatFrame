package decok.dfcdvadstf.catframe.core.component.predicates;

import decok.dfcdvadstf.catframe.core.component.ComponentSerializer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;

/**
 * 方块物品的方块实体数据。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.world.level.block.entity.BlockEntityData}。
 * 用于存储那些在放置时传递给方块实体的 NBT 数据（如刷怪笼、牌子、箱子等）。
 * 映射到原版 ItemStack 的 "BlockEntityTag" NBT 标签。
 */
public final class BlockItemStateProperties {

    private static final BlockItemStateProperties EMPTY = new BlockItemStateProperties(new NBTTagCompound());

    private final NBTTagCompound tag;

    private BlockItemStateProperties(NBTTagCompound tag) {
        this.tag = tag;
    }

    // ========== 工厂方法 ==========

    public static BlockItemStateProperties of(NBTTagCompound tag) {
        return tag.hasNoTags() ? EMPTY : new BlockItemStateProperties((NBTTagCompound) tag.copy());
    }

    public static BlockItemStateProperties wrap(NBTTagCompound tag) {
        return tag.hasNoTags() ? EMPTY : new BlockItemStateProperties(tag);
    }

    public static BlockItemStateProperties empty() {
        return EMPTY;
    }

    // ========== 访问 ==========

    public NBTTagCompound getTag() {
        return tag;
    }

    public NBTTagCompound copyTag() {
        return (NBTTagCompound) tag.copy();
    }

    public boolean isEmpty() {
        return tag.hasNoTags();
    }

    /**
     * 应用到 TileEntity。
     */
    public void applyToTileEntity(TileEntity tileEntity) {
        tileEntity.readFromNBT(tag);
    }

    // ========== 对象约定 ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockItemStateProperties)) return false;
        return tag.equals(((BlockItemStateProperties) o).tag);
    }

    @Override
    public int hashCode() {
        return tag.hashCode();
    }

    @Override
    public String toString() {
        return "BlockEntityData" + tag;
    }

    // ========== 序列化器 ==========

    public static final ComponentSerializer<BlockItemStateProperties> SERIALIZER = new ComponentSerializer<BlockItemStateProperties>() {
        private static final String KEY = "BlockEntityTag";

        @Override
        public void write(NBTTagCompound nbt, BlockItemStateProperties value) {
            if (!value.isEmpty()) {
                nbt.setTag(KEY, value.tag);
            } else if (nbt.hasKey(KEY)) {
                nbt.removeTag(KEY);
            }
        }

        @Nullable
        @Override
        public BlockItemStateProperties read(NBTTagCompound nbt) {
            if (!nbt.hasKey(KEY, 10)) return null;
            return wrap(nbt.getCompoundTag(KEY));
        }
    };
}
