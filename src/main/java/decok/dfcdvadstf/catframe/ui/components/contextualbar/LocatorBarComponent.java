package decok.dfcdvadstf.catframe.ui.components.contextualbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * 定位器条组件 —— 渲染实体的位置指示。
 * </p>
 * <p>
 * Locator bar component — renders the entity location indicator.
 * </p>
 */
public class LocatorBarComponent extends ContextualBarComponent {

    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private final Gui gui = new Gui();

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.ridingEntity == null) return;

        updatePosition();

        mc.getTextureManager().bindTexture(ICONS);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        gui.drawTexturedModalRect(x, y, 0, 74, 182, 5);

        if (mc.thePlayer.ridingEntity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) mc.thePlayer.ridingEntity;
            float health = living.getHealth() / living.getMaxHealth();
            int filledWidth = (int) (health * 183.0F);
            gui.drawTexturedModalRect(x, y, 0, 79, filledWidth, 5);
        }
    }
}
