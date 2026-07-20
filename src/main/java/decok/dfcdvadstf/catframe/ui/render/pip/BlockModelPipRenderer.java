package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.opengl.GL11;

/**
 * 3D 方块模型 PiP 渲染器 — 走与 {@code RenderJsonBlockModel.renderInventoryBlock} 一致的
 * 模型库 GUI 方块路径（{@link ModelRegistry#getBlockModel(Block)} +
 * {@link UniformRenderPipeline#renderBlockQuadsGUI}）。
 */
public class BlockModelPipRenderer implements PictureInPictureRenderer<GuiBlockModelRenderState> {

    private static final int GL_SAVE_MASK =
            GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT;

    @Override
    public Class<GuiBlockModelRenderState> getStateClass() {
        return GuiBlockModelRenderState.class;
    }

    @Override
    public void prepare(GuiBlockModelRenderState state) {
        Block block = state.getBlock();
        BlockStateModel model = ModelRegistry.getBlockModel(block);
        if (model == null) return;

        BlockStateModelPart part = model.collectParts(null, 0, 0, 0, state.getMetadata());
        if (part == null || part.isEmpty()) return;

        GL11.glPushAttrib(GL_SAVE_MASK);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            // 恢复采集点的 modelview 矩阵（帧末 GL 上下文已切换）
            PipGl.restore(state.poseMatrix());

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RenderHelper.enableGUIStandardItemLighting();

            // 平移到 bounds 中心并按 scale 缩放（独立 3D 视口定位）
            ScreenRectangle b = state.bounds();
            if (b != null) {
                GL11.glTranslatef(b.left() + b.width / 2.0f, b.top() + b.height / 2.0f, 0.0f);
            }
            float scale = state.getScale();
            GL11.glScalef(scale, scale, scale);

            UniformRenderPipeline.renderBlockQuadsGUI(part, block, state.getMetadata());
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
