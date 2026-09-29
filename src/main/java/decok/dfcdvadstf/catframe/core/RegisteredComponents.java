package decok.dfcdvadstf.catframe.core;

import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.core.component.ComponentSerializers;
import decok.dfcdvadstf.catframe.core.component.DataComponentType;
import decok.dfcdvadstf.catframe.core.component.DataComponents;
import decok.dfcdvadstf.catframe.core.component.predicates.*;
import net.minecraft.util.ResourceLocation;

/**
 * Known registered component type constants.
 * <p>
 * Registered during FMLPreInitializationEvent.
 */
public final class RegisteredComponents {

    private RegisteredComponents() {}

    // ========== Component type definitions ==========

    /** Custom data (fallback NBT, scope = full vanilla item NBT) */
    public static final DataComponentType<CustomData> CUSTOM_DATA =
            DataComponentType.<CustomData>builder(new ResourceLocation(Tags.MODID, "custom_data"))
                    .persistent(CustomData.SERIALIZER)
                    .networkSynchronized(CustomData.SERIALIZER)
                    .cacheEncoding()
                    .build();

    /** Enchantment list */
    public static final DataComponentType<ItemEnchantments> ENCHANTMENTS =
            DataComponentType.<ItemEnchantments>builder(new ResourceLocation(Tags.MODID, "enchantments"))
                    .persistent(ItemEnchantments.SERIALIZER)
                    .networkSynchronized(ItemEnchantments.SERIALIZER)
                    .build();

    /** Item lore */
    public static final DataComponentType<ItemLore> LORE =
            DataComponentType.<ItemLore>builder(new ResourceLocation(Tags.MODID, "lore"))
                    .persistent(ItemLore.SERIALIZER)
                    .networkSynchronized(ItemLore.SERIALIZER)
                    .build();

    /** Custom name */
    public static final DataComponentType<String> CUSTOM_NAME =
            DataComponentType.<String>builder(new ResourceLocation(Tags.MODID, "custom_name"))
                    .persistent(ComponentSerializers.ofString("Name"))
                    .networkSynchronized(ComponentSerializers.ofString("Name"))
                    .build();

    /** Dyed color */
    public static final DataComponentType<DyedItemColor> DYED_COLOR =
            DataComponentType.<DyedItemColor>builder(new ResourceLocation(Tags.MODID, "dyed_color"))
                    .persistent(DyedItemColor.SERIALIZER)
                    .networkSynchronized(DyedItemColor.SERIALIZER)
                    .build();

    /** Attribute modifiers */
    public static final DataComponentType<ItemAttributeModifiers> ATTRIBUTE_MODIFIERS =
            DataComponentType.<ItemAttributeModifiers>builder(new ResourceLocation(Tags.MODID, "attribute_modifiers"))
                    .persistent(ItemAttributeModifiers.SERIALIZER)
                    .networkSynchronized(ItemAttributeModifiers.SERIALIZER)
                    .build();

    /** Block entity data */
    public static final DataComponentType<BlockItemStateProperties> BLOCK_ENTITY_DATA =
            DataComponentType.<BlockItemStateProperties>builder(new ResourceLocation(Tags.MODID, "block_entity_data"))
                    .persistent(BlockItemStateProperties.SERIALIZER)
                    .networkSynchronized(BlockItemStateProperties.SERIALIZER)
                    .build();

    /** Tool properties */
    public static final DataComponentType<Tool> TOOL =
            DataComponentType.<Tool>builder(new ResourceLocation(Tags.MODID, "tool"))
                    .persistent(Tool.SERIALIZER)
                    .networkSynchronized(Tool.SERIALIZER)
                    .build();

    /** Unbreakable flag */
    public static final DataComponentType<Boolean> UNBREAKABLE =
            DataComponentType.<Boolean>builder(new ResourceLocation(Tags.MODID, "unbreakable"))
                    .persistent(ComponentSerializers.ofBoolean("Unbreakable"))
                    .networkSynchronized(ComponentSerializers.ofBoolean("Unbreakable"))
                    .build();

    /** Repair cost */
    public static final DataComponentType<Integer> REPAIR_COST =
            DataComponentType.<Integer>builder(new ResourceLocation(Tags.MODID, "repair_cost"))
                    .persistent(ComponentSerializers.ofInt("RepairCost"))
                    .networkSynchronized(ComponentSerializers.ofInt("RepairCost"))
                    .build();

    /** Max stack size (runtime override) */
    public static final DataComponentType<Integer> MAX_STACK_SIZE =
            DataComponentType.<Integer>builder(new ResourceLocation(Tags.MODID, "max_stack_size"))
                    .persistent(ComponentSerializers.ofInt("MaxStackSize"))
                    .networkSynchronized(ComponentSerializers.ofInt("MaxStackSize"))
                    .build();

    /** Item damage value */
    public static final DataComponentType<Integer> DAMAGE =
            DataComponentType.<Integer>builder(new ResourceLocation(Tags.MODID, "damage"))
                    .persistent(ComponentSerializers.ofInt("Damage"))
                    .networkSynchronized(ComponentSerializers.ofInt("Damage"))
                    .build();

    // ========== Registration methods ==========

    /**
     * Register all component types. Called during FMLPreInitializationEvent.
     */
    public static void registerAll() {
        DataComponents.register(CUSTOM_DATA);
        DataComponents.register(ENCHANTMENTS);
        DataComponents.register(LORE);
        DataComponents.register(CUSTOM_NAME);
        DataComponents.register(DYED_COLOR);
        DataComponents.register(ATTRIBUTE_MODIFIERS);
        DataComponents.register(BLOCK_ENTITY_DATA);
        DataComponents.register(TOOL);
        DataComponents.register(UNBREAKABLE);
        DataComponents.register(REPAIR_COST);
        DataComponents.register(MAX_STACK_SIZE);
        DataComponents.register(DAMAGE);
        DataComponents.register(DataComponents.ENCHANTMENT_GLINT);
        DataComponents.register(DataComponents.ITEM_MODEL);
        DataComponents.register(DataComponents.TOOLTIP_STYLE);
    }
}
