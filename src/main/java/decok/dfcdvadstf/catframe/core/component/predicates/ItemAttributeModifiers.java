package decok.dfcdvadstf.catframe.core.component.predicates;

import decok.dfcdvadstf.catframe.core.component.ComponentSerializer;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Collection of item attribute modifiers.
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.world.item.component.ItemAttributeModifiers}.
 * Maps to the vanilla ItemStack "AttributeModifiers" NBT tag.
 * <p>
 * In 1.7.10, attribute modifiers are represented by
 * {@link net.minecraft.entity.ai.attributes.AttributeModifier} and stored in the
 * "AttributeModifiers" list of the item NBT.
 */
public final class ItemAttributeModifiers {

    private static final ItemAttributeModifiers EMPTY = new ItemAttributeModifiers(Collections.emptyList());

    private final List<Entry> entries;

    private ItemAttributeModifiers(List<Entry> entries) {
        this.entries = entries;
    }

    // ========== Factory methods ==========

    public static ItemAttributeModifiers empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ========== Queries ==========

    public List<Entry> getEntries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    // ========== NBT conversion ==========

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

    // ========== Object contract ==========

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

    // ========== Entry type ==========

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

    // ========== Serializer ==========

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
