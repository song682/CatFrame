package decok.dfcdvadstf.catframe.mixin;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.CatFrameConfig;
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
     * 精确控制策略 — {@link ItemModel#handles(RenderPhase)} 是唯一的门控：
     * <ol>
     *   <li>注册了 ItemModel？否 → 走原版</li>
     *   <li>{@code handles(phase)} 返回 true？否 → 走原版</li>
     *   <li>是 → 取消原版，走自定义渲染</li>
     * </ol>
     * 不再使用 {@code hasItemModel} 布尔值判断。
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

        // 没有注册 ItemModel → 完全走原版（不用 hasItemModel 布尔值）
        ItemModel itemModel = VanillaModelManager.ModelRegistration.getRegisteredItemModel(item);
        if (itemModel == null) return;

        // 判断渲染阶段
        int thirdPersonView = Minecraft.getMinecraft().gameSettings.thirdPersonView;
        boolean isFirstPerson = (entity == Minecraft.getMinecraft().thePlayer
                && thirdPersonView == 0);
        RenderPhase phase = isFirstPerson
                ? RenderPhase.ITEM_HAND_FIRST_PERSON
                : RenderPhase.ITEM_HAND_THIRD_PERSON;

        // handles() 是精确控制面 — ItemModel 自己决定此阶段是否接管
        if (!itemModel.handles(phase)) {
            if (CatFrameConfig.shouldLogDebug()) {
                CatFrame.logger.info("[MixinItemRenderer] ItemModel.handles({})=false for {}, vanilla fallback",
                        phase, Item.itemRegistry.getNameForObject(item));
            }
            return;
        }

        // 接管 → cancel 原版，走自定义渲染
        if (CatFrameConfig.shouldLogDebug()) {
            CatFrame.logger.info("[MixinItemRenderer] rendering {} | phase={} | firstPerson={}",
                    Item.itemRegistry.getNameForObject(item), phase, isFirstPerson);
        }
        VanillaModelManager.PublicRenderAPI.renderItemInHand(itemStack, isFirstPerson);
        ci.cancel();
    }
}
