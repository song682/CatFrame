package decok.dfcdvadstf.catframe.ui.tooltip;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 物品 tooltip 图像提供者注册表——1.7.10 对 26.1.2 {@code Item#getTooltipImage(ItemStack)} 的静态替代。
 * <p>
 * 1.7.10 无法为原版 {@code Item} 追加可覆写方法，本注册表以 Item 为键登记提供者，
 * 由物品 tooltip 的收集路径查询；物品可在初始化阶段登记自身的 tooltip 图像组件
 * （对标 26.1.2 {@code BundleItem} 提供 {@code BundleTooltip}）。
 * </p>
 * <p>
 * Static registry replacing 26.1.2 {@code Item.getTooltipImage(ItemStack)},
 * which cannot be added to the vanilla {@code Item} class under 1.7.10.
 * </p>
 */
public final class ItemTooltipImages {

    /**
     * 图像提供者——按 ItemStack 返回其 tooltip 图像组件。
     * <p>对标 26.1.2 {@code Item#getTooltipImage(ItemStack)} 的返回值。</p>
     */
    @FunctionalInterface
    public interface Provider {
        Optional<TooltipComponent> get(ItemStack stack);
    }

    /** Item → 提供者（注册期写入、渲染期读取）。 */
    private static final Map<Item, Provider> PROVIDERS = new IdentityHashMap<>();

    private ItemTooltipImages() {}

    /**
     * 登记物品的 tooltip 图像提供者。
     *
     * @param item     目标物品
     * @param provider 图像提供者
     */
    public static synchronized void register(final Item item, final Provider provider) {
        PROVIDERS.put(item, provider);
    }

    /**
     * 查询 ItemStack 的 tooltip 图像组件。
     * <p>对标 26.1.2 {@code ItemStack#getTooltipImage()}。</p>
     *
     * @param stack 目标物品堆（可为 null）
     * @return 图像组件；未登记提供者或无图像时为空
     */
    public static Optional<TooltipComponent> get(final ItemStack stack) {
        if (stack == null) {
            return Optional.empty();
        }
        final Provider provider = PROVIDERS.get(stack.getItem());
        return provider != null ? provider.get(stack) : Optional.empty();
    }
}
