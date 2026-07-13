package decok.dfcdvadstf.catframe.compact.vanilla.model;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.RenderDispatcher;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderItemInFrameEvent;

public class RenderItemInFrameHandler {

    /**
     * 物品展示框渲染事件 — 当物品在展示框中渲染时，
     * 若该物品已注册 CatFrame 模型，则 cancel 原版渲染，
     * 通过 {@link RenderDispatcher#renderItemInFrame} 走 CatFrame 管线，
     * 使 {@code display.fixed} transform 生效。
     * <p>
     * 地图物品（filled_map）由原版特殊处理，不走 CatFrame。
     */
    @SubscribeEvent
    public void onRenderItemInFrame(RenderItemInFrameEvent event) {
        if (event.entityItemFrame == null) return;
        ItemStack stack = event.entityItemFrame.getDisplayedItem();
        if (stack == null || stack.getItem() == null) return;

        // 地图由原版 MapRenderer 处理，不接管
        if (stack.getItem() == Items.filled_map) return;

        // 仅接管已注册 CatFrame 模型的物品
        if (ModelRegistry.getRegisteredItemModel(stack.getItem()) == null) return;

        event.setCanceled(true);
        RenderDispatcher.renderItemInFrame(stack);
    }
}
