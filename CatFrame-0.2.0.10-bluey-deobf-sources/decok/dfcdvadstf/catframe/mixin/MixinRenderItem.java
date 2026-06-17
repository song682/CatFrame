package decok.dfcdvadstf.catframe.mixin;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.ItemModel;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItem.class)
public class MixinRenderItem {

    /**
     * 拦截 6 参数版本（实现体），因为 5 参数版本内部委托给此重载。
     * <p>
     * 策略：先问 {@link ItemModel#handles(RenderPhase)} 是否接管 ITEM_GUI 阶段。
     * 不接管 → 不 cancel，走原版 2D 图标渲染；
     * 接管 → cancel 原版，走 {@link VanillaModelManager.PublicRenderAPI#renderItem(ItemStack)}。
     */
    @Inject(method = "renderItemIntoGUI", at = @At("HEAD"), cancellable = true)
    private void catframe$onRenderItemIntoGUI(FontRenderer fontRenderer,
                                              TextureManager textureManager,
                                              ItemStack itemStack, int x, int y,
                                              CallbackInfo ci) {
        if (itemStack == null) return;
        Item item = itemStack.getItem();
        if (item == null) return;
        boolean hasModel = VanillaModelManager.ModelRegistration.hasItemModel(item);
        CatFrame.logger.info("[MixinRenderItem] renderItemIntoGUI | item={} | hasItemModel={}",
                Item.itemRegistry.getNameForObject(item), hasModel);
        if (hasModel) {
            ItemModel itemModel = VanillaModelManager.ModelRegistration.getRegisteredItemModel(item);
            if (itemModel != null && !itemModel.handles(RenderPhase.ITEM_GUI)) {
                CatFrame.logger.info("[MixinRenderItem] ItemModel.handles(ITEM_GUI)=false, letting vanilla render GUI for {}",
                        Item.itemRegistry.getNameForObject(item));
                return;
            }
            VanillaModelManager.PublicRenderAPI.renderItem(itemStack);
            ci.cancel();
        }
    }
}
