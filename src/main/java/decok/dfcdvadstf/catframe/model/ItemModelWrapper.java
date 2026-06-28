package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 物品模型包装器。将 {@link BlockStateModel} 的烘焙 quads 复用为物品渲染。
 * 类似于 1.21.5 的 CuboidItemModelWrapper。
 *
 * <p>物品渲染时，通过 {@link UniformRenderPipeline#renderItemQuads} 完成
 * 扩展链处理和 Tessellator 提交。
 *
 * <p>支持 JSON model 的 {@code display} transforms，根据 {@link RenderPhase}
 * 自动应用对应的变换（gui / thirdperson_righthand）。
 */
public class ItemModelWrapper implements ItemModel {
    private final BlockStateModel blockModel;
    private final Map<String, ModelJson.DisplayTransform> display;
    /** 可选的 handles 委托，由 IItemState.handles(phase) 语义传入 */
    private final Function<RenderPhase, Boolean> handlesDelegate;

    public ItemModelWrapper(BlockStateModel blockModel) {
        this(blockModel, null);
    }

    public ItemModelWrapper(BlockStateModel blockModel, Map<String, ModelJson.DisplayTransform> display) {
        this.blockModel = blockModel;
        this.display = display;
        this.handlesDelegate = null;
    }

    public ItemModelWrapper(BlockStateModelPart part) {
        this(part, null);
    }

    public ItemModelWrapper(BlockStateModelPart part, Map<String, ModelJson.DisplayTransform> display) {
        this(part, display, null);
    }

    public ItemModelWrapper(BlockStateModelPart part, Map<String, ModelJson.DisplayTransform> display,
                            Function<RenderPhase, Boolean> handlesDelegate) {
        this.blockModel = new SingleBlockModel(part);
        this.display = display;
        this.handlesDelegate = handlesDelegate;
    }

    /**
     * 从预烘焙的 quad 列表直接构造（无需经过 BlockStateModel 调度）。
     */
    public static ItemModelWrapper fromQuads(List<BakedQuad> quads) {
        return new ItemModelWrapper(BlockStateModelPart.fromQuads(quads));
    }

    public static ItemModelWrapper fromQuads(List<BakedQuad> quads, Map<String, ModelJson.DisplayTransform> display) {
        return new ItemModelWrapper(BlockStateModelPart.fromQuads(quads), display);
    }

    @Override
    public boolean handles(RenderPhase phase) {
        if (handlesDelegate != null) return handlesDelegate.apply(phase);
        return ItemModel.super.handles(phase);
    }

    @Override
    public void render(ItemStack stack, RenderPhase phase) {
        // 物品渲染不需要世界坐标，metadata 来自栈 damage
        int damage = stack.getItemDamage();
        BlockStateModelPart part = blockModel.collectParts(null, 0, 0, 0, damage);
        if (part == null || part.isEmpty()) return;

        UniformRenderPipeline.renderItemQuads(part, stack, phase, display);
    }
}
