package decok.dfcdvadstf.catframe.ui.components.toast;

import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.extension.special.SpecialRenderRegistry;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * Item Toast — displays Toast notifications with item icons.
 * </p>
 */
public class ItemToast extends BaseToast {

    private final ItemStack itemStack;
    private final String title;
    private final String description;
    private final long displayTime;

    public ItemToast(ItemStack itemStack, String title, String description) {
        this(itemStack, title, description, DEFAULT_DISPLAY_TIME);
    }

    public ItemToast(ItemStack itemStack, String title, String description, long displayTime) {
        this.itemStack = itemStack;
        this.title = title;
        this.description = description;
        this.displayTime = displayTime;
        this.width = 180;
        this.height = 48;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        wantedVisibility = fullyVisibleForMs < displayTime ?
            Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    protected void renderContent(FontRenderer fontRenderer, long fullyVisibleForMs) {
        // Render item icon (32x32)
        GL11.glPushMatrix();
        GL11.glScalef(2.0F, 2.0F, 1.0F);
        renderItemIcon(4, 4);
        GL11.glPopMatrix();

        // Render title
        fontRenderer.drawString(title, 42, 10, 0xFFFFFF);

        // Render description
        if (description != null) {
            fontRenderer.drawString(description, 42, 24, 0xAAAAAA);
        }
    }

    /**
     * Draws the toast icon at the given slot origin.
     * <p>
     * Items handled by the CatFrame model pipeline go through
     * {@link GuiGraphicsExtractor#item(ItemStack, int, int)}, so an {@code oversized_in_gui} item
     * overflows the icon slot exactly like it does in a container slot. Everything else falls back
     * to the vanilla icon renderer — that pipeline is what draws non-CatFrame items everywhere else
     * in the GUI, and the toast must show them too.
     * </p>
     */
    private void renderItemIcon(int x, int y) {
        if (itemStack == null || itemStack.getItem() == null) {
            return;
        }
        if (ModelRegistry.hasItemModel(itemStack.getItem())
                || SpecialRenderRegistry.getProvider(itemStack.getItem()) != null) {
            GuiGraphicsExtractor.getInstance().item(itemStack, x, y);
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        RenderHelper.enableGUIStandardItemLighting();
        RenderItem.getInstance().renderItemAndEffectIntoGUI(
                mc.fontRenderer, mc.getTextureManager(), itemStack, x, y);
        RenderHelper.disableStandardItemLighting();
    }
}
