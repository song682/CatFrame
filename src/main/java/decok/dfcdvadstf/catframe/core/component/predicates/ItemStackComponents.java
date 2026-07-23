package decok.dfcdvadstf.catframe.core.component.predicates;

import decok.dfcdvadstf.catframe.core.component.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

/**
 * ItemStack 与 DataComponent 系统的 per-instance 桥接器。
 * <p>
 * 设计定位：类似 {@code OreDict2Tag} 在 OreDictionary 和 Tag 之间的角色，
 * 本类在 NBT (stackTagCompound) 和 DataComponent 系统之间架起桥梁：
 * <ul>
 *   <li>读取时：合并 Item 默认组件 + NBT 实例数据 → 完整组件视图</li>
 *   <li>写入时：将组件变更同步回 NBT</li>
 * </ul>
 * <p>
 * 使用 {@link IdentityHashMap} 缓存每个 ItemStack 实例的 {@link PatchedDataComponentMap}，
 * 避免重复解析 NBT。缓存使用弱引用语义（通过外部管理），GC 安全。
 * <p>
 * <b>无需 Mixin</b>——所有数据存储在外部映射中，不侵入 ItemStack 类。
 */
public final class ItemStackComponents {

    private ItemStackComponents() {}

    /**
     * ItemStack → PatchedDataComponentMap 缓存。
     * <p>
     * 每个 ItemStack 实例对应一个 PatchedDataComponentMap：
     * <ul>
     *   <li>prototype = Item 的默认组件（来自 {@link DataComponents#getDefaults}）</li>
     *   <li>patch = NBT 中解析出的实例级覆写</li>
     * </ul>
     */
    private static final IdentityHashMap<ItemStack, PatchedDataComponentMap> CACHE = new IdentityHashMap<>();

    // ==================== 核心 API ====================

    ///
    /// 获取 ItemStack 的完整组件映射（懒初始化）。
    /// <p>
    /// 首次调用时：
    /// 1. 从 {@link DataComponents#getDefaults} 获取该 Item 的默认组件作为原型
    /// 2. 从 stackTagCompound 解析实例数据作为补丁
    /// 3. 缓存到 IdentityHashMap 中
    /// <p>
    /// 后续调用直接返回缓存。
    ///
    /// @param stack 目标物品
    /// @return 完整的组件映射（原型 + NBT 补丁）
    ///
    public static PatchedDataComponentMap get(ItemStack stack) {
        if (stack == null) {
            throw new IllegalArgumentException("ItemStack must not be null");
        }

        PatchedDataComponentMap cached = CACHE.get(stack);
        if (cached != null) {
            return cached;
        }

        // 懒初始化：原型 + NBT 解析
        DataComponentMap defaults = DataComponents.getDefaults(stack.getItem());
        PatchedDataComponentMap map = new PatchedDataComponentMap(defaults);

        NBTTagCompound tag = stack.stackTagCompound;
        if (tag != null && !tag.hasNoTags()) {
            DataComponentMap fromNBT = ComponentMigration.readFromNBT(tag);
            // 将 NBT 中解析出的值作为补丁写入
            for (TypedDataComponent<?> entry : fromNBT) {
                @SuppressWarnings("unchecked")
                DataComponentType<Object> type = (DataComponentType<Object>) entry.getType();
                map.set(type, entry.getValue());
            }
        }

        CACHE.put(stack, map);
        return map;
    }

    ///
    /// 将组件数据同步回 ItemStack 的 NBT。
    /// <p>
    /// 适用于"组件为唯一真相源"的场景：调用后将组件值写回 stackTagCompound。
    ///
    /// @param stack 目标物品
    ///
    public static void syncToNBT(ItemStack stack) {
        if (stack == null) return;

        PatchedDataComponentMap map = CACHE.get(stack);
        if (map == null) return; // 没有组件数据，无需同步

        ensureTagCompound(stack);
        ComponentMigration.syncToNBT(stack.stackTagCompound, map);
    }

    ///
    /// 使缓存失效——当 ItemStack 的 NBT 被外部修改时调用。
    /// <p>
    /// 下次 {@link #get} 时会重新从 NBT 解析。
    ///
    /// @param stack 目标物品
    ///
    public static void invalidate(ItemStack stack) {
        if (stack != null) {
            CACHE.remove(stack);
        }
    }

    ///
    /// 清除所有缓存。
    /// <p>
    /// 通常在世界加载/卸载时调用，防止内存泄漏。
    ///
    public static void clearCache() {
        CACHE.clear();
    }

    ///
    /// 获取 ItemStack 的组件映射（若已缓存），否则返回 null。
    /// <p>
    /// 非破坏性查询——不会触发 NBT 解析。
    ///
    @Nullable
    public static PatchedDataComponentMap getCached(ItemStack stack) {
        return stack != null ? CACHE.get(stack) : null;
    }

    ///
    /// 复制源物品的组件到目标物品。
    /// <p>
    /// 用于 ItemStack.copy() / splitStack() 等场景。
    ///
    /// @param source 源物品
    /// @param target 目标物品
    ///
    public static void copyComponents(ItemStack source, ItemStack target) {
        if (source == null || target == null) return;

        PatchedDataComponentMap sourceMap = getCached(source);
        if (sourceMap != null) {
            CACHE.put(target, sourceMap.copy());
        }
    }

    // ==================== 内部辅助 ====================

    private static void ensureTagCompound(ItemStack stack) {
        if (stack.stackTagCompound == null) {
            stack.stackTagCompound = new NBTTagCompound();
        }
    }
}
