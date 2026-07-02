package decok.dfcdvadstf.catframe.component;

import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 物品属性修饰符集合。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.world.item.component.ItemAttributeModifiers}。
 * 映射到原版 ItemStack 的 "AttributeModifiers" NBT 标签。
 * <p>
 * 在 1.7.10 中，属性修饰符由 {@link net.minecraft.entity.ai.attributes.AttributeModifier} 表示，
 * 并存储在物品 NBT 的 "AttributeModifiers" 列表中。
 */
public final class ItemAttributeModifiers {

    private static final ItemAttributeModifiers EMPTY = new ItemAttributeModifiers(Collections.emptyList());

    private final List<Entry> entries;

    private ItemAttributeModifiers(List<Entry> entries) {
        this.entries = entries;
    }

    // ========== 工厂方法 ==========

    public static ItemAttributeModifiers empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ========== 查询 ==========

    public List<Entry> getEntries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    // ========== NBT 转换 ==========

    public NBTTagList toNBT() {
        NBTTagList list = new NBTTagList();
        for (Entry entry : entries) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("AttributeName", entry.attributeName);
            tag.setString("Name", entry.modifier.getName());
            tag.setDouble("Amount", entry.modifier.getAmount());
            tag.setInteger("Operation", entry.modifier.getOperation());
            tag.setLong("UUIDMost", entry.modifier.getID().getMostSignificantBits());
            tag.setLong("UUIDLeast", entry.modifier.getID().getLeastSignificantBits());
            if (entry.slot != null) {
                tag.setString("Slot", entry.slot);
            }
            list.appendTag(tag);
        }
        return list;
    }

    public static ItemAttributeModifiers fromNBT(NBTTagList list) {
        if (list == null || list.tagCount() == 0) return EMPTY;
        Builder builder = builder();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            String attributeName = tag.getString("AttributeName");
            String name = tag.getString("Name");
            double amount = tag.getDouble("Amount");
            int operation = tag.getInteger("Operation");
            long uuidMost = tag.getLong("UUIDMost");
            long uuidLeast = tag.getLong("UUIDLeast");
            String slot = tag.hasKey("Slot") ? tag.getString("Slot") : null;

            AttributeModifier modifier = new AttributeModifier(
                    new java.util.UUID(uuidMost, uuidLeast),
                    name, amount, operation
            );
            builder.add(new Entry(attributeName, modifier, slot));
        }
        return builder.build();
    }

    // ========== 对象约定 ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemAttributeModifiers)) return false;
        return entries.equals(((ItemAttributeModifiers) o).entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "Attributes" + entries;
    }

    // ========== Entry 类型 ==========

    public static final class Entry {
        private final String attributeName;
        private final AttributeModifier modifier;
        private final String slot;

        public Entry(String attributeName, AttributeModifier modifier, @Nullable String slot) {
            this.attributeName = attributeName;
            this.modifier = modifier;
            this.slot = slot;
        }

        public String getAttributeName() { return attributeName; }
        public AttributeModifier getModifier() { return modifier; }
        @Nullable
        public String getSlot() { return slot; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Entry)) return false;
            Entry entry = (Entry) o;
            return attributeName.equals(entry.attributeName)
                    && modifier.equals(entry.modifier)
                    && Objects.equals(slot, entry.slot);
        }

        @Override
        public int hashCode() {
            return Objects.hash(attributeName, modifier, slot);
        }
    }

    // ========== Builder ==========

    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();

        private Builder() {}

        public Builder add(Entry entry) {
            entries.add(entry);
            return this;
        }

        public Builder add(String attributeName, AttributeModifier modifier, @Nullable String slot) {
            return add(new Entry(attributeName, modifier, slot));
        }

        public ItemAttributeModifiers build() {
            return entries.isEmpty() ? EMPTY : new ItemAttributeModifiers(Collections.unmodifiableList(new ArrayList<>(entries)));
        }
    }

    // ========== 序列化器 ==========

    public static final ComponentSerializer<ItemAttributeModifiers> SERIALIZER = new ComponentSerializer<ItemAttributeModifiers>() {
        private static final String KEY = "AttributeModifiers";

        @Override
        public void write(NBTTagCompound nbt, ItemAttributeModifiers value) {
            if (!value.isEmpty()) {
                nbt.setTag(KEY, value.toNBT());
            } else if (nbt.hasKey(KEY)) {
                nbt.removeTag(KEY);
            }
        }

        @Nullable
        @Override
        public ItemAttributeModifiers read(NBTTagCompound nbt) {
            if (!nbt.hasKey(KEY, 9)) return null;
            return fromNBT(nbt.getTagList(KEY, 10));
        }
    };
}
