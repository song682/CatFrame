package decok.dfcdvadstf.catframe.mixin;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.CatFrameConfig;
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
     * 精确控制策略 — {@link ItemModel#handles(RenderPhase)} 是唯一的门控：
     * <ol>
     *   <li>注册了 ItemModel？否 → 走原版 2D 图标</li>
     *   <li>{@code handles(ITEM_GUI)} 返回 true？否 → 走原版 2D 图标</li>
     *   <li>是 → 取消原版，走自定义渲染</li>
     * </ol>
     * 不再使用 {@code hasItemModel} 布尔值判断。
     */
    @Inject(method = "renderItemIntoGUI", at = @At("HEAD"), cancellable = true)
    private void catframe$onRenderItemIntoGUI(FontRenderer fontRenderer,
                                              TextureManager textureManager,
                                              ItemStack itemStack, int x, int y,
                                              CallbackInfo ci) {
        if (itemStack == null) return;
        Item item = itemStack.getItem();
        if (item == null) return;

        // 没有注册 ItemModel → 完全走原版 GUI 2D 图标（不用 hasItemModel 布尔值）
        ItemModel itemModel = VanillaModelManager.ModelRegistration.getRegisteredItemModel(item);
        if (itemModel == null) return;

        // handles() 是精确控制面 — ItemModel 自己决定是否接管 GUI 阶段
        if (!itemModel.handles(RenderPhase.ITEM_GUI)) {
            if (CatFrameConfig.shouldLogDebug()) {
                CatFrame.logger.info("[MixinRenderItem] ItemModel.handles(ITEM_GUI)=false for {}, vanilla 2D icon",
                        Item.itemRegistry.getNameForObject(item));
            }
            return;
        }

        // 接管 → cancel 原版图标，走自定义 GUI 渲染
        VanillaModelManager.PublicRenderAPI.renderItem(itemStack);
        ci.cancel();
    }
}
