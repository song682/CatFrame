package decok.dfcdvadstf.catframe.ui.components.contextualbar;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
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

    /**
     * 绘制坐骑跳跃条 —— 可见性检查已由 {@link #extractRenderState}
     * 在调用前处理。<br>
     * Draws the mount jump bar — the visibility check is already handled by
     * {@link #extractRenderState}.
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.ridingEntity == null) return;

        updatePosition();

        mc.getTextureManager().bindTexture(ICONS);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        gui.drawTexturedModalRect(x, y, 0, 84, 182, 5);
    }
}
