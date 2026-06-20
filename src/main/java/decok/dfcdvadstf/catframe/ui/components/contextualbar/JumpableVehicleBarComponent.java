package decok.dfcdvadstf.catframe.ui.components.contextualbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * 跳跃条组件 —— 渲染坐骑的跳跃值。
 * </p>
 * <p>
 * Jump bar component — renders the mount's jump value.
 * </p>
 */
public class JumpableVehicleBarComponent extends ContextualBarComponent {

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

        gui.drawTexturedModalRect(x, y, 0, 84, 182, 5);
    }
}
