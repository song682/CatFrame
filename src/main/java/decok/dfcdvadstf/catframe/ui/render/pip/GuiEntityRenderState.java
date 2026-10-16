package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.entity.EntityLivingBase;

import javax.annotation.Nullable;

/**
 * PiP render state for a GUI entity (mirrors integration content C).
 * <p>
 * Collected by {@code GuiGraphicsExtractor.entity(...)} and handed to
 * {@link EntityPipRenderer} at frame end, which runs the entity render path
 * ported from {@code GuiInventory.drawEntityOnScreen}.
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
