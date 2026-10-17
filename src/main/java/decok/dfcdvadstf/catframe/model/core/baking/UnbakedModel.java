package decok.dfcdvadstf.catframe.model.core.baking;

import decok.dfcdvadstf.catframe.model.ItemModelGenerator;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.TextureSlots;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Unbaked model definition. Counterpart of {@code UnbakedModel} in 26.1.2.
 * <p>
 * Represents the state of a model after JSON parsing but before resolution into concrete {@link BakedQuad}s.
 * Contains metadata such as texture references, display transforms, and guiLight.
 * Baked into a {@link BlockStateModelPart} via the {@link #bake(TextureSlots, int, int)} method.
 *
 * <p>The existing {@link ModelJson} POJO is adapted to this interface via {@link ModelJsonUnbakedAdapter},
 * so baking logic does not need to depend directly on the JSON parsing layer.
 *
 * <h3>Differences from 26.1.2</h3>
 * <ul>
 *   <li>26.1.2 distinguishes UnbakedGeometry and UnbakedModel; here they are merged into a single interface for simplicity</li>
 *   <li>ModelBakery's complex caching and dependency resolution are not introduced, keeping it lightweight</li>
 * </ul>
 */
public interface UnbakedModel {

    /**
     * Texture reference map: slot name → texture path (or a {@code #xxx} reference, resolved by TextureSlots).
     * Corresponds to ModelJson.textures.
     */
    @Nullable
    Map<String, String> textureReferences();

    /**
     * Display transforms for different render contexts (gui / thirdperson_righthand / ...).
     * Corresponds to ModelJson.display.
     */
    @Nullable
    Map<String, ModelJson.DisplayTransform> display();

    /**
     * GUI lighting mode: {@code "front"} (item/flat lighting) or {@code "side"} (block/3D lighting).
     * Corresponds to ModelJson.guiLight.
     */
    @Nullable
    String guiLight();

    /**
     * Global ambient occlusion toggle.
     * Corresponds to ModelJson.ambientocclusion (element level); here it serves as the model-level default.
     */
    @Nullable
    Boolean ambientOcclusion();

    /**
     * List of elements in the model. May be null (e.g. builtin/generated is generated dynamically by ItemModelGenerator).
     * Corresponds to ModelJson.elements.
     */
    @Nullable
    List<ModelJson.Element> elements();

    /**
     * Whether this inherits from the builtin/generated model.
     * When true, baking calls {@link ItemModelGenerator#bakeSideFaces} to generate side quads.
     */
    boolean isBuiltinGenerated();

    /**
     * Texture size [width, height], default [16, 16].
     * Used for UV coordinate scaling. Corresponds to ModelJson.texture_size.
     */
    @Nullable
    int[] textureSize();

    /**
     * Baking entry point: converts an unbaked model into a render-ready {@link BlockStateModelPart}.
     *
     * @param textures  resolved texture slots (IIcon already available)
     * @param rotationX X-axis rotation angle (any angle is supported, e.g. 22.5°)
     * @param rotationY Y-axis rotation angle (any angle is supported, e.g. 22.5°)
     * @param rotationZ Z-axis rotation angle (any angle is supported, e.g. 22.5°)
     * @return the baked render part
     */
    BlockStateModelPart bake(TextureSlots textures, float rotationX, float rotationY, float rotationZ);
}
