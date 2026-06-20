package decok.dfcdvadstf.catframe.ui.components.contextualbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * 经验条组件 —— 渲染玩家的经验值和经验等级。
 * </p>
 * <p>
 * Experience bar component — renders the player's experience progress and level.
 * </p>
 */
public class ExperienceBarComponent extends ContextualBarComponent {

    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private final Gui gui = new Gui();

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        updatePosition();

        int expLevel = mc.thePlayer.experienceLevel;
        float expProgress = mc.thePlayer.experience;
        int expTotal = mc.thePlayer.xpBarCap();

        if (expLevel > 0 || expProgress > 0) {
            mc.getTextureManager().bindTexture(ICONS);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            int barWidth = (int) (expProgress * 183.0F);
            int barX = x;
            int barY = y;

            gui.drawTexturedModalRect(barX, barY, 0, 64, 182, 5);

            if (barWidth > 0) {
                gui.drawTexturedModalRect(barX, barY, 0, 69, barWidth, 5);
            }
        }

        // Use scaled resolution for level text positioning
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        renderLevelText(expLevel, res.getScaledWidth(), res.getScaledHeight());
    }
}
