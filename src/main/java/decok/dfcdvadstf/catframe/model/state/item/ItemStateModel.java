package decok.dfcdvadstf.catframe.model.state.item;

import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.property.ItemProperties;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * ItemState 决策树驱动的物品模型。
 * <p>
 * 这是 {@code LazyItemModel}（单模型路径懒烘焙）与
 * {@code ItemStateItemModel}（决策树分派）合并后的统一实现：
 * <ul>
 *   <li>无抉择时，内部退化为单个 {@link ItemStateNode.ModelLeaf}，等价于原 LazyItemModel</li>
 *   <li>有抉择时，持有完整 {@link ItemStateNode} 决策树，运行时根据 ItemStack 数据求值</li>
 * </ul>
 *
 * <p>渲染流程：
 * <ol>
 *   <li>调用 {@link ItemProperties#buildProperties(ItemStack, RenderPhase)} 构建运行时属性集</li>
 *   <li>调用 {@link ItemStateNode#evaluate(Map)} 递归求值，得到最终模型路径</li>
 *   <li>从 {@link BakedModelCache} 懒获取选中模型</li>
 *   <li>通过 {@link UniformRenderPipeline#renderItemQuads} 渲染</li>
 * </ol>
 */
public class ItemStateModel implements IItemStateProvider {

    private final ItemStateNode rootNode;

    /**
     * 从单模型路径构造（无抉择，退化为 ModelLeaf）。
     *
     * @param modelPath 模型路径
     */
    public ItemStateModel(String modelPath) {
        this(new ItemStateNode.ModelLeaf(modelPath));
    }

    /**
     * 从决策树根节点构造。
     *
     * @param rootNode 决策树根节点（不可为 null）
     */
    public ItemStateModel(ItemStateNode rootNode) {
        if (rootNode == null) {
            throw new IllegalArgumentException("ItemStateModel root node must not be null");
        }
        this.rootNode = rootNode;
    }

    @Override
    public void render(ItemStack stack, RenderPhase phase) {
        // 1. 构建运行时属性集
        Map<String, Comparable<?>> props = ItemProperties.buildProperties(stack, phase);

        // 2. 递归求值决策树，得到模型路径
        String resolvedPath = rootNode.evaluate(props);
        if (resolvedPath == null) return;

        // 3. 从 BakedModelCache 懒获取模型
        String cacheKey = BakedModelCache.buildKey(resolvedPath, 0, 0);
        BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
        if (part == null || part.isEmpty()) return;

        // 4. 渲染（display 已由 BlockStateModelPart 持有，无需单独传入）
        UniformRenderPipeline.renderItemQuads(part, stack, phase);
    }

    /**
     * 返回决策树根节点（用于调试和纹理收集）。
     */
    public ItemStateNode getRootNode() {
        return rootNode;
    }
}
