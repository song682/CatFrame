package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.world.IBlockAccess;

/**
 * 懒烘焙单模型。持有单个 modelPath，渲染时从 {@link BakedModelCache} 懒获取。
 * <p>
 * 用于 model_mappings 中的 block（无 blockstate，单一 model path）。
 * 与 {@link SingleBlockModel} 的区别：SingleBlockModel 持有预烘焙的 BlockStateModelPart，
 * 本类持有 modelPath 并在 collectParts 时懒解析 + 懒烘焙。
 */
public class LazySingleBlockModel implements BlockStateModel {
    private final String modelPath;

    public LazySingleBlockModel(String modelPath) {
        this.modelPath = modelPath;
    }

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        if (modelPath == null) return BlockStateModelPart.empty();
        String cacheKey = BakedModelCache.buildKey(modelPath, 0, 0);
        BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
        return part != null ? part : BlockStateModelPart.empty();
    }

    public String getModelPath() {
        return modelPath;
    }
}
