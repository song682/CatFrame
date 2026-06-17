package decok.dfcdvadstf.catframe.mixin;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.ItemModel;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {

    /**
     * 拦截 4 参数版本 {@code renderItem(EntityLivingBase, ItemStack, int, ItemRenderType)}，
     * 因为 3 参数版本内部直接委托给此重载（{@code this.renderItem(e, s, p, EQUIPPED)}）。
     * <p>
     * 第一人称手持调用 {@code renderItem(e, s, 0, EQUIPPED_FIRST_PERSON)}，
     * 第三人称/掉落物经由 3-param → 4-param(EQUIPPED)，两者均由此处拦截。
     * <p>
     * 统一判断逻辑：先问 {@link ItemModel#handles(RenderPhase)} 决定是否接管。
     */
    @Inject(method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void catframe$onRenderItemInHand(
            net.minecraft.entity.EntityLivingBase entity,
            ItemStack itemStack, int pass,
            ItemRenderType type,
            CallbackInfo ci) {
        if (itemStack == null) return;
        Item item = itemStack.getItem();
        if (item == null) return;
        boolean hasModel = VanillaModelManager.ModelRegistration.hasItemModel(item);
        int thirdPersonView = Minecraft.getMinecraft().gameSettings.thirdPersonView;
        CatFrame.logger.info("[MixinItemRenderer] renderItem(4) | item={} | type={} | thirdPersonView={} | hasItemModel={}",
                Item.itemRegistry.getNameForObject(item),
                type,
                thirdPersonView,
                hasModel);
        if (hasModel) {
            // 判断渲染阶段：第一人称手持有单独的 phase
            boolean isFirstPerson = (entity == Minecraft.getMinecraft().thePlayer
                    && thirdPersonView == 0);
            RenderPhase phase = isFirstPerson
                    ? RenderPhase.ITEM_HAND_FIRST_PERSON
                    : RenderPhase.ITEM_HAND_THIRD_PERSON;

            ItemModel itemModel = VanillaModelManager.ModelRegistration.getRegisteredItemModel(item);
            if (itemModel != null && !itemModel.handles(phase)) {
                // ItemModel 不接管此阶段 → 走原版渲染
                CatFrame.logger.info("[MixinItemRenderer] ItemModel.handles({})=false, letting vanilla render",
                        phase);
                return;
            }
            // ItemModel 接管 → cancel 原版，走自定义渲染
            VanillaModelManager.PublicRenderAPI.renderItemInHand(itemStack, isFirstPerson);
            ci.cancel();
        }
    }
}
