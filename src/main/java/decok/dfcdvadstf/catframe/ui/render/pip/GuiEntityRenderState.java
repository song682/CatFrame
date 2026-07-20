package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.entity.EntityLivingBase;

import javax.annotation.Nullable;

/**
 * GUI 实体的 PiP 渲染状态（对标接入内容 C）。
 * <p>
 * 由 {@code GuiGraphicsExtractor.entity(...)} 采集，帧末交给 {@link EntityPipRenderer}
 * 走移植自 {@code GuiInventory.drawEntityOnScreen} 的实体渲染路径。
 */
public class GuiEntityRenderState extends AbstractPipRenderState {

    private final EntityLivingBase entity;
    private final int scale;
    private final float lookX;
    private final float lookY;

    public GuiEntityRenderState(EntityLivingBase entity, int scale,
                                float lookX, float lookY,
                                @Nullable float[] poseMatrix,
                                ScreenRectangle bounds,
                                @Nullable ScreenRectangle scissorArea) {
        super(poseMatrix, bounds, scissorArea);
        this.entity = entity;
        this.scale = scale;
        this.lookX = lookX;
        this.lookY = lookY;
    }

    public EntityLivingBase getEntity() {
        return entity;
    }

    public int getScale() {
        return scale;
    }

    public float getLookX() {
        return lookX;
    }

    public float getLookY() {
        return lookY;
    }
}
