package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.block.Block;

import javax.annotation.Nullable;

/**
 * 3D 方块模型的 PiP 渲染状态（对标接入内容 A）。
 * <p>
 * 由 {@code GuiGraphicsExtractor.blockModel(...)} 采集，在帧末交给
 * {@link BlockModelPipRenderer} 走模型库 GUI 方块路径绘制。
 */
public class GuiBlockModelRenderState extends AbstractPipRenderState {

    private final Block block;
    private final int metadata;
    private final float scale;

    public GuiBlockModelRenderState(Block block, int metadata, float scale,
                                    @Nullable float[] poseMatrix,
                                    ScreenRectangle bounds,
                                    @Nullable ScreenRectangle scissorArea) {
        super(poseMatrix, bounds, scissorArea);
        this.block = block;
        this.metadata = metadata;
        this.scale = scale;
    }

    public Block getBlock() {
        return block;
    }

    public int getMetadata() {
        return metadata;
    }

    public float getScale() {
        return scale;
    }
}
