package decok.dfcdvadstf.catframe.ui.screens.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import decok.dfcdvadstf.catframe.ui.screens.container.data.ContainerData;

/**
 * <p>
 * Menu logic layer of the three-layer container abstraction. Extends vanilla
 * {@link net.minecraft.inventory.Container} (required by the 1.7.10 framework)
 * but delegates storage to a {@link Container} via composition and re-implements
 * {@link #slotClick} as a thin dispatcher over the {@link ContainerInput}
 * enum.<br>
 * The carried (cursor-held) item is managed through {@link #getCarried(EntityPlayer)} /
 * {@link #setCarried(EntityPlayer, ItemStack)}, bridging internally to
 * {@code InventoryPlayer.getItemStack()} so that vanilla sync still works.
 * </p>
 */
public abstract class AbstractContainerMenu extends net.minecraft.inventory.Container {

    /** The data container this menu operates on. */
    protected final Container container;

    /** Data slots for integer sync (furnace progress, etc.).  */
    private final List<ContainerData> dataSlots = new ArrayList<ContainerData>();

    /** Previous values for change detection. */
    private final List<int[]> dataSlotPreviousValues = new ArrayList<int[]>();

    // ──── Construction ────

    /**
     * @param container the data container to operate on
     */
    protected AbstractContainerMenu(final Container container) {
        this.container = container;
    }

    // ──── Carried item (cursor-held) ────

    /**
     * Get the item stack currently held by the player's cursor.
     *
     * @param player the player
     * @return the carried stack, or {@code null} if none
     */
    public ItemStack getCarried(final EntityPlayer player) {
        return player.inventory.getItemStack();
    }

    /**
     * Set the item stack the player's cursor is holding.
     *
     * @param player the player
     * @param stack  the stack to hold (may be {@code null})m
     */
    public void setCarried(final EntityPlayer player, final ItemStack stack) {
        player.inventory.setItemStack(stack);
    }

    // ──── Slot helpers ────

    /**
     * Add the standard 36-slot player inventory (27 main + 9 hotbar) at the
     * given pixel offset.
     *
     * @param playerInv the player's inventory
     * @param left      x offset in pixels
     * @param top       y offset in pixels
     */
    protected void addStandardInventorySlots(final InventoryPlayer playerInv, final int left, final int top) {
        // 27 main inventory slots (3 rows × 9 columns)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, left + col * 18, top + row * 18));
            }
        }
        // 9 hotbar slots
        for (int col = 0; col < 9; col++) {
            this.addSlotToContainer(new Slot(playerInv, col, left + col * 18, top + 58));
        }
    }

    // ──── Data slots ────

    /**
     * Add a {@link ContainerData} to be tracked and synced.
     *
     * @param data the data to add
     */
    protected void addDataSlot(final ContainerData data) {
        this.dataSlots.add(data);
        this.dataSlotPreviousValues.add(new int[data.getCount()]);
    }

    // ──── slotClick → ContainerInput dispatch ────

    /**
     * {@inheritDoc}
     * <p>
     * Translates the vanilla magic-number {@code mode} into a
     * {@link ContainerInput} and delegates to the corresponding
     * {@code onXxx} method. Subclasses should override the {@code onXxx}
     * methods rather than this one.
     * </p>
     */
    @Override
    public ItemStack slotClick(final int slotIndex, final int button, final int mode, final EntityPlayer player) {
        final ContainerInput input = ContainerInput.byId(mode);
        switch (input) {
            case PICKUP:
                return onPickup(slotIndex, button, player);
            case QUICK_MOVE:
                return onQuickMove(slotIndex, player);
            case SWAP:
                return onSwap(slotIndex, button, player);
            case CLONE:
                return onClone(slotIndex, player);
            case THROW:
                return onThrow(slotIndex, button, player);
            case QUICK_CRAFT:
                return onQuickCraft(slotIndex, button, player);
            case PICKUP_ALL:
                return onPickupAll(slotIndex, button, player);
            default:
                return null;
        }
    }

    // ──── Click-type handlers (override in subclasses) ────

    /**
     * Handle a PICKUP click (left/right click to pick up or place).
     *
     * @return the resulting stack, or {@code null} / 结果物品堆，无则 {@code null}
     */
    protected ItemStack onPickup(final int slotIndex, final int button, final EntityPlayer player) {
        // Delegate to vanilla's default slotClick for PICKUP (mode 0)
        return super.slotClick(slotIndex, button, 0, player);
    }

    /**
     * Handle a QUICK_MOVE click (shift-click transfer).
     */
    protected ItemStack onQuickMove(final int slotIndex, final EntityPlayer player) {
        return this.transferStackInSlot(player, slotIndex);
    }

    /**
     * Handle a SWAP click (swap with hotbar slot via number key).
     */
    protected ItemStack onSwap(final int slotIndex, final int button, final EntityPlayer player) {
        return super.slotClick(slotIndex, button, 2, player);
    }

    /**
     * Handle a CLONE click (creative-mode middle-click copy).
     */
    protected ItemStack onClone(final int slotIndex, final EntityPlayer player) {
        return super.slotClick(slotIndex, 0, 3, player);
    }

    /**
     * Handle a THROW click (drop item via Q key).
     */
    protected ItemStack onThrow(final int slotIndex, final int button, final EntityPlayer player) {
        return super.slotClick(slotIndex, button, 4, player);
    }

    /**
     * Handle a QUICK_CRAFT action (drag-distribute items across slots).
     */
    protected ItemStack onQuickCraft(final int slotIndex, final int button, final EntityPlayer player) {
        return super.slotClick(slotIndex, button, 5, player);
    }

    /**
     * Handle a PICKUP_ALL click (double-click to collect all matching items).
     */
    protected ItemStack onPickupAll(final int slotIndex, final int button, final EntityPlayer player) {
        return super.slotClick(slotIndex, button, 6, player);
    }

    // ──── Abstract method ────

    /**
     * {@inheritDoc}
     * <p>Subclasses must implement to define accessibility rules.</p>
     */
    @Override
    public abstract boolean canInteractWith(EntityPlayer player);

    // ──── Lifecycle hooks ────

    /**
     * {@inheritDoc}
     * <p>
     * <b>CallSuper:</b> Subclasses that override this method <b>must</b>
     * call {@code super.detectAndSendChanges()} to maintain sync.
     * </p>
     */
    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        syncDataSlots();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>CallSuper:</b> Subclasses that override this method <b>must</b>
     * call {@code super.onContainerClosed(player)} to return the carried item.
     * </p>
     */
    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
    }

    // ──── Accessors ────

    /**
     * @return the data container this menu operates on
     */
    public Container getContainer() {
        return this.container;
    }

    // ──── Data slot sync helpers (internal) ────

    /**
     * Check all data slots for changes and broadcast to listeners.
     * Called automatically by {@link #detectAndSendChanges()}.
     */
    protected void syncDataSlots() {
        int flatIndex = 0;
        for (int d = 0; d < this.dataSlots.size(); d++) {
            final ContainerData data = this.dataSlots.get(d);
            final int[] prev = this.dataSlotPreviousValues.get(d);
            for (int i = 0; i < data.getCount(); i++) {
                final int current = data.get(i);
                if (current != prev[i]) {
                    prev[i] = current;
                    // Broadcast to all crafting listeners
                    for (int j = 0; j < this.crafters.size(); j++) {
                        ((net.minecraft.inventory.ICrafting) this.crafters.get(j))
                                .sendProgressBarUpdate(this, flatIndex, current);
                    }
                }
                flatIndex++;
            }
        }
    }
}
