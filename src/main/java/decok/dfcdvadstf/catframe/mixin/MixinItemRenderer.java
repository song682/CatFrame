package decok.dfcdvadstf.catframe.mixin;

import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into ItemRenderer to intercept first-person / third-person
 * item-in-hand rendering.  If the item has a JSON model registered
 * with {@link VanillaModelManager}, the baked 3D quads are rendered
 * instead of vanilla's flat 2D sprite.
 */
@Mixin(ItemRenderer.class)
public class MixinItemRenderer {

    /**
     * Inject at HEAD of renderItem to check whether the item has a
     * JSON model.  If so, render the baked quads and cancel vanilla
     * processing.  Targets the 3-parameter overload (the 4-param
     * overload is called internally by this one).
     */
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void catframe$onRenderItemInHand(
            net.minecraft.entity.EntityLivingBase entity,
            ItemStack itemStack, int pass,
            CallbackInfo ci) {
        if (itemStack == null) return;
        Item item = itemStack.getItem();
        if (item != null && VanillaModelManager.hasItemModel(item)) {
            VanillaModelManager.renderItemInHand(itemStack);
            ci.cancel();
        }
    }
}
