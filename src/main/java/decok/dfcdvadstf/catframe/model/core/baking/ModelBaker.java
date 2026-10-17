package decok.dfcdvadstf.catframe.model.core.baking;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.core.TextureSlots;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Baking facade. Counterpart of {@code ModelBaker} in 26.1.2.
 * <p>
 * Encapsulates the full pipeline from model path to baked result:
 * {@code modelPath → ModelResolver.resolve() → UnbakedModel → TextureSlots → bake()}
 * <p>
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Provides baking entry points at three granularities (path / resolved JSON / UnbakedModel)</li>
 *   <li>Coordinates between {@link TextureSlots#fromModel} and {@link ModelJsonUnbakedAdapter#bake}</li>
 * </ul>
 *
 * <p>Caching responsibility is already handled by {@link BakedModelCache} (StampedLock + LRU lazy baking).
 * This class no longer maintains an internal cache; it serves purely as the baking pipeline facade.
 */
public class ModelBaker {

    private ModelBaker() {}

    // ==================== Baking entry points (with convenience overloads) ====================

    /**
     * Convenience overload: bake from a model path with no rotation (uses the current stitch cycle's iconMap).
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath) {
        return bake(modelPath, 0, 0, 0);
    }

    /**
     * Convenience overload: bake from a model path with Y-axis rotation only (uses the current stitch cycle's iconMap).
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, float rotationY) {
        return bake(modelPath, 0, rotationY, 0);
    }

    /**
     * Bakes a model path into a {@link BlockStateModelPart} (uses the current stitch cycle's iconMap).
     * <p>
     * Full pipeline: {@code modelPath → ModelResolver.resolve() → ModelJsonUnbakedAdapter → TextureSlots → bake()}
     * <p>
     * Note: this method does not maintain a cache. Caching is managed centrally by {@link BakedModelCache}.
     *
     * @param modelPath model path (e.g. {@code "block/stone"}, {@code "builtin/generated"})
     * @param rotationX X-axis rotation angle (any angle is supported)
     * @param rotationY Y-axis rotation angle (any angle is supported)
     * @return the baked render part, or null on failure
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, float rotationX, float rotationY) {
        return bake(modelPath, rotationX, rotationY, 0);
    }

    /**
     * Bakes a model path into a {@link BlockStateModelPart} (uses the current stitch cycle's iconMap).
     * <p>
     * Full pipeline: {@code modelPath → ModelResolver.resolve() → ModelJsonUnbakedAdapter → TextureSlots → bake()}
     * <p>
     * Note: this method does not maintain a cache. Caching is managed centrally by {@link BakedModelCache}.
     *
     * @param modelPath model path (e.g. {@code "block/stone"}, {@code "builtin/generated"})
     * @param rotationX X-axis rotation angle (any angle is supported)
     * @param rotationY Y-axis rotation angle (any angle is supported)
     * @param rotationZ Z-axis rotation angle (any angle is supported)
     * @return the baked render part, or null on failure
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, float rotationX, float rotationY, float rotationZ) {
        return bake(modelPath, rotationX, rotationY, rotationZ, VanillaTextureTracker.textureIcons);
    }

    /**
     * Bakes a model path into a {@link BlockStateModelPart} with an explicitly specified iconMap.
     * <p>
     * Follows the instantiation-closure pattern of the modern {@code MaterialBaker}: each bake uses
     * the caller-supplied iconMap (instead of a global static field), avoiding race-condition
     * overwrites between stitch cycles.
     *
     * @param modelPath model path
     * @param rotationX X-axis rotation angle
     * @param rotationY Y-axis rotation angle
     * @param rotationZ Z-axis rotation angle
     * @param iconMap   IIcon map for the current stitch cycle
     * @return the baked render part, or null on failure
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, float rotationX, float rotationY, float rotationZ,
                                            @Nullable Map<String, IIcon> iconMap) {
        if (modelPath == null) return null;

        // 1. Resolve the JSON
        ModelJson resolved = ModelResolver.resolve(modelPath);
        if (resolved == null) {
            CatFrame.logger.warn("[ModelBaker] ModelResolver.resolve('{}') returned null", modelPath);
            return null;
        }

        // 2. Delegate to the resolved overload
        return bake(resolved, rotationX, rotationY, rotationZ, modelPath, iconMap);
    }

    /**
     * Convenience overload: bake from a resolved {@link ModelJson} with no rotation.
     */
    @Nullable
    public static BlockStateModelPart bake(ModelJson resolved, float rotationX, float rotationY) {
        return bake(resolved, rotationX, rotationY, 0);
    }

    /**
     * Convenience overload: bake from a resolved {@link ModelJson} with no Z-axis rotation.
     */
    @Nullable
    public static BlockStateModelPart bake(ModelJson resolved, float rotationX, float rotationY, String modelPath) {
        return bake(resolved, rotationX, rotationY, 0, modelPath);
    }

    /**
     * Bakes directly from a resolved {@link ModelJson} (bypassing JSON parsing and caching).
     *
     * @param resolved  the resolved model
     * @param rotationX X-axis rotation angle
     * @param rotationY Y-axis rotation angle
     * @param rotationZ Z-axis rotation angle
     * @return the baked render part
     */
    @Nullable
    public static BlockStateModelPart bake(ModelJson resolved, float rotationX, float rotationY, float rotationZ) {
        return bake(resolved, rotationX, rotationY, rotationZ, "adapter", VanillaTextureTracker.textureIcons);
    }

    /**
     * Bakes from a resolved {@link ModelJson}, tagged with the model path (uses the current stitch cycle's iconMap).
     *
     * @param resolved  the resolved model
     * @param rotationX X-axis rotation angle
     * @param rotationY Y-axis rotation angle
     * @param rotationZ Z-axis rotation angle
     * @param modelPath model path (used for logging and identification)
     * @return the baked render part
     */
    @Nullable
    public static BlockStateModelPart bake(ModelJson resolved, float rotationX, float rotationY, float rotationZ, String modelPath) {
        return bake(resolved, rotationX, rotationY, rotationZ, modelPath, VanillaTextureTracker.textureIcons);
    }

    /**
     * Bakes from a resolved {@link ModelJson} with an explicitly specified iconMap.
     * <p>
     * Follows the instantiation-closure pattern of the modern {@code MaterialBaker}.
     *
     * @param resolved  the resolved model
     * @param rotationX X-axis rotation angle
     * @param rotationY Y-axis rotation angle
     * @param rotationZ Z-axis rotation angle
     * @param modelPath model path (used for logging and identification)
     * @param iconMap   IIcon map for the current stitch cycle
     * @return the baked render part
     */
    @Nullable
    public static BlockStateModelPart bake(ModelJson resolved, float rotationX, float rotationY, float rotationZ,
                                            String modelPath, @Nullable Map<String, IIcon> iconMap) {
        if (resolved == null) return null;
        if (resolved.elements == null || resolved.elements.isEmpty()) {
            if (!resolved.builtinGenerated) {
                CatFrame.logger.warn("[ModelBaker] model '{}' has no elements and is not builtinGenerated", modelPath);
                return null;
            }
        }

        // 1. Build the UnbakedModel adapter
        UnbakedModel unbaked = new ModelJsonUnbakedAdapter(resolved, modelPath);

        // 2. Build TextureSlots (using the caller-supplied iconMap rather than the global static one)
        TextureSlots textures = TextureSlots.fromModel(resolved, iconMap, null);

        // 3. Bake
        return unbaked.bake(textures, rotationX, rotationY, rotationZ);
    }

    /**
     * Bakes from an {@link UnbakedModel} (lowest-level entry point, suited to tests or programmatic models).
     *
     * @param model     the UnbakedModel instance
     * @param textures  resolved texture slots
     * @param rotationX X-axis rotation angle
     * @param rotationY Y-axis rotation angle
     * @param rotationZ Z-axis rotation angle
     * @return the baked render part
     */
    @Nullable
    public static BlockStateModelPart bake(UnbakedModel model, TextureSlots textures,
                                            float rotationX, float rotationY, float rotationZ) {
        if (model == null) return null;
        return model.bake(textures, rotationX, rotationY, rotationZ);
    }

}
