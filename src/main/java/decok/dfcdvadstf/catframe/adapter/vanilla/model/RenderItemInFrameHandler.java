package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.RenderDispatcher;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderItemInFrameEvent;

public class RenderItemInFrameHandler {

    /**
     * Item-frame render event — when an item is rendered inside an item frame,
     * if the item has a registered CatFrame model the vanilla render is canceled and
     * {@link RenderDispatcher#renderItemInFrame} takes over the CatFrame pipeline,
     * so that the {@code display.fixed} transform takes effect.
     * <p>
     * Map items (filled_map) are special-cased by vanilla and do not go through CatFrame.
     */
    @SubscribeEvent
    public void onRenderItemInFrame(RenderItemInFrameEvent event) {
        if (event.entityItemFrame == null) return;
        ItemStack stack = event.entityItemFrame.getDisplayedItem();
        if (stack == null || stack.getItem() == null) return;

        // Maps are handled by the vanilla MapRenderer, not intercepted
        if (stack.getItem() == Items.filled_map) return;

        // Only intercept items with a registered CatFrame model
        if (!ModelRegistry.hasItemModel(stack.getItem())) return;

        event.setCanceled(true);
        RenderDispatcher.renderItemInFrame(stack);
    }
}
