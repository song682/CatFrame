package decok.dfcdvadstf.catframe.model.state.item;

import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.block.SingleBlockModel;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import javax.vecmath.Matrix4d;
import java.util.List;
import java.util.Map;

/**
 * 方块状态物品模型包装器。
 * <p>
 * 将 {@link BlockStateModel} 的烘焙 quads 复用为物品渲染，
 * 类似于 1.21.5 的 CuboidItemModelWrapper。
 * <p>
 * 物品渲染时，通过 {@link UniformRenderPipeline#renderItemQuads} 完成
 * 扩展链处理和 Tessellator 提交。
 * <p>
 * 支持 JSON model 的 {@code display} transforms，根据 {@link RenderPhase}
 * 自动应用对应的变换（gui / thirdperson_righthand）。
 */
public class BlockStateItemState implements IItemStateProvider {

    private final BlockStateModel blockModel;

    public BlockStateItemState(BlockStateModel blockModel) {
        this.blockModel = blockModel;
    }

    public BlockStateItemState(BlockStateModel blockModel, Map<String, ModelJson.DisplayTransform> display) {
        this.blockModel = blockModel;
    }

    public BlockStateItemState(BlockStateModelPart part) {
        this.blockModel = new SingleBlockModel(part);
    }

    public BlockStateItemState(BlockStateModelPart part, Map<String, ModelJson.DisplayTransform> display) {
        // display 已由 BlockStateModelPart 持有，合并到 part 一级
        this.blockModel = new SingleBlockModel(
                display != null ? part.withDisplay(display) : part);
    }

    /**
     * 从预烘焙的 quad 列表直接构造（无需经过 BlockStateModel 调度）。
     */
    public static BlockStateItemState fromQuads(List<BakedQuad> quads) {
        return new BlockStateItemState(BlockStateModelPart.fromQuads(quads));
    }

    public static BlockStateItemState fromQuads(List<BakedQuad> quads, Map<String, ModelJson.DisplayTransform> display) {
        BlockStateModelPart part = BlockStateModelPart.fromQuads(quads, display);
        return new BlockStateItemState(part);
    }

    /**
     * 解析模型路径的 display transforms。
     */
    @javax.annotation.Nullable
    private static Map<String, ModelJson.DisplayTransform> resolveDisplay(String modelPath) {
        if (modelPath == null) return null;
        ModelJson resolved = ModelResolver.resolve(modelPath);
        return resolved != null ? resolved.display : null;
    }

    @Override
    public void render(ItemStack stack, RenderPhase phase) {
        render(stack, phase, null);
    }

    @Override
    public void render(ItemStack stack, RenderPhase phase, @javax.annotation.Nullable Matrix4d preTransform) {
        if (stack != null && stack.getItem() instanceof ItemBlock) {
            Block block = ((ItemBlock) stack.getItem()).field_150939_a;

            // 检测 IBlockStateProvider.inventoryFlatModel() → 模组方块扁平 2D
            if (block instanceof IBlockStateProvider) {
                String flatModel = ((IBlockStateProvider) block).inventoryFlatModel();
                if (flatModel != null) {
                    String cacheKey = BakedModelCache.buildKey(flatModel, 0, 0);
                    BlockStateModelPart flatPart = BakedModelCache.INSTANCE.get(cacheKey);
                    if (flatPart != null && !flatPart.isEmpty()) {
                        UniformRenderPipeline.renderItemQuads(flatPart, stack, phase,
                                null, 0, 0, 0, null, preTransform);
                        return;
                    }
                }
            }
        }

        // 默认：使用 3D 方块模型渲染
        int damage = stack.getItemDamage();
        BlockStateModelPart part = blockModel.collectParts(null, 0, 0, 0, damage);
        if (part == null || part.isEmpty()) return;

        UniformRenderPipeline.renderItemQuads(part, stack, phase,
                null, 0, 0, 0, null, preTransform);
    }
}
