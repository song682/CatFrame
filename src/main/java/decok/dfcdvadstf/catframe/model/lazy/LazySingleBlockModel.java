package decok.dfcdvadstf.catframe.model.lazy;

import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.ParticleIconResolver;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

/**
 * Lazy-baked single model. Holds a single modelPath, lazily retrieves from {@link BakedModelCache} at render time.
 * <p>
 * Used for model_mappings blocks (no blockstate, single model path).
 * Holds modelPath and lazily parses + bakes during collectParts.
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
     * Particle icon resolution: fetch from single-model cache, return explicit particle slot
     * (missing is null, mapped to missingno by ParticleIconResolver).
     */
    @Override
    public IIcon particleIcon(int metadata) {
        if (modelPath == null) return null;
        String cacheKey = BakedModelCache.buildKey(modelPath, 0, 0);
        return ParticleIconResolver.fromPart(BakedModelCache.INSTANCE.get(cacheKey));
    }
}
