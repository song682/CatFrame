package decok.dfcdvadstf.catframe.component;

import net.minecraft.nbt.NBTTagCompound;

/**
 * NBT 与组件系统的双向迁移工具。
 * <p>
 * 负责在旧版 NBT（stackTagCompound）与新版组件系统之间同步数据。
 * 遵循"组件优先"原则：写时组件→NBT，读时 NBT→组件。
 */
public final class ComponentMigration {

    private ComponentMigration() {}

    /**
     * 从旧版 NBT 迁移数据到组件系统。
     *
     * @param tag        旧版 stackTagCompound
     * @param components 目标组件容器
     */
    public static void migrate(NBTTagCompound tag, PatchedDataComponentMap components) {
        if (tag == null || tag.hasNoTags()) return;

        migrateEnchantments(tag, components);
        migrateDisplayName(tag, components);
        migrateLore(tag, components);
        migrateColor(tag, components);
        migrateAttributeModifiers(tag, components);
        migrateUnbreakable(tag, components);
        migrateRepairCost(tag, components);
        migrateBlockEntityTag(tag, components);
    }

    /**
     * 将组件数据同步回旧版 NBT。
     *
     * @param tag        旧版 stackTagCompound
     * @param components 组件容器
     */
    public static void syncToNBT(NBTTagCompound tag, PatchedDataComponentMap components) {
        if (tag == null) return;

        syncEnchantments(tag, components);
        syncDisplayName(tag, components);
        syncLore(tag, components);
        syncColor(tag, components);
        syncAttributeModifiers(tag, components);
        syncUnbreakable(tag, components);
        syncRepairCost(tag, components);
        syncBlockEntityTag(tag, components);
    }

    // ========== 单个字段迁移 ==========

    private static void migrateEnchantments(NBTTagCompound tag, PatchedDataComponentMap components) {
        if (tag.hasKey("ench", 9) && !components.hasNonDefault(RegisteredComponents.ENCHANTMENTS)) {
            components.set(RegisteredComponents.ENCHANTMENTS,
                    ItemEnchantments.fromNBT(tag.getTagList("ench", 10)));
        }
    }

    private static void migrateDisplayName(NBTTagCompound tag, PatchedDataComponentMap components) {
        if (tag.hasKey("display", 10)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            if (display.hasKey("Name") && !components.hasNonDefault(RegisteredComponents.CUSTOM_NAME)) {
                components.set(RegisteredComponents.CUSTOM_NAME, display.getString("Name"));
            }
        }
    }

    private static void migrateLore(NBTTagCompound tag, PatchedDataComponentMap components) {
        if (tag.hasKey("display", 10)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            if (display.hasKey("Lore", 9) && !components.hasNonDefault(RegisteredComponents.LORE)) {
                components.set(RegisteredComponents.LORE,
                        ItemLore.fromNBT(display.getTagList("Lore", 8)));
            }
        }
    }

    private static void migrateColor(NBTTagCompound tag, PatchedDataComponentMap components) {
        if (tag.hasKey("display", 10)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            if (display.hasKey("color") && !components.hasNonDefault(RegisteredComponents.DYED_COLOR)) {
                components.set(RegisteredComponents.DYED_COLOR,
                        new DyedItemColor(display.getInteger("color")));
            }
        }
    }

    private static void migrateAttributeModifiers(NBTTagCompound tag, PatchedDataComponentMap components) {
        if (tag.hasKey("AttributeModifiers", 9) && !components.hasNonDefault(RegisteredComponents.ATTRIBUTE_MODIFIERS)) {
            components.set(RegisteredComponents.ATTRIBUTE_MODIFIERS,
                    ItemAttributeModifiers.fromNBT(tag.getTagList("AttributeModifiers", 10)));
        }
    }

    private static void migrateUnbreakable(NBTTagCompound tag, PatchedDataComponentMap components) {
        if (tag.hasKey("Unbreakable") && !components.hasNonDefault(RegisteredComponents.UNBREAKABLE)) {
            components.set(RegisteredComponents.UNBREAKABLE, tag.getByte("Unbreakable") != 0);
        }
    }

    private static void migrateRepairCost(NBTTagCompound tag, PatchedDataComponentMap components) {
        if (tag.hasKey("RepairCost") && !components.hasNonDefault(RegisteredComponents.REPAIR_COST)) {
            components.set(RegisteredComponents.REPAIR_COST, tag.getInteger("RepairCost"));
        }
    }

    private static void migrateBlockEntityTag(NBTTagCompound tag, PatchedDataComponentMap components) {
        if (tag.hasKey("BlockEntityTag", 10) && !components.hasNonDefault(RegisteredComponents.BLOCK_ENTITY_DATA)) {
            components.set(RegisteredComponents.BLOCK_ENTITY_DATA,
                    BlockItemStateProperties.wrap(tag.getCompoundTag("BlockEntityTag")));
        }
    }

    // ========== 单个字段同步 ==========

    private static void syncEnchantments(NBTTagCompound tag, PatchedDataComponentMap components) {
        ItemEnchantments ench = components.get(RegisteredComponents.ENCHANTMENTS);
        if (ench != null && !ench.isEmpty()) {
            tag.setTag("ench", ench.toNBT());
        } else {
            tag.removeTag("ench");
        }
    }

    private static void syncDisplayName(NBTTagCompound tag, PatchedDataComponentMap components) {
        String name = components.get(RegisteredComponents.CUSTOM_NAME);
        if (name != null && !name.isEmpty()) {
            NBTTagCompound display = tag.getCompoundTag("display");
            display.setString("Name", name);
            tag.setTag("display", display);
        } else if (tag.hasKey("display", 10)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            display.removeTag("Name");
            if (display.hasNoTags()) {
                tag.removeTag("display");
            }
        }
    }

    private static void syncLore(NBTTagCompound tag, PatchedDataComponentMap components) {
        ItemLore lore = components.get(RegisteredComponents.LORE);
        if (lore != null && !lore.isEmpty()) {
            NBTTagCompound display = tag.getCompoundTag("display");
            display.setTag("Lore", lore.toNBT());
            tag.setTag("display", display);
        } else if (tag.hasKey("display", 10)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            display.removeTag("Lore");
            if (display.hasNoTags()) {
                tag.removeTag("display");
            }
        }
    }

    private static void syncColor(NBTTagCompound tag, PatchedDataComponentMap components) {
        DyedItemColor color = components.get(RegisteredComponents.DYED_COLOR);
        if (color != null) {
            NBTTagCompound display = tag.getCompoundTag("display");
            display.setInteger("color", color.getRgb());
            tag.setTag("display", display);
        } else if (tag.hasKey("display", 10)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            display.removeTag("color");
            if (display.hasNoTags()) {
                tag.removeTag("display");
            }
        }
    }

    private static void syncAttributeModifiers(NBTTagCompound tag, PatchedDataComponentMap components) {
        ItemAttributeModifiers attrs = components.get(RegisteredComponents.ATTRIBUTE_MODIFIERS);
        if (attrs != null && !attrs.isEmpty()) {
            tag.setTag("AttributeModifiers", attrs.toNBT());
        } else {
            tag.removeTag("AttributeModifiers");
        }
    }

    private static void syncUnbreakable(NBTTagCompound tag, PatchedDataComponentMap components) {
        Boolean unbreakable = components.get(RegisteredComponents.UNBREAKABLE);
        if (Boolean.TRUE.equals(unbreakable)) {
            tag.setByte("Unbreakable", (byte) 1);
        } else {
            tag.removeTag("Unbreakable");
        }
    }

    private static void syncRepairCost(NBTTagCompound tag, PatchedDataComponentMap components) {
        Integer cost = components.get(RegisteredComponents.REPAIR_COST);
        if (cost != null && cost > 0) {
            tag.setInteger("RepairCost", cost);
        } else {
            tag.removeTag("RepairCost");
        }
    }

    private static void syncBlockEntityTag(NBTTagCompound tag, PatchedDataComponentMap components) {
        BlockItemStateProperties beData = components.get(RegisteredComponents.BLOCK_ENTITY_DATA);
        if (beData != null && !beData.isEmpty()) {
            tag.setTag("BlockEntityTag", beData.getTag());
        } else {
            tag.removeTag("BlockEntityTag");
        }
    }
}
