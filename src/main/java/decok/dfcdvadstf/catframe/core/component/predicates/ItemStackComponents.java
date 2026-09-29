package decok.dfcdvadstf.catframe.core.component.predicates;

import decok.dfcdvadstf.catframe.core.component.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

/**
 * Per-instance bridge between ItemStack and the DataComponent system.
 * <p>
 * Design role: like the role {@code OreDict2Tag} plays between OreDictionary and Tags,
 * this class bridges NBT (stackTagCompound) and the DataComponent system:
 * <ul>
 *   <li>on read: merges the item defaults with the NBT instance data → full component view</li>
 *   <li>on write: syncs component changes back to NBT</li>
 * </ul>
 * <p>
 * Uses an {@link IdentityHashMap} to cache the {@link PatchedDataComponentMap} of every ItemStack
 * instance, avoiding repeated NBT parsing. The cache follows weak-reference semantics (managed
 * externally) and is GC-safe.
 * <p>
 * <b>No Mixin required</b> — all data lives in an external map, without intruding into ItemStack.
 */
public final class ItemStackComponents {

    private ItemStackComponents() {}

    /**
     * ItemStack → PatchedDataComponentMap cache.
     * <p>
     * Every ItemStack instance maps to one PatchedDataComponentMap:
     * <ul>
     *   <li>prototype = the item's default components (from {@link DataComponents#getDefaults})</li>
     *   <li>patch = instance-level overrides parsed from NBT</li>
     * </ul>
     */
    private static final IdentityHashMap<ItemStack, PatchedDataComponentMap> CACHE = new IdentityHashMap<>();

    // ==================== Core API ====================

    ///
    /// Returns the full component map of an ItemStack (lazily initialized).
    /// <p>
    /// On the first call:
    /// 1. take the item's default components from {@link DataComponents#getDefaults} as the prototype
    /// 2. parse the instance data from stackTagCompound as the patch
    /// 3. cache it in the IdentityHashMap
    /// <p>
    /// Later calls return the cache directly.
    ///
    /// @param stack the target item stack
    /// @return the full component map (prototype + NBT patch)
    ///
    public static PatchedDataComponentMap get(ItemStack stack) {
        if (stack == null) {
            throw new IllegalArgumentException("ItemStack must not be null");
        }

        PatchedDataComponentMap cached = CACHE.get(stack);
        if (cached != null) {
            return cached;
        }

        // Lazy init: prototype + NBT parsing
        DataComponentMap defaults = DataComponents.getDefaults(stack.getItem());
        PatchedDataComponentMap map = new PatchedDataComponentMap(defaults);

        NBTTagCompound tag = stack.stackTagCompound;
        if (tag != null && !tag.hasNoTags()) {
            DataComponentMap fromNBT = ComponentMigration.readFromNBT(tag);
            // Write the values parsed from NBT as patch entries
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
    /// Syncs the component data back to the ItemStack's NBT.
    /// <p>
    /// Suits scenarios where "components are the single source of truth": the component values are
    /// written back to stackTagCompound.
    ///
    /// @param stack the target item stack
    ///
    public static void syncToNBT(ItemStack stack) {
        if (stack == null) return;

        PatchedDataComponentMap map = CACHE.get(stack);
        if (map == null) return; // No component data, nothing to sync

        ensureTagCompound(stack);
        ComponentMigration.syncToNBT(stack.stackTagCompound, map);
    }

    ///
    /// Invalidates the cache — call when the ItemStack's NBT is modified externally.
    /// <p>
    /// The next {@link #get} re-parses from NBT.
    ///
    /// @param stack the target item stack
    ///
    public static void invalidate(ItemStack stack) {
        if (stack != null) {
            CACHE.remove(stack);
        }
    }

    ///
    /// Clears the whole cache.
    /// <p>
    /// Usually called on world load/unload to prevent memory leaks.
    ///
    public static void clearCache() {
        CACHE.clear();
    }

    ///
    /// Returns the ItemStack's component map if already cached, otherwise null.
    /// <p>
    /// Non-destructive query — does not trigger NBT parsing.
    ///
    @Nullable
    public static PatchedDataComponentMap getCached(ItemStack stack) {
        return stack != null ? CACHE.get(stack) : null;
    }

    ///
    /// Copies the components of the source stack onto the target stack.
    /// <p>
    /// Used by scenarios such as ItemStack.copy() / splitStack().
    ///
    /// @param source the source stack
    /// @param target the target stack
    ///
    public static void copyComponents(ItemStack source, ItemStack target) {
        if (source == null || target == null) return;

        PatchedDataComponentMap sourceMap = getCached(source);
        if (sourceMap != null) {
            CACHE.put(target, sourceMap.copy());
        }
    }

    // ==================== Internal helpers ====================

    private static void ensureTagCompound(ItemStack stack) {
        if (stack.stackTagCompound == null) {
            stack.stackTagCompound = new NBTTagCompound();
        }
    }
}
