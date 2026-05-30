package decok.dfcdvadstf.catframe.mixin;

import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into RenderItem to intercept GUI item rendering.
 * If an item has a registered JSON model, render it using VanillaModelManager.
 */
@Mixin(RenderItem.class)
public class MixinRenderItem {

    /**
     * Inject into renderItemIntoGUI to override item icon rendering with JSON model.
     */
    @Inject(method = "renderItemIntoGUI", at = @At("HEAD"), cancellable = true)
    private void catframe$onRenderItemIntoGUI(net.minecraft.client.gui.FontRenderer fontRenderer,
                                               net.minecraft.client.renderer.texture.TextureManager textureManager,
                                               ItemStack itemStack, int x, int y, CallbackInfo ci) {
        if (itemStack == null) return;
        Item item = itemStack.getItem();
        if (item != null && VanillaModelManager.hasItemModel(item)) {
            VanillaModelManager.renderItem(itemStack);
            ci.cancel();
        }
    }
}
