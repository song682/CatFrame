package decok.dfcdvadstf.catframe.ui.screens.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * <p>
 * Pure data container interface — the storage layer of the three-layer
 * container abstraction. Extends {@link IInventory} so that vanilla
 * TileEntities and automation (hoppers, pipes) can interact with
 * implementations, but knows nothing about {@code Slot}, click handling,
 * or network sync.<br>
 * Counterpart of the high-version Minecraft {@code Container} interface
 * (storage-only semantics).
 * </p>
 * <p>
 * 纯数据容器接口——三层容器抽象的存储层。扩展 {@link IInventory} 以便原版
 * TileEntity 与自动化设施（漏斗、管道）可以与之交互，但不知道 {@code Slot}、
 * 点击处理或网络同步。<br>
 * 对标高版本 Minecraft 的 {@code Container} 接口（纯存储语义）。
 * </p>
 */
public interface Container extends IInventory, Iterable<ItemStack> {

    // ──── Modern API (abstract — implementations must provide) ────

    /**
     * @return the number of slots in this container / 本容器的槽位数量
     */
    int getContainerSize();

    /**
     * @return {@code true} if every slot is empty / 所有槽位均为空时返回 {@code true}
     */
    boolean isEmpty();

    /**
     * Get the stack in the given slot.
     * <p>获取指定槽位的物品堆。</p>
     *
     * @param slot the slot index / 槽位索引
     * @return the stack, or {@code null} if empty / 物品堆，空则为 {@code null}
     */
    ItemStack getItem(int slot);

    /**
     * Set the stack in the given slot.
     * <p>设置指定槽位的物品堆。</p>
     *
     * @param slot  the slot index / 槽位索引
     * @param stack the stack to place (may be {@code null}) / 要放入的物品堆（可为 {@code null}）
     */
    void setItem(int slot, ItemStack stack);

    /**
     * Remove up to {@code count} items from the given slot, returning them as
     * a new stack.
     * <p>从指定槽位移除最多 {@code count} 个物品，以新堆返回。</p>
     *
     * @param slot  the slot index / 槽位索引
     * @param count maximum number to remove / 最大移除数量
     * @return the removed stack, or {@code null} / 被移除的物品堆，无则 {@code null}
     */
    ItemStack removeItem(int slot, int count);

    /**
     * Remove the entire stack from the given slot without triggering a
     * change notification.
     * <p>从指定槽位移除整个物品堆，不触发变更通知。</p>
     *
     * @param slot the slot index / 槽位索引
     * @return the removed stack, or {@code null} / 被移除的物品堆，无则 {@code null}
     */
    ItemStack removeItemNoUpdate(int slot);

    /**
     * Clear all slots.
     * <p>清空所有槽位。</p>
     */
    void clearContent();

    /**
     * @return the maximum stack size for this container / 本容器的最大堆叠大小
     */
    int getMaxStackSize();

    /**
     * Called when the contents of the container change. Implementations
     * should override to mark dirty, notify neighbours, etc.
     * <p>当容器内容变更时调用。实现应覆盖以标记脏、通知邻居等。</p>
     */
    void setChanged();

    /**
     * @param player the player to check / 要检查的玩家
     * @return whether this container is still usable by the player
     *         / 本容器是否仍可被该玩家使用
     */
    boolean stillValid(EntityPlayer player);

    // ──── IInventory bridge (default methods delegating to modern API) ────

    @Override
    default int getSizeInventory() {
        return getContainerSize();
    }

    @Override
    default ItemStack getStackInSlot(final int slot) {
        return getItem(slot);
    }

    @Override
    default ItemStack decrStackSize(final int slot, final int count) {
        return removeItem(slot, count);
    }

    @Override
    default ItemStack getStackInSlotOnClosing(final int slot) {
        return removeItemNoUpdate(slot);
    }

    @Override
    default void setInventorySlotContents(final int slot, final ItemStack stack) {
        setItem(slot, stack);
    }

    @Override
    default String getInventoryName() {
        return "";
    }

    @Override
    default boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    default int getInventoryStackLimit() {
        return getMaxStackSize();
    }

    @Override
    default void markDirty() {
        setChanged();
    }

    @Override
    default boolean isUseableByPlayer(final EntityPlayer player) {
        return stillValid(player);
    }

    @Override
    default void openInventory() {
    }

    @Override
    default void closeInventory() {
    }

    @Override
    default boolean isItemValidForSlot(final int slot, final ItemStack stack) {
        return true;
    }

    // ──── Iterable<ItemStack> ────

    @Override
    default Iterator<ItemStack> iterator() {
        return new ContainerIterator(this);
    }

    /**
     * Shared iterator implementation for Container.
     * <p>Container 的共享迭代器实现。</p>
     */
    class ContainerIterator implements Iterator<ItemStack> {
        private final Container container;
        private int index;

        public ContainerIterator(final Container container) {
            this.container = container;
        }

        @Override
        public boolean hasNext() {
            return this.index < this.container.getContainerSize();
        }

        @Override
        public ItemStack next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return this.container.getItem(this.index++);
        }
    }
}
