package decok.dfcdvadstf.catframe.mixin.middle;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import decok.dfcdvadstf.catframe.component.*;

/**
 * Mixin into ItemStack to add DataComponent support.
 * <p>
 * Adds a {@link PatchedDataComponentMap} field and hooks into constructor and serialization
 * to bridge old NBT (stackTagCompound) with the new component system.
 * <p>
 * Integration strategy:
 * <ul>
 *   <li>Components are initialized from {@link ComponentDefaults} per Item type</li>
 *   <li>At load time, old NBT fields are migrated into components</li>
 *   <li>At save time, components are synced back to stackTagCompound for backwards compat</li>
 *   <li>New component API reads/writes through the component system directly</li>
 * </ul>
 */
@Mixin(ItemStack.class)
public class MixinItemStack {

    @Shadow
    public NBTTagCompound stackTagCompound;

    @Shadow
    public int itemDamage;

    @Unique
    private PatchedDataComponentMap catframe$components;

    // ========== 主构造器：ItemStack(Item, int, int) ==========

    @Inject(method = "<init>(Lnet/minecraft/item/Item;II)V", at = @At("RETURN"))
    private void catframe$initFromItem(Item item, int count, int damage, CallbackInfo ci) {
        DataComponentMap defaults = ComponentDefaults.getDefaults(item);
        this.catframe$components = new PatchedDataComponentMap(defaults);
        if (damage > 0) {
            this.catframe$components.set(RegisteredComponents.DAMAGE, damage);
        }
        // 如果已有 NBT（如 copy/splitStack 后设了 stackTagCompound），迁移
        if (this.stackTagCompound != null && !this.stackTagCompound.hasNoTags()) {
            ComponentMigration.migrate(this.stackTagCompound, this.catframe$components);
        }
    }

    // ========== readFromNBT 拦截（反序列化） ==========

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void catframe$afterReadFromNBT(NBTTagCompound tag, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        this.catframe$components = new PatchedDataComponentMap(
                ComponentDefaults.getDefaults(self.getItem()));
        if (tag != null && !tag.hasNoTags()) {
            ComponentMigration.migrate(tag, this.catframe$components);
        }
    }

    // ========== writeToNBT 拦截（序列化） ==========

    @Inject(method = "writeToNBT", at = @At("HEAD"))
    private void catframe$beforeWriteToNBT(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) {
        if (this.catframe$components != null) {
            ensureTagCompound();
            ComponentMigration.syncToNBT(this.stackTagCompound, this.catframe$components);
        }
    }

    // ========== copy() 拦截 ==========

    @Inject(method = "copy", at = @At("RETURN"))
    private void catframe$afterCopy(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack copy = cir.getReturnValue();
        MixinItemStack target = (MixinItemStack) (Object) copy;
        if (this.catframe$components != null) {
            target.catframe$components = this.catframe$components.copy();
        }
    }

    // ========== splitStack(int) 拦截 ==========

    @Inject(method = "splitStack", at = @At("RETURN"))
    private void catframe$afterSplit(int amount, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack split = cir.getReturnValue();
        MixinItemStack target = (MixinItemStack) (Object) split;
        if (this.catframe$components != null) {
            target.catframe$components = this.catframe$components.copy();
        }
    }

    // ========== setTagCompound 拦截（旧 NBT 兼容） ==========

    @Inject(method = "setTagCompound", at = @At("RETURN"))
    private void catframe$afterSetTagCompound(NBTTagCompound tag, CallbackInfo ci) {
        if (tag != null && this.catframe$components != null) {
            ComponentMigration.migrate(tag, this.catframe$components);
        }
    }

    // ========== 组件系统公共 API ==========

    /**
     * 获取此 ItemStack 的组件映射。
     */
    public PatchedDataComponentMap catframe$getComponents() {
        if (this.catframe$components == null) {
            ItemStack self = (ItemStack) (Object) this;
            this.catframe$components = new PatchedDataComponentMap(
                    ComponentDefaults.getDefaults(self.getItem()));
            if (this.stackTagCompound != null && !this.stackTagCompound.hasNoTags()) {
                ComponentMigration.migrate(this.stackTagCompound, this.catframe$components);
            }
        }
        return this.catframe$components;
    }

    // ========== 内部辅助 ==========

    @Unique
    private void ensureTagCompound() {
        if (this.stackTagCompound == null) {
            this.stackTagCompound = new NBTTagCompound();
        }
    }
}
