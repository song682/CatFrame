package decok.dfcdvadstf.catframe.core.component;

import decok.dfcdvadstf.catframe.compact.forge.tags.OreDict2Tag;
import decok.dfcdvadstf.catframe.core.RegisteredComponents;
import decok.dfcdvadstf.catframe.core.component.predicates.*;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

/**
 * Two-way bridge between the NBT and DataComponent systems — analogous to the role {@link OreDict2Tag} plays for OreDictionary and Tag.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li><b>NBT → Component</b>: Parse component values from legacy NBT (stackTagCompound)</li>
 *   <li><b>Component → NBT</b>: Write component values back to NBT to maintain legacy compatibility</li>
 * </ul>
 * <p>
 * This class does not depend on ItemStack; it only operates on {@link NBTTagCompound} and {@link DataComponentMap}.
 * Per-ItemStack instance bridging is handled by {@link ItemStackComponents}.
 */
public final class ComponentMigration {

    private ComponentMigration() {}

    // ==================== NBT → Component ====================

    ///
    /// 从 NBT 中解析出所有已知的组件值，构建为一个 DataComponentMap。
    ///
    /// @param tag 旧版 stackTagCompound（可为 null）
    /// @return 解析出的组件映射（若 tag 为 null 或空则返回空映射）
    ///
    public static DataComponentMap readFromNBT(@Nullable NBTTagCompound tag) {
        if (tag == null || tag.hasNoTags()) return DataComponentMap.EMPTY;

        DataComponentMap.Builder builder = DataComponentMap.builder();

        readEnchantments(tag, builder);
        readDisplayName(tag, builder);
        readLore(tag, builder);
        readColor(tag, builder);
        readAttributeModifiers(tag, builder);
        readUnbreakable(tag, builder);
        readRepairCost(tag, builder);
        readBlockEntityTag(tag, builder);
        readCustomData(tag, builder);

        return builder.build();
    }

    // ==================== Component → NBT ====================

    ///
    /// 将组件数据写入 NBT 标签，保持与旧版 NBT 格式兼容。
    ///
    /// @param tag        目标 NBT 标签（stackTagCompound）
    /// @param components 组件数据源
    ///
    public static void writeToNBT(NBTTagCompound tag, DataComponentMap components) {
        if (tag == null) return;

        writeEnchantments(tag, components);
        writeDisplayName(tag, components);
        writeLore(tag, components);
        writeColor(tag, components);
        writeAttributeModifiers(tag, components);
        writeUnbreakable(tag, components);
        writeRepairCost(tag, components);
        writeBlockEntityTag(tag, components);
        writeCustomData(tag, components);
    }

    /**
     * Synchronization Mode: First clear the old field, then write the component value.<br>
     * Suitable for scenarios where "the component is the single source of truth."
     */
    public static void syncToNBT(NBTTagCompound tag, DataComponentMap components) {
        if (tag == null) return;
        writeToNBT(tag, components);
    }

    // Single String（NBT → Component）

    private static void readEnchantments(NBTTagCompound tag, DataComponentMap.Builder builder) {
        if (tag.hasKey("ench", 9)) {
            builder.set(RegisteredComponents.ENCHANTMENTS,
                    ItemEnchantments.fromNBT(tag.getTagList("ench", 10)));
        }
    }

    private static void readDisplayName(NBTTagCompound tag, DataComponentMap.Builder builder) {
        if (tag.hasKey("display", 10)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            if (display.hasKey("Name")) {
                builder.set(RegisteredComponents.CUSTOM_NAME, display.getString("Name"));
            }
        }
    }

    private static void readLore(NBTTagCompound tag, DataComponentMap.Builder builder) {
        if (tag.hasKey("display", 10)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            if (display.hasKey("Lore", 9)) {
                builder.set(RegisteredComponents.LORE,
                        ItemLore.fromNBT(display.getTagList("Lore", 8)));
            }
        }
    }

    private static void readColor(NBTTagCompound tag, DataComponentMap.Builder builder) {
        if (tag.hasKey("display", 10)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            if (display.hasKey("color")) {
                builder.set(RegisteredComponents.DYED_COLOR,
                        new DyedItemColor(display.getInteger("color")));
            }
        }
    }

    private static void readAttributeModifiers(NBTTagCompound tag, DataComponentMap.Builder builder) {
        if (tag.hasKey("AttributeModifiers", 9)) {
            builder.set(RegisteredComponents.ATTRIBUTE_MODIFIERS,
                    ItemAttributeModifiers.fromNBT(tag.getTagList("AttributeModifiers", 10)));
        }
    }

    private static void readUnbreakable(NBTTagCompound tag, DataComponentMap.Builder builder) {
        if (tag.hasKey("Unbreakable")) {
            builder.set(RegisteredComponents.UNBREAKABLE, tag.getByte("Unbreakable") != 0);
        }
    }

    private static void readRepairCost(NBTTagCompound tag, DataComponentMap.Builder builder) {
        if (tag.hasKey("RepairCost")) {
            builder.set(RegisteredComponents.REPAIR_COST, tag.getInteger("RepairCost"));
        }
    }

    private static void readBlockEntityTag(NBTTagCompound tag, DataComponentMap.Builder builder) {
        if (tag.hasKey("BlockEntityTag", 10)) {
            builder.set(RegisteredComponents.BLOCK_ENTITY_DATA,
                    BlockItemStateProperties.wrap(tag.getCompoundTag("BlockEntityTag")));
        }
    }

    private static void readCustomData(NBTTagCompound tag, DataComponentMap.Builder builder) {
        CustomData custom = CustomData.SERIALIZER.read(tag);
        if (custom != null && !custom.isEmpty()) {
            builder.set(RegisteredComponents.CUSTOM_DATA, custom);
        }
    }

    // 单字段写入（Component → NBT）

    private static void writeEnchantments(NBTTagCompound tag, DataComponentMap components) {
        ItemEnchantments ench = components.get(RegisteredComponents.ENCHANTMENTS);
        if (ench != null && !ench.isEmpty()) {
            tag.setTag("ench", ench.toNBT());
        } else {
            tag.removeTag("ench");
        }
    }

    private static void writeDisplayName(NBTTagCompound tag, DataComponentMap components) {
        String name = components.get(RegisteredComponents.CUSTOM_NAME);
        if (name != null && !name.isEmpty()) {
            NBTTagCompound display = getOrCreateCompound(tag, "display");
            display.setString("Name", name);
        } else {
            removeDisplayField(tag, "Name");
        }
    }

    private static void writeLore(NBTTagCompound tag, DataComponentMap components) {
        ItemLore lore = components.get(RegisteredComponents.LORE);
        if (lore != null && !lore.isEmpty()) {
            NBTTagCompound display = getOrCreateCompound(tag, "display");
            display.setTag("Lore", lore.toNBT());
        } else {
            removeDisplayField(tag, "Lore");
        }
    }

    private static void writeColor(NBTTagCompound tag, DataComponentMap components) {
        DyedItemColor color = components.get(RegisteredComponents.DYED_COLOR);
        if (color != null) {
            NBTTagCompound display = getOrCreateCompound(tag, "display");
            display.setInteger("color", color.getRgb());
        } else {
            removeDisplayField(tag, "color");
        }
    }

    private static void writeAttributeModifiers(NBTTagCompound tag, DataComponentMap components) {
        ItemAttributeModifiers attrs = components.get(RegisteredComponents.ATTRIBUTE_MODIFIERS);
        if (attrs != null && !attrs.isEmpty()) {
            tag.setTag("AttributeModifiers", attrs.toNBT());
        } else {
            tag.removeTag("AttributeModifiers");
        }
    }

    private static void writeUnbreakable(NBTTagCompound tag, DataComponentMap components) {
        Boolean unbreakable = components.get(RegisteredComponents.UNBREAKABLE);
        if (Boolean.TRUE.equals(unbreakable)) {
            tag.setByte("Unbreakable", (byte) 1);
        } else {
            tag.removeTag("Unbreakable");
        }
    }

    private static void writeRepairCost(NBTTagCompound tag, DataComponentMap components) {
        Integer cost = components.get(RegisteredComponents.REPAIR_COST);
        if (cost != null && cost > 0) {
            tag.setInteger("RepairCost", cost);
        } else {
            tag.removeTag("RepairCost");
        }
    }

    private static void writeBlockEntityTag(NBTTagCompound tag, DataComponentMap components) {
        BlockItemStateProperties beData = components.get(RegisteredComponents.BLOCK_ENTITY_DATA);
        if (beData != null && !beData.isEmpty()) {
            tag.setTag("BlockEntityTag", beData.getTag());
        } else {
            tag.removeTag("BlockEntityTag");
        }
    }

    private static void writeCustomData(NBTTagCompound tag, DataComponentMap components) {
        CustomData custom = components.get(RegisteredComponents.CUSTOM_DATA);
        if (custom != null && !custom.isEmpty()) {
            CustomData.SERIALIZER.write(tag, custom);
        } else {
            tag.removeTag("CustomData");
        }
    }

    // Inner Filed
    private static NBTTagCompound getOrCreateCompound(NBTTagCompound parent, String key) {
        if (parent.hasKey(key, 10)) {
            return parent.getCompoundTag(key);
        }
        NBTTagCompound compound = new NBTTagCompound();
        parent.setTag(key, compound);
        return compound;
    }

    private static void removeDisplayField(NBTTagCompound tag, String field) {
        if (tag.hasKey("display", 10)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            display.removeTag(field);
            if (display.hasNoTags()) {
                tag.removeTag("display");
            }
        }
    }
}
