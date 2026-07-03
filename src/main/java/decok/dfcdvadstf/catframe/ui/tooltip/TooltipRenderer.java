package decok.dfcdvadstf.catframe.ui.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 工具提示渲染器——提供与 26.1.2 {@code GuiGraphics} 等效的 tooltip 渲染能力。
 * <p>
 * 在 1.7.10 的立即模式渲染中直接绘制 Tooltip 背景 + 文字 + 图像。
 */
@SideOnly(Side.CLIENT)
public class TooltipRenderer {

    /**
     * 渲染完整的 tooltip。
     */
    public static void renderTooltip(
            FontRenderer font,
            List<String> lines,
            int mouseX,
            int mouseY,
            ClientTooltipPositioner positioner
    ) {
        renderTooltip(font, lines, Optional.empty(), mouseX, mouseY, positioner, false, null);
    }

    /**
     * 渲染完整的 tooltip（含组件与样式）。
     */
    public static void renderTooltip(
            FontRenderer font,
            List<String> lines,
            Optional<TooltipComponent> component,
            int mouseX,
            int mouseY,
            ClientTooltipPositioner positioner,
            boolean replaceExisting,
            @Nullable ResourceLocation style
    ) {
        if (lines.isEmpty()) return;

        List<ClientTooltipComponent> components = new ArrayList<>();
        for (String line : lines) {
            components.add(ClientTooltipComponent.create(line));
        }

        Minecraft mc = Minecraft.getMinecraft();
        int screenWidth, screenHeight;
        if (mc.currentScreen != null) {
            screenWidth = mc.currentScreen.width;
            screenHeight = mc.currentScreen.height;
        } else {
            ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            screenWidth = res.getScaledWidth();
            screenHeight = res.getScaledHeight();
        }

        // 计算 tooltip 尺寸
        int textWidth = 0;
        int tempHeight = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent comp : components) {
            int lineWidth = comp.getWidth(font);
            if (lineWidth > textWidth) textWidth = lineWidth;
            tempHeight += comp.getHeight(font);
        }

        int w = textWidth;
        int h = tempHeight;

        // 定位
        int[] pos = positioner.positionTooltip(screenWidth, screenHeight, mouseX, mouseY, w, h);
        int x = pos[0];
        int yPos = pos[1];

        // 保存 OpenGL 状态并渲染
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 渲染背景
        TooltipRenderUtil.renderTooltipBackground(x, yPos, w, h);

        // 渲染文字行
        int localY = yPos;
        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent comp = components.get(i);
            comp.renderText(font, x, localY);
            localY += comp.getHeight(font) + (i == 0 ? 2 : 0);
        }

        // 渲染图像组件
        localY = yPos;
        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent comp = components.get(i);
            comp.renderImage(font, x, localY, w, h);
            localY += comp.getHeight(font) + (i == 0 ? 2 : 0);
        }

        GL11.glPopAttrib();
    }
}
