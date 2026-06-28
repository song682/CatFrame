package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * 懒烘焙物品模型。持有 modelPath，渲染时从 {@link BakedModelCache} 懒获取。
 * <p>
 * 替代旧系统中从 {@code bakedItemModels} 获取预烘焙 quads 的路径。
 * display transforms 在构造时从 ModelJson 预解析（仅读 JSON，不烘焙 quads）。
 */
public class LazyItemModel implements ItemModel {
    private final String modelPath;
    private final Map<String, ModelJson.DisplayTransform> display;

    public LazyItemModel(String modelPath, Map<String, ModelJson.DisplayTransform> display) {
        this.modelPath = modelPath;
        this.display = display;
    }

    @Override
    public void render(ItemStack stack, RenderPhase phase) {
        if (modelPath == null) return;
        String cacheKey = BakedModelCache.buildKey(modelPath, 0, 0);
        BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
        if (part == null || part.isEmpty()) return;
        UniformRenderPipeline.renderItemQuads(part, stack, phase, display);
    }

    public String getModelPath() {
        return modelPath;
    }
}
