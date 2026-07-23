package decok.dfcdvadstf.catframe.core;

import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.core.component.ComponentSerializers;
import decok.dfcdvadstf.catframe.core.component.predicates.CustomData;
import decok.dfcdvadstf.catframe.core.component.DataComponentType;
import decok.dfcdvadstf.catframe.core.component.DataComponents;
import decok.dfcdvadstf.catframe.core.component.predicates.*;
import net.minecraft.util.ResourceLocation;

/**
 * 已知的已注册组件类型常量。
 * <p>
 * 在 FMLPreInitializationEvent 阶段注册。
 */
public final class RegisteredComponents {

    private RegisteredComponents() {}

    // ========== 组件类型定义 ==========

    /** 自定义数据（兜底 NBT） */
    public static final DataComponentType<CustomData> CUSTOM_DATA =
            DataComponentType.<CustomData>builder(new ResourceLocation(Tags.MODID, "custom_data"))
                    .persistent(CustomData.SERIALIZER)
                    .networkSynchronized(CustomData.SERIALIZER)
                    .cacheEncoding()
                    .build();

    /** 附魔列表 */
    public static final DataComponentType<ItemEnchantments> ENCHANTMENTS =
            DataComponentType.<ItemEnchantments>builder(new ResourceLocation(Tags.MODID, "enchantments"))
                    .persistent(ItemEnchantments.SERIALIZER)
                    .networkSynchronized(ItemEnchantments.SERIALIZER)
                    .build();

    /** 物品描述 */
    public static final DataComponentType<ItemLore> LORE =
            DataComponentType.<ItemLore>builder(new ResourceLocation(Tags.MODID, "lore"))
                    .persistent(ItemLore.SERIALIZER)
                    .networkSynchronized(ItemLore.SERIALIZER)
                    .build();

    /** 自定义名称 */
    public static final DataComponentType<String> CUSTOM_NAME =
            DataComponentType.<String>builder(new ResourceLocation(Tags.MODID, "custom_name"))
                    .persistent(ComponentSerializers.ofString("Name"))
                    .networkSynchronized(ComponentSerializers.ofString("Name"))
                    .build();

    /** 染色颜色 */
    public static final DataComponentType<DyedItemColor> DYED_COLOR =
            DataComponentType.<DyedItemColor>builder(new ResourceLocation(Tags.MODID, "dyed_color"))
                    .persistent(DyedItemColor.SERIALIZER)
                    .networkSynchronized(DyedItemColor.SERIALIZER)
                    .build();

    /** 属性修饰符 */
    public static final DataComponentType<ItemAttributeModifiers> ATTRIBUTE_MODIFIERS =
            DataComponentType.<ItemAttributeModifiers>builder(new ResourceLocation(Tags.MODID, "attribute_modifiers"))
                    .persistent(ItemAttributeModifiers.SERIALIZER)
                    .networkSynchronized(ItemAttributeModifiers.SERIALIZER)
                    .build();

    /** 方块实体数据 */
    public static final DataComponentType<BlockItemStateProperties> BLOCK_ENTITY_DATA =
            DataComponentType.<BlockItemStateProperties>builder(new ResourceLocation(Tags.MODID, "block_entity_data"))
                    .persistent(BlockItemStateProperties.SERIALIZER)
                    .networkSynchronized(BlockItemStateProperties.SERIALIZER)
                    .build();

    /** 工具属性 */
    public static final DataComponentType<Tool> TOOL =
            DataComponentType.<Tool>builder(new ResourceLocation(Tags.MODID, "tool"))
                    .persistent(Tool.SERIALIZER)
                    .networkSynchronized(Tool.SERIALIZER)
                    .build();

    /** 不可破坏标记 */
    public static final DataComponentType<Boolean> UNBREAKABLE =
            DataComponentType.<Boolean>builder(new ResourceLocation(Tags.MODID, "unbreakable"))
                    .persistent(ComponentSerializers.ofBoolean("Unbreakable"))
                    .networkSynchronized(ComponentSerializers.ofBoolean("Unbreakable"))
                    .build();

    /** 修复花费 */
    public static final DataComponentType<Integer> REPAIR_COST =
            DataComponentType.<Integer>builder(new ResourceLocation(Tags.MODID, "repair_cost"))
                    .persistent(ComponentSerializers.ofInt("RepairCost"))
                    .networkSynchronized(ComponentSerializers.ofInt("RepairCost"))
                    .build();

    /** 最大堆叠数（运行时覆盖） */
    public static final DataComponentType<Integer> MAX_STACK_SIZE =
            DataComponentType.<Integer>builder(new ResourceLocation(Tags.MODID, "max_stack_size"))
                    .persistent(ComponentSerializers.ofInt("MaxStackSize"))
                    .networkSynchronized(ComponentSerializers.ofInt("MaxStackSize"))
                    .build();

    /** 物品损伤值（damage） */
    public static final DataComponentType<Integer> DAMAGE =
            DataComponentType.<Integer>builder(new ResourceLocation(Tags.MODID, "damage"))
                    .persistent(ComponentSerializers.ofInt("Damage"))
                    .networkSynchronized(ComponentSerializers.ofInt("Damage"))
                    .build();

    /** 工具提示样式 ID（兼容 ChromaticTooltips —— 按 style 换纹理） */
    public static final DataComponentType<String> TOOLTIP_STYLE =
            DataComponentType.<String>builder(new ResourceLocation(Tags.MODID, "tooltip_style"))
                    .persistent(ComponentSerializers.ofString("TooltipStyle"))
                    .networkSynchronized(ComponentSerializers.ofString("TooltipStyle"))
                    .build();

    // ========== 注册方法 ==========

    /**
     * 注册所有组件类型。在 FMLPreInitializationEvent 中调用。
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
        DataComponents.register(TOOLTIP_STYLE);
        DataComponents.register(DataComponents.ENCHANTMENT_GLINT);
        DataComponents.register(DataComponents.ITEM_MODEL);
    }
}
