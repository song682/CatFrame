package decok.dfcdvadstf.catframe.ui.screens.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Pure data container interface — the storage layer of the three-layer
 * container abstraction. Extends {@link IInventory} so that vanilla
 * TileEntities and automation (hoppers, pipes) can interact with
 * implementations, but knows nothing about {@code Slot}, click handling,
 * or network sync.<br>
 * Counterpart of the high-version Minecraft {@code Container} interface
 * (storage-only semantics).
 */
public interface Container extends IInventory, Iterable<ItemStack> {

    // ──── Modern API (abstract — implementations must provide) ────

    /**
     * @return the number of slots in this container 
     */
    int getContainerSize();

    /**
     * @return {@code true} if every slot is empty 
     */
    boolean isEmpty();

    /**
     * Get the stack in the given slot.
     * @param slot the slot index
     * @return the stack, or {@code null} if empty
     */
    ItemStack getItem(int slot);

    /**
     * Set the stack in the given slot.+
     * @param slot  the slot index
     * @param stack the stack to place (may be {@code null})
     */
    void setItem(int slot, ItemStack stack);

    /**
     * Remove up to {@code count} items from the given slot, returning them as
     * a new stack.
     *
     * @param slot  the slot index
     * @param count maximum number to remove
     * @return the removed stack, or {@code null}
     */
    ItemStack removeItem(int slot, int count);

    /**
     * Remove the entire stack from the given slot without triggering a
     * change notification.
     *
     * @param slot the slot index
     * @return the removed stack, or {@code null}
     */
    ItemStack removeItemNoUpdate(int slot);

    /**
     * Clear all slots.
     */
    void clearContent();

    /**
     * @return the maximum stack size for this container
     */
    int getMaxStackSize();

    /**
     * Called when the contents of the container change. Implementations
     * should override to mark dirty, notify neighbours, etc.
     */
    void setChanged();

    /**
     * @param player the player to check
     * @return whether this container is still usable by the player
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
