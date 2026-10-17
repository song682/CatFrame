package decok.dfcdvadstf.catframe.model.core.baking;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Pure baking function. Stateless, writes no static fields, inherently thread-safe.
 * <p>
 * Core baking logic extracted from {@link VanillaModelManager.VMMModelBaking#bakeModel} and {@link ModelBaker}:
 * <pre>
 *   modelPath → ModelResolver.resolve() → ModelBaker.bake() → BlockStateModelPart
 * </pre>
 * <p>
 * This class maintains no cache; all caching responsibility is handled by {@link BakedModelCache}.
 * This allows the class to be called concurrently and safely by multiple threads (e.g. the Akka BakeWorker or lazy baking on the render thread).
 */
public class BakingCore {

    private BakingCore() {}

    /**
     * Convenience overload: no Z-axis rotation.
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, float rotX, float rotY) {
        return bake(modelPath, rotX, rotY, 0);
    }

    /**
     * Convenience overload: no Z-axis rotation, explicit iconMap.
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, float rotX, float rotY,
                                            @Nullable Map<String, IIcon> iconMap) {
        return bake(modelPath, rotX, rotY, 0, iconMap);
    }

    /**
     * Bakes a single model into a {@link BlockStateModelPart} (uses the current stitch cycle's iconMap).
     * <p>
     * Pure-function semantics: identical (modelPath, rotX, rotY, rotZ, iconMap) inputs always produce semantically equivalent results.
     * No static cache or global state is written.
     *
     * @param modelPath model path (e.g. "block/stone", "builtin/generated")
     * @param rotX      X-axis rotation angle (any angle is supported)
     * @param rotY      Y-axis rotation angle (any angle is supported)
     * @param rotZ      Z-axis rotation angle (any angle is supported)
     * @return the baked model part, or null on failure
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, float rotX, float rotY, float rotZ) {
        return bake(modelPath, rotX, rotY, rotZ, VanillaTextureTracker.textureIcons);
    }

    /**
     * Bakes a single model into a {@link BlockStateModelPart} with an explicitly specified iconMap.
     * <p>
     * Follows the instantiation-closure pattern of the modern {@code MaterialBaker}: each bake uses
     * the caller-supplied iconMap, avoiding race-condition overwrites between stitch cycles.
     *
     * @param modelPath model path
     * @param rotX      X-axis rotation angle
     * @param rotY      Y-axis rotation angle
     * @param rotZ      Z-axis rotation angle
     * @param iconMap   IIcon map for the current stitch cycle
     * @return the baked model part, or null on failure
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, float rotX, float rotY, float rotZ,
                                            @Nullable Map<String, IIcon> iconMap) {
        if (modelPath == null) return null;

        // 1. Resolve the JSON (ModelResolver caches internally; thread safety is guaranteed by ConcurrentHashMap)
        ModelJson resolved = ModelResolver.resolve(modelPath);
        if (resolved == null) {
            CatFrame.logger.warn("[BakingCore] ModelResolver.resolve('{}') returned null", modelPath);
            return null;
        }

        // 2. Delegate the actual baking to ModelBaker (passing iconMap rather than reading the global static one)
        BlockStateModelPart part = ModelBaker.bake(resolved, rotX, rotY, rotZ, modelPath, iconMap);
        if (part == null) {
            CatFrame.logger.debug("[BakingCore] bake returned null for '{}' @ {}@{}@{}", modelPath, rotX, rotY, rotZ);
        }
        return part;
    }
}
