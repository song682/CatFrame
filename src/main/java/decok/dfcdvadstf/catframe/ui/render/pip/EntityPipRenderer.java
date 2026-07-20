package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * GUI 实体 PiP 渲染器 — 移植自 {@code GuiInventory.drawEntityOnScreen}。
 * <p>
 * 恢复采集矩阵后，在独立 3D 视口内按 {@code lookX/lookY} 旋转并用
 * {@link RenderManager} 渲染实体，绘制完成后还原实体朝向字段。
 */
public class EntityPipRenderer implements PictureInPictureRenderer<GuiEntityRenderState> {

    @Override
    public Class<GuiEntityRenderState> getStateClass() {
        return GuiEntityRenderState.class;
    }

    @Override
    public void prepare(GuiEntityRenderState state) {
        EntityLivingBase ent = state.getEntity();
        if (ent == null) return;

        ScreenRectangle b = state.bounds();
        float posX = b != null ? b.left() + b.width / 2.0f : 0.0f;
        float posY = b != null ? b.top() + b.height / 2.0f : 0.0f;
        int scale = state.getScale();
        float mouseX = state.getLookX();
        float mouseY = state.getLookY();

        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glPushMatrix();
        try {
            // 恢复采集点变换后再叠加 GUI 实体定位（帧末 GL 上下文已切换）
            PipGl.restore(state.poseMatrix());

            GL11.glTranslatef(posX, posY, 50.0F);
            GL11.glScalef((float) (-scale), (float) scale, (float) scale);
            GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);

            float f2 = ent.renderYawOffset;
            float f3 = ent.rotationYaw;
            float f4 = ent.rotationPitch;
            float f5 = ent.prevRotationYawHead;
            float f6 = ent.rotationYawHead;

            GL11.glRotatef(135.0F, 0.0F, 1.0F, 0.0F);
            RenderHelper.enableStandardItemLighting();
            GL11.glRotatef(-135.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-((float) Math.atan(mouseY / 40.0F)) * 20.0F, 1.0F, 0.0F, 0.0F);

            ent.renderYawOffset = (float) Math.atan(mouseX / 40.0F) * 20.0F;
            ent.rotationYaw = (float) Math.atan(mouseX / 40.0F) * 40.0F;
            ent.rotationPitch = -((float) Math.atan(mouseY / 40.0F)) * 20.0F;
            ent.rotationYawHead = ent.rotationYaw;
            ent.prevRotationYawHead = ent.rotationYaw;

            GL11.glTranslatef(0.0F, ent.yOffset, 0.0F);
            RenderManager.instance.playerViewY = 180.0F;
            RenderManager.instance.renderEntityWithPosYaw(ent, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F);

            ent.renderYawOffset = f2;
            ent.rotationYaw = f3;
            ent.rotationPitch = f4;
            ent.prevRotationYawHead = f5;
            ent.rotationYawHead = f6;
        } finally {
            GL11.glPopMatrix();
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        }
    }
}
