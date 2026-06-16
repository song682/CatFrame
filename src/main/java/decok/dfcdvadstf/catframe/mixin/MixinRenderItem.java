package decok.dfcdvadstf.catframe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.model.ItemModel;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.block.Block;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.util.IIcon;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItem.class)
public class MixinRenderItem {

    /**
     * 标记本次 GUI 渲染调用是否已由 CatFrame 接管。
     * 用于多 pass 物品（renderIcon 在循环中被调用多次），避免重复渲染。
     * 在 5-param 委托入口（@Redirect）或 renderItemAndEffectIntoGUI 入口（@WrapOperation）重置。
     */
    private boolean catframe$renderedGUI = false;

    // ==================== 5-param 入口：Slice 定位委托调用 ====================

    /**
     * 5-param 方法体仅含一个 INVOKE（委托到 6-param），
     * 用 @Slice + @At("INVOKE", ordinal = 0) 避免 target method mapping 错误。
     */
    @Inject(
        method = "renderItemIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;II)V",
        at = @At(value = "INVOKE", ordinal = 0),
        slice = @Slice(
            from = @At(value = "HEAD")
        ),
        cancellable = true
    )
    private void catframe$resetGuiAtInvoke(FontRenderer fr, TextureManager tm, ItemStack stack,
                                            int x, int y, CallbackInfo ci) {
        this.catframe$renderedGUI = false;
        if (catframe$tryRenderGUI(stack)) {
            ci.cancel();
        }
    }

    // ==================== renderItemAndEffectIntoGUI 路径 ====================

    /**
     * {@code renderItemAndEffectIntoGUI} 直接调用 6-param（不走 5-param 委托），
     * 因此需要在 {@code ForgeHooksClient.renderInventoryItem()} 处重置 flag。
     * 此调用恰好在 6-param 之前，是理想的 flag 重置点。
     */
    @WrapOperation(
        method = "renderItemAndEffectIntoGUI",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraftforge/client/ForgeHooksClient;renderInventoryItem(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;ZFFF)Z",
                 remap = false),
        remap = false)
    private boolean catframe$resetFlagThenRenderInv(RenderBlocks rb, TextureManager tm, ItemStack stack,
                                                     boolean color, float z, float x, float y,
                                                     Operation<Boolean> original) {
        this.catframe$renderedGUI = false;
        return original.call(rb, tm, stack, color, z, x, y);
    }

    // ==================== 6-param：包裹渲染调用点（兜底） ====================

    /**
     * 包裹分支 A：{@code RenderBlocks.renderBlockAsItem()}（ItemBlock 3D 方块物品）。
     */
    @WrapOperation(
        method = "renderItemIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IIZ)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockAsItem(Lnet/minecraft/block/Block;IF)V"))
    private void catframe$wrapBlockAsItem(Block block, int meta, float brightness,
                                          Operation<Void> original,
                                          @Local(argsOnly = true) ItemStack stack) {
        if (catframe$tryRenderGUI(stack)) return;
        original.call(block, meta, brightness);
    }

    /**
     * 包裹分支 B/C：{@code renderIcon()}（多 pass 和单 pass 图标路径）。
     * 同一 @WrapOperation 覆盖调用点 line 515（多 pass 循环）和 line 555（单 pass）。
     * 首次渲染后 {@link #catframe$renderedGUI} flag 阻止后续重复渲染。
     */
    @WrapOperation(
        method = "renderItemIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IIZ)V",
        at = @At(value = "INVOKE",
                  target = "Lnet/minecraft/client/renderer/entity/RenderItem;renderIcon(IILnet/minecraft/util/IIcon;II)V"))
    private void catframe$wrapRenderIcon(int x, int y, IIcon icon, int w, int h,
                                          Operation<Void> original,
                                          @Local(argsOnly = true) ItemStack stack) {
        if (catframe$tryRenderGUI(stack)) return;
        original.call(x, y, icon, w, h);
    }

    // ==================== 统一帮助方法 ====================

    /**
     * 尝试用 CatFrame 管线渲染物品 GUI。
     *
     * @return true=已由 CatFrame 接管
     */
    private boolean catframe$tryRenderGUI(ItemStack stack) {
        if (stack == null || this.catframe$renderedGUI) return false;
        Item item = stack.getItem();
        if (item == null) return false;
        ItemModel model = VanillaModelManager.ModelRegistration.getRegisteredItemModel(item);
        if (model == null || !model.handles(RenderPhase.ITEM_GUI)) return false;
        this.catframe$renderedGUI = true;
        if (CatFrameConfig.shouldLogDebug()) {
            CatFrame.logger.info("[MixinRenderItem] CatFrame rendering {} GUI",
                Item.itemRegistry.getNameForObject(item));
        }
        VanillaModelManager.PublicRenderAPI.renderItem(stack);
        return true;
    }
}
