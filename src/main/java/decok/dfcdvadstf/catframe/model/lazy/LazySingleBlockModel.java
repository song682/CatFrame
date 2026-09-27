package decok.dfcdvadstf.catframe.model.lazy;

import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.ParticleIconResolver;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

/**
 * 懒烘焙单模型。持有单个 modelPath，渲染时从 {@link BakedModelCache} 懒获取。
 * <p>
 * 用于 model_mappings 中的 block（无 blockstate，单一 model path）。
 * 持有 modelPath 并在 collectParts 时懒解析 + 懒烘焙。
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

    /**
     * 粒子纹理解析：单模型缓存获取后返回显式 particle 槽
     * （缺失为 null，由 ParticleIconResolver 映射 missingno）。
     */
    @Override
    public IIcon particleIcon(int metadata) {
        if (modelPath == null) return null;
        String cacheKey = BakedModelCache.buildKey(modelPath, 0, 0);
        return ParticleIconResolver.fromPart(BakedModelCache.INSTANCE.get(cacheKey));
    }
}
