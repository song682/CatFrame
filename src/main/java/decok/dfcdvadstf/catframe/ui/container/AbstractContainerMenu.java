package decok.dfcdvadstf.catframe.ui.container;

import decok.dfcdvadstf.catframe.ui.container.data.ContainerData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Menu logic layer of the three-layer container abstraction. Extends vanilla
 * {@link Container} (required by the 1.7.10 framework) but delegates storage
 * to an {@link AbstractContainer} via composition and re-implements
 * {@link #slotClick} as a thin dispatcher over the {@link ContainerInput}
 * enum.<br>
 * The carried (cursor-held) item is managed through {@link #getCarried()} /
 * {@link #setCarried(ItemStack)}, bridging internally to
 * {@code InventoryPlayer.getItemStack()} so that vanilla sync still works.
 * </p>
 * <p>
 * 三层容器抽象的菜单逻辑层。继承原版 {@link Container}（1.7.10 框架强制要求），
 * 但通过组合将存储委托给 {@link AbstractContainer}，并将 {@link #slotClick}
 * 重新实现为基于 {@link ContainerInput} 枚举的薄分发器。<br>
 * 游标物品通过 {@link #getCarried()} / {@link #setCarried(ItemStack)} 管理，
 * 内部桥接到 {@code InventoryPlayer.getItemStack()} 以保证原版同步正常运作。
 * </p>
 */
public abstract class AbstractContainerMenu extends Container {

    /** The data container this menu operates on. / 本菜单操作的数据容器。 */
    protected final AbstractContainer container;

    /** Data slots for integer sync (furnace progress, etc.). / 整数同步数据槽。 */
    private final List<ContainerData> dataSlots = new ArrayList<ContainerData>();

    /** Previous values for change detection. / 用于变更检测的前值。 */
    private final List<int[]> dataSlotPreviousValues = new ArrayList<int[]>();

    // ──── Construction ────

    /**
     * @param container the data container to operate on / 要操作的数据容器
     */
    protected AbstractContainerMenu(final AbstractContainer container) {
        this.container = container;
    }

    // ──── Carried item (cursor-held) ────

    /**
     * Get the item stack currently held by the player's cursor.
     * <p>获取玩家游标当前持有的物品堆。</p>
     *
     * @param player the player / 玩家
     * @return the carried stack, or {@code null} if none / 持有的物品堆，无则 {@code null}
     */
    public ItemStack getCarried(final EntityPlayer player) {
        return player.inventory.getItemStack();
    }

    /**
     * Set the item stack the player's cursor is holding.
     * <p>设置玩家游标持有的物品堆。</p>
     *
     * @param player the player / 玩家
     * @param stack  the stack to hold (may be {@code null}) / 要持有的物品堆（可为 {@code null}）
     */
    public void setCarried(final EntityPlayer player, final ItemStack stack) {
        player.inventory.setItemStack(stack);
    }

    // ──── Slot helpers ────

    /**
     * Add the standard 36-slot player inventory (27 main + 9 hotbar) at the
     * given pixel offset.
     * <p>在给定像素偏移处添加标准 36 格玩家背包（27 主背包 + 9 热键栏）。</p>
     *
     * @param playerInv the player's inventory / 玩家背包
     * @param left      x offset in pixels / 像素 x 偏移
     * @param top       y offset in pixels / 像素 y 偏移
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
     * <p>添加一个将被追踪和同步的 {@link ContainerData}。</p>
     *
     * @param data the data to add / 要添加的数据
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
     * <p>
     * 将原版魔数 {@code mode} 翻译为 {@link ContainerInput}，
     * 再委托给对应的 {@code onXxx} 方法。子类应覆盖 {@code onXxx} 方法而非本方法。
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
     * <p>处理 PICKUP 点击（左/右键拾取或放置）。</p>
     *
     * @return the resulting stack, or {@code null} / 结果物品堆，无则 {@code null}
     */
    protected ItemStack onPickup(final int slotIndex, final int button, final EntityPlayer player) {
        // Delegate to vanilla's default slotClick for PICKUP (mode 0)
        return super.slotClick(slotIndex, button, 0, player);
    }

    /**
     * Handle a QUICK_MOVE click (shift-click transfer).
     * <p>处理 QUICK_MOVE 点击（shift+点击转移）。</p>
     */
    protected ItemStack onQuickMove(final int slotIndex, final EntityPlayer player) {
        return this.transferStackInSlot(player, slotIndex);
    }

    /**
     * Handle a SWAP click (swap with hotbar slot via number key).
     * <p>处理 SWAP 点击（通过数字键与热键栏交换）。</p>
     */
    protected ItemStack onSwap(final int slotIndex, final int button, final EntityPlayer player) {
        return super.slotClick(slotIndex, button, 2, player);
    }

    /**
     * Handle a CLONE click (creative-mode middle-click copy).
     * <p>处理 CLONE 点击（创造模式中键复制）。</p>
     */
    protected ItemStack onClone(final int slotIndex, final EntityPlayer player) {
        return super.slotClick(slotIndex, 0, 3, player);
    }

    /**
     * Handle a THROW click (drop item via Q key).
     * <p>处理 THROW 点击（通过 Q 键丢弃物品）。</p>
     */
    protected ItemStack onThrow(final int slotIndex, final int button, final EntityPlayer player) {
        return super.slotClick(slotIndex, button, 4, player);
    }

    /**
     * Handle a QUICK_CRAFT action (drag-distribute items across slots).
     * <p>处理 QUICK_CRAFT 操作（拖拽分配物品到多个槽位）。</p>
     */
    protected ItemStack onQuickCraft(final int slotIndex, final int button, final EntityPlayer player) {
        return super.slotClick(slotIndex, button, 5, player);
    }

    /**
     * Handle a PICKUP_ALL click (double-click to collect all matching items).
     * <p>处理 PICKUP_ALL 点击（双击收集所有匹配物品）。</p>
     */
    protected ItemStack onPickupAll(final int slotIndex, final int button, final EntityPlayer player) {
        return super.slotClick(slotIndex, button, 6, player);
    }

    // ──── Abstract method ────

    /**
     * {@inheritDoc}
     * <p>Subclasses must implement to define accessibility rules.</p>
     * <p>子类必须实现以定义可访问性规则。</p>
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
     * <p>
     * <b>必须调用 super：</b>覆盖此方法的子类<b>必须</b>调用
     * {@code super.detectAndSendChanges()} 以维持同步。
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
     * <p>
     * <b>必须调用 super：</b>覆盖此方法的子类<b>必须</b>调用
     * {@code super.onContainerClosed(player)} 以归还游标物品。
     * </p>
     */
    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
    }

    // ──── Accessors ────

    /**
     * @return the data container this menu operates on / 本菜单操作的数据容器
     */
    public AbstractContainer getContainer() {
        return this.container;
    }

    // ──── Data slot sync helpers (internal) ────

    /**
     * Check all data slots for changes and broadcast to listeners.
     * Called automatically by {@link #detectAndSendChanges()}.
     * <p>检查所有数据槽的变更并向监听器广播。由 {@link #detectAndSendChanges()} 自动调用。</p>
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
