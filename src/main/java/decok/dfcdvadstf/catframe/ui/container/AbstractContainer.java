package decok.dfcdvadstf.catframe.ui.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * <p>
 * Pure data container — the storage layer of the three-layer container
 * abstraction. Implements {@link IInventory} so that vanilla TileEntities and
 * automation (hoppers, pipes) can interact with it, but knows nothing about
 * {@code Slot}, click handling, or network sync.<br>
 * Counterpart of the high-version Minecraft {@code Container} interface
 * (storage-only semantics).
 * </p>
 * <p>
 * 纯数据容器——三层容器抽象的存储层。实现 {@link IInventory} 以便原版 TileEntity
 * 与自动化设施（漏斗、管道）可以与之交互，但不知道 {@code Slot}、点击处理或网络同步。<br>
 * 对标高版本 Minecraft 的 {@code Container} 接口（纯存储语义）。
 * </p>
 */
public abstract class AbstractContainer implements IInventory, Iterable<ItemStack> {

    /** Backing storage. / 底层存储。 */
    private final ItemStack[] items;

    /** Custom display name, or {@code null} for none. / 自定义显示名，或 {@code null}。 */
    private String customName;

    /**
     * @param size the number of slots / 槽位数量
     */
    protected AbstractContainer(final int size) {
        this.items = new ItemStack[size];
    }

    // ──── Modern API (high-version naming) ────

    /**
     * @return the number of slots in this container / 本容器的槽位数量
     */
    public int getContainerSize() {
        return this.items.length;
    }

    /**
     * @return {@code true} if every slot is empty / 所有槽位均为空时返回 {@code true}
     */
    public boolean isEmpty() {
        for (final ItemStack stack : this.items) {
            if (stack != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the stack in the given slot.
     * <p>获取指定槽位的物品堆。</p>
     *
     * @param slot the slot index / 槽位索引
     * @return the stack, or {@code null} if empty / 物品堆，空则为 {@code null}
     */
    public ItemStack getItem(final int slot) {
        if (slot < 0 || slot >= this.items.length) {
            return null;
        }
        return this.items[slot];
    }

    /**
     * Set the stack in the given slot.
     * <p>设置指定槽位的物品堆。</p>
     *
     * @param slot  the slot index / 槽位索引
     * @param stack the stack to place (may be {@code null}) / 要放入的物品堆（可为 {@code null}）
     */
    public void setItem(final int slot, final ItemStack stack) {
        if (slot < 0 || slot >= this.items.length) {
            return;
        }
        this.items[slot] = stack;
        if (stack != null && stack.stackSize > getMaxStackSize()) {
            stack.stackSize = getMaxStackSize();
        }
        onContentsChanged(slot);
    }

    /**
     * Remove up to {@code count} items from the given slot, returning them as a
     * new stack.
     * <p>从指定槽位移除最多 {@code count} 个物品，以新堆返回。</p>
     *
     * @param slot  the slot index / 槽位索引
     * @param count maximum number to remove / 最大移除数量
     * @return the removed stack, or {@code null} / 被移除的物品堆，无则 {@code null}
     */
    public ItemStack removeItem(final int slot, final int count) {
        if (slot < 0 || slot >= this.items.length || this.items[slot] == null) {
            return null;
        }
        if (this.items[slot].stackSize <= count) {
            final ItemStack result = this.items[slot];
            this.items[slot] = null;
            onContentsChanged(slot);
            return result;
        }
        final ItemStack split = this.items[slot].splitStack(count);
        if (this.items[slot].stackSize == 0) {
            this.items[slot] = null;
        }
        onContentsChanged(slot);
        return split;
    }

    /**
     * Remove the entire stack from the given slot without triggering a
     * change notification.
     * <p>从指定槽位移除整个物品堆，不触发变更通知。</p>
     *
     * @param slot the slot index / 槽位索引
     * @return the removed stack, or {@code null} / 被移除的物品堆，无则 {@code null}
     */
    public ItemStack removeItemNoUpdate(final int slot) {
        if (slot < 0 || slot >= this.items.length) {
            return null;
        }
        final ItemStack result = this.items[slot];
        this.items[slot] = null;
        return result;
    }

    /**
     * Clear all slots.
     * <p>清空所有槽位。</p>
     */
    public void clearContent() {
        for (int i = 0; i < this.items.length; i++) {
            this.items[i] = null;
        }
    }

    /**
     * @return the maximum stack size for this container / 本容器的最大堆叠大小
     */
    public int getMaxStackSize() {
        return 64;
    }

    /**
     * Set a custom display name for this container.
     * <p>设置本容器的自定义显示名。</p>
     */
    public void setCustomName(final String name) {
        this.customName = name;
    }

    // ──── IInventory implementation (delegates to modern API) ────

    @Override
    public int getSizeInventory() {
        return getContainerSize();
    }

    @Override
    public ItemStack getStackInSlot(final int slot) {
        return getItem(slot);
    }

    @Override
    public ItemStack decrStackSize(final int slot, final int count) {
        return removeItem(slot, count);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(final int slot) {
        return removeItemNoUpdate(slot);
    }

    @Override
    public void setInventorySlotContents(final int slot, final ItemStack stack) {
        setItem(slot, stack);
    }

    @Override
    public String getInventoryName() {
        return this.customName != null ? this.customName : "";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return this.customName != null && !this.customName.isEmpty();
    }

    @Override
    public int getInventoryStackLimit() {
        return getMaxStackSize();
    }

    @Override
    public void markDirty() {
        // No-op by default; subclasses (e.g. TileEntity) can override to
        // trigger chunk save / change notification.
    }

    @Override
    public boolean isUseableByPlayer(final EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(final int slot, final ItemStack stack) {
        return true;
    }

    // ──── Iterable<ItemStack> ────

    @Override
    public Iterator<ItemStack> iterator() {
        return new ContainerIterator();
    }

    private class ContainerIterator implements Iterator<ItemStack> {
        private int index;

        @Override
        public boolean hasNext() {
            return this.index < items.length;
        }

        @Override
        public ItemStack next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return items[this.index++];
        }
    }

    // ──── Hook for subclasses ────

    /**
     * Called when the contents of a slot change. Subclasses (e.g. TileEntity)
     * can override to mark dirty, notify neighbours, etc.
     * <p>当槽位内容变更时调用。子类（如 TileEntity）可覆盖以标记脏、通知邻居等。</p>
     *
     * @param slot the changed slot index / 变更的槽位索引
     */
    protected void onContentsChanged(final int slot) {
        markDirty();
    }
}
