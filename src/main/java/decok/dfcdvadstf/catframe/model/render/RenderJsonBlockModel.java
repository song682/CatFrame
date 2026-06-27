package decok.dfcdvadstf.catframe.model.render;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

/**
 * CatFrame 方块的 ISBRH 处理器。
 * <p>
 * 每个通过 {@link decok.dfcdvadstf.catframe.model.JsonBlock JsonBlock} 注册的方块
 * 拥有一个独立的 renderID，对应一个 {@code RenderJsonBlockModel} 实例。
 * <p>
 * 本处理器本身<b>不存储</b>任何烘焙数据 ——— 所有模型数据统一存放在
 * {@link VanillaModelManager} 的 {@code registeredBlockModels} 中，
 * 渲染时委托给 {@link VanillaModelManager.PublicRenderAPI PublicRenderAPI}，
 * 经由 {@link UniformRenderPipeline} 完成实际绘制。
 * <p>
 * 这样保证了模型加载（VMM）与渲染（UniformRenderPipeline）的解耦，
 * 扩展可自由插入渲染流程。
 */
public class RenderJsonBlockModel implements ISimpleBlockRenderingHandler {
    private final int ID;
    private final boolean renderItem;

    public RenderJsonBlockModel(int ID, boolean renderItem) {
        this.ID = ID;
        this.renderItem = renderItem;
    }

    /**
     * 世界中方块渲染 — 委托给 {@link VanillaModelManager.PublicRenderAPI#renderBlock}。
     * <p>
     * PublicRenderAPI 内部从 VMM 的 {@code registeredBlockModels} 取出
     * {@link BlockStateModel}，再通过 {@link UniformRenderPipeline#renderBlockQuads}
     * 完成带扩展链的渲染。
     */
    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z,
                                     Block block, int modelId, RenderBlocks renderer) {
        return VanillaModelManager.PublicRenderAPI.renderBlock(world, x, y, z, block, renderer);
    }

    /**
     * 物品栏 / GUI 中的方块渲染 — 从 VMM 取模型数据，走 BLOCK_GUI 阶段的 UniformRenderPipeline。
     */
    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        BlockStateModel model = VanillaModelManager.ModelRegistration.getBlockModel(block);
        if (model == null) return;

        BlockStateModelPart part = model.collectParts(null, 0, 0, 0, metadata);
        if (part == null || part.isEmpty()) return;

        // [S1] 将 metadata 传入渲染管线，用于 Block.getRenderColor(metadata) 染色
        UniformRenderPipeline.renderBlockQuadsGUI(part, block, null, metadata);
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return renderItem;
    }

    @Override
    public int getRenderId() {
        return this.ID;
    }
}
