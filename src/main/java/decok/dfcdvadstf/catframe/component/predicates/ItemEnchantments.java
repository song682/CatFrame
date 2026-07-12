package decok.dfcdvadstf.catframe.component.predicates;

import decok.dfcdvadstf.catframe.component.ComponentSerializer;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 物品附魔列表 - 不可变的附魔集合。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.world.item.enchantment.ItemEnchantments}。
 * 映射到原版 ItemStack 的 "ench" NBT 标签。
 */
public final class ItemEnchantments {

    private static final ItemEnchantments EMPTY = new ItemEnchantments(Collections.emptyMap());

    private final Map<Enchantment, Integer> enchantments;

    private ItemEnchantments(Map<Enchantment, Integer> enchantments) {
        this.enchantments = enchantments;
    }

    // ========== 工厂方法 ==========

    public static ItemEnchantments empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ItemEnchantments of(Enchantment enchantment, int level) {
        return new ItemEnchantments(Collections.singletonMap(enchantment, level));
    }

    // ========== 查询 ==========

    public int getLevel(Enchantment enchantment) {
        return enchantments.getOrDefault(enchantment, 0);
    }

    public boolean has(Enchantment enchantment) {
        return enchantments.containsKey(enchantment);
    }

    public boolean isEmpty() {
        return enchantments.isEmpty();
    }

    public int size() {
        return enchantments.size();
    }

    public Set<Map.Entry<Enchantment, Integer>> entrySet() {
        return enchantments.entrySet();
    }

    public Set<Enchantment> keySet() {
        return enchantments.keySet();
    }

    // ========== NBT 转换 ==========

    public NBTTagList toNBT() {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", (short) entry.getKey().effectId);
            tag.setShort("lvl", entry.getValue().shortValue());
            list.appendTag(tag);
        }
        return list;
    }

    public static ItemEnchantments fromNBT(NBTTagList list) {
        if (list == null || list.tagCount() == 0) return EMPTY;
        Builder builder = builder();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            short id = tag.getShort("id");
            short level = tag.getShort("lvl");
            if (id >= 0 && id < Enchantment.enchantmentsList.length) {
                Enchantment enchantment = Enchantment.enchantmentsList[id];
                if (enchantment != null && level > 0) {
                    builder.add(enchantment, (int) level);
                }
            }
        }
        return builder.build();
    }

    // ========== 对象约定 ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemEnchantments)) return false;
        ItemEnchantments that = (ItemEnchantments) o;
        return enchantments.equals(that.enchantments);
    }

    @Override
    public int hashCode() {
        return enchantments.hashCode();
    }

    @Override
    public String toString() {
        return enchantments.toString();
    }

    // ========== Builder ==========

    public static final class Builder {
        private final Map<Enchantment, Integer> map = new LinkedHashMap<>();

        private Builder() {}

        public Builder add(Enchantment enchantment, int level) {
            map.put(enchantment, Math.max(0, level));
            return this;
        }

        public Builder remove(Enchantment enchantment) {
            map.remove(enchantment);
            return this;
        }

        public Builder addAll(ItemEnchantments other) {
            map.putAll(other.enchantments);
            return this;
        }

        public ItemEnchantments build() {
            return map.isEmpty() ? EMPTY : new ItemEnchantments(new LinkedHashMap<>(map));
        }
    }

    // ========== 序列化器 ==========

    public static final ComponentSerializer<ItemEnchantments> SERIALIZER = new ComponentSerializer<ItemEnchantments>() {
        private static final String KEY = "ench";

        @Override
        public void write(NBTTagCompound nbt, ItemEnchantments value) {
            if (!value.isEmpty()) {
                nbt.setTag(KEY, value.toNBT());
            } else if (nbt.hasKey(KEY)) {
                nbt.removeTag(KEY);
            }
        }

        @Nullable
        @Override
        public ItemEnchantments read(NBTTagCompound nbt) {
            if (!nbt.hasKey(KEY, 9)) return null;
            NBTTagList list = nbt.getTagList(KEY, 10);
            return fromNBT(list);
        }
    };
}
