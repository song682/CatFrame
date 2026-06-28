package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.property.ItemProperties;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * ItemState 决策树驱动的 ItemModel。
 * <p>
 * 持有 {@link ItemStateNode} 决策树根节点，渲染时：
 * <ol>
 *   <li>调用 {@link ItemProperties#buildProperties(ItemStack, RenderPhase)} 构建运行时属性集</li>
 *   <li>调用 {@link ItemStateNode#evaluate(Map)} 递归求值决策树，得到最终模型路径</li>
 *   <li>从 {@link BakedModelCache} 懒获取选中模型</li>
 *   <li>通过 {@link UniformRenderPipeline#renderItemQuads} 渲染</li>
 * </ol>
 *
 * <p>这是 ItemState 系统的核心渲染类，替代了旧的 {@link LazyItemModel}（单模型路径）
 * 和 {@link ModernItem.DualRenderItemModel}（硬编码 phase 分派）。
 */
public class ItemStateItemModel implements ItemModel {

    private final ItemStateNode rootNode;

    /**
     * @param rootNode 决策树根节点（不可为 null）
     */
    public ItemStateItemModel(ItemStateNode rootNode) {
        if (rootNode == null) {
            throw new IllegalArgumentException("ItemStateItemModel root node must not be null");
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

        // 4. 解析 display transforms
        Map<String, ModelJson.DisplayTransform> display = resolveDisplay(resolvedPath);

        // 5. 渲染
        UniformRenderPipeline.renderItemQuads(part, stack, phase, display);
    }

    @Override
    public boolean handles(RenderPhase phase) {
        // ItemState 决策树处理所有渲染阶段
        return true;
    }

    /**
     * 返回决策树根节点（用于调试和纹理收集）。
     */
    public ItemStateNode getRootNode() {
        return rootNode;
    }

    /**
     * 从 ModelJson 解析 display transforms。
     */
    private static Map<String, ModelJson.DisplayTransform> resolveDisplay(String modelPath) {
        if (modelPath == null) return null;
        ModelJson resolved = ModelResolver.resolve(modelPath);
        return resolved != null ? resolved.display : null;
    }
}
