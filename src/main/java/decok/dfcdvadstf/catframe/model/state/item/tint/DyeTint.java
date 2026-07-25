package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.core.component.predicates.DyedItemColor;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * 物品染色颜色 tint（皮革盔甲等）。
 * <p>
 * 对齐原版 {@code ItemArmor#getColor}：皮革盔甲的可染色层从物品 NBT 的
 * {@code display.color} 读取颜色；未染色时回落到默认色（原版皮革默认色为
 * {@code 10511680} = {@code 0xA06540}）。
 * <p>
 * 读取通过 CatFrame 的 DataComponent 抽象 {@link DyedItemColor}（对应高版本
 * {@code dyed_color} 组件，wiki 中 {@code minecraft:dye} tint 类型的数据来源）完成。
 * 这里刻意使用 {@link DyedItemColor#SERIALIZER} 每帧直读 NBT，而非走
 * {@code ItemStackComponents} 的实例缓存——避免同一 ItemStack 实例被重新染色后
 * 缓存未失效导致渲染出陈旧颜色。
 *
 * <p>Item dye tint (leather armor, etc.). Mirrors vanilla {@code ItemArmor#getColor}:
 * reads {@code display.color} from NBT for the dyeable layer, falling back to a default
 * color when undyed (vanilla leather default is {@code 10511680}). Color is read through
 * CatFrame's {@link DyedItemColor} DataComponent (equivalent to the high-version
 * {@code dyed_color} component). {@code SERIALIZER} is used to read NBT fresh each frame
 * instead of the cached component map, avoiding stale colors after re-dyeing.
 *
 * <p>JSON: {@code {"type": "minecraft:dye", "default": 10511680}}
 */
public class DyeTint implements ItemTint {

    /** 原版皮革盔甲未染色时的默认色（{@code 0xA06540}）。 */
    public static final int DEFAULT_LEATHER_COLOR = 0xA06540; // 10511680

    /** 取不到染色信息时使用的默认色（对齐 wiki dye tint 的 {@code default} 字段）。 */
    private final int defaultColor;

    public DyeTint() {
        this(DEFAULT_LEATHER_COLOR);
    }

    public DyeTint(int defaultColor) {
        this.defaultColor = defaultColor & 0xFFFFFF;
    }

    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        if (stack == null || !stack.hasTagCompound()) return defaultColor;
        // 经 DyedItemColor 组件序列化器直读 display.color；未染色返回 null → 用默认色。
        // Read display.color via the DyedItemColor serializer; null (undyed) → default.
        DyedItemColor dyed = DyedItemColor.SERIALIZER.read(stack.getTagCompound());
        return dyed != null ? dyed.getRgb() & 0xFFFFFF : defaultColor;
    }
}
