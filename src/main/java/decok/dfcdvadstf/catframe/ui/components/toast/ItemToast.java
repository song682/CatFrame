package decok.dfcdvadstf.catframe.ui.components.toast;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * 物品 Toast<br>
 * 展示带有物品图标的 Toast 通知。
 * </p>
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
        // TODO: Use RenderHelper to render the item
        // mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, 4, 4);
        GL11.glPopMatrix();

        // Render title
        fontRenderer.drawString(title, 42, 10, 0xFFFFFF);

        // Render description
        if (description != null) {
            fontRenderer.drawString(description, 42, 24, 0xAAAAAA);
        }
    }
}
