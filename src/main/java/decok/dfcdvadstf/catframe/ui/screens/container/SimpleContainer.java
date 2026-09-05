package decok.dfcdvadstf.catframe.ui.screens.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * <p>
 * A simple array-backed {@link Container} implementation. Provides the
 * concrete storage logic that the {@link Container} interface cannot
 * (fields, constructors).<br>
 * Counterpart of the high-version Minecraft {@code SimpleContainer} class.
 * </p>
 * <p>
 * 基于数组的简单 {@link Container} 实现。提供 {@link Container} 接口无法
 * 承载的具体存储逻辑（字段、构造器）。<br>
 * 对标高版本 Minecraft 的 {@code SimpleContainer} 类。
 * </p>
 */
public class SimpleContainer implements Container {

    /** Backing storage. / 底层存储。 */
    private final ItemStack[] items;

    /** Custom display name, or {@code null} for none. / 自定义显示名，或 {@code null}。 */
    private String customName;

    /**
     * @param size the number of slots / 槽位数量
     */
    public SimpleContainer(final int size) {
        this.items = new ItemStack[size];
    }

    // ──── Container API ────

    @Override
    public int getContainerSize() {
        return this.items.length;
    }

    @Override
    public boolean isEmpty() {
        for (final ItemStack stack : this.items) {
            if (stack != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(final int slot) {
        if (slot < 0 || slot >= this.items.length) {
            return null;
        }
        return this.items[slot];
    }

    @Override
    public void setItem(final int slot, final ItemStack stack) {
        if (slot < 0 || slot >= this.items.length) {
            return;
        }
        this.items[slot] = stack;
        if (stack != null && stack.stackSize > getMaxStackSize()) {
            stack.stackSize = getMaxStackSize();
        }
        setChanged();
    }

    @Override
    public ItemStack removeItem(final int slot, final int count) {
        if (slot < 0 || slot >= this.items.length || this.items[slot] == null) {
            return null;
        }
        if (this.items[slot].stackSize <= count) {
            final ItemStack result = this.items[slot];
            this.items[slot] = null;
            setChanged();
            return result;
        }
        final ItemStack split = this.items[slot].splitStack(count);
        if (this.items[slot].stackSize == 0) {
            this.items[slot] = null;
        }
        setChanged();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(final int slot) {
        if (slot < 0 || slot >= this.items.length) {
            return null;
        }
        final ItemStack result = this.items[slot];
        this.items[slot] = null;
        return result;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < this.items.length; i++) {
            this.items[i] = null;
        }
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void setChanged() {
        // No-op by default; subclasses (e.g. TileEntity) can override to
        // trigger chunk save / change notification.
    }

    @Override
    public boolean stillValid(final EntityPlayer player) {
        return true;
    }

    // ──── Custom name ────

    /**
     * Set a custom display name for this container.
     * <p>设置本容器的自定义显示名。</p>
     */
    public void setCustomName(final String name) {
        this.customName = name;
    }

    @Override
    public String getInventoryName() {
        return this.customName != null ? this.customName : "";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return this.customName != null && !this.customName.isEmpty();
    }
}
