package decok.dfcdvadstf.catframe.model.core.baking;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.ItemModelGenerator;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.TextureSlots;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter that wraps {@link ModelJson} as the {@link UnbakedModel} interface.
 * <p>
 * The element baking logic currently in {@link VanillaModelManager.ModelBaking#bakeModel}
 * has been moved into this adapter's {@link #bake(TextureSlots, int, int)} method.
 * <p>
 * This achieves:
 * <ul>
 *   <li>Decoupling of baking logic from VMM — VMM only needs to delegate to {@link ModelBaker}</li>
 *   <li>An Unbaked abstraction layer — other UnbakedModel implementations (e.g. procedurally generated geometry) can be added later</li>
 *   <li>Testability — baking logic can be tested without initializing a Minecraft environment</li>
 * </ul>
 */
public class ModelJsonUnbakedAdapter implements UnbakedModel {

    private final ModelJson json;
    private final String modelPath;

    public ModelJsonUnbakedAdapter(ModelJson json, String modelPath) {
        this.json = json;
        this.modelPath = modelPath;
    }

    // ==================== UnbakedModel interface ====================

    @Nullable
    @Override
    public Map<String, String> textureReferences() {
        return json.textures;
    }

    @Nullable
    @Override
    public Map<String, ModelJson.DisplayTransform> display() {
        return json.display;
    }

    @Nullable
    @Override
    public String guiLight() {
        return json.guiLight;
    }

    @Nullable
    @Override
    public Boolean ambientOcclusion() {
        // Model-level "ambientocclusion" (26.1.2 CuboidModel.getAmbientOcclusion); null = not declared
        return json.ambientocclusion;
    }

    @Nullable
    @Override
    public List<ModelJson.Element> elements() {
        return json.elements;
    }

    @Override
    public boolean isBuiltinGenerated() {
        return json.builtinGenerated;
    }

    @Nullable
    @Override
    public int[] textureSize() {
        return json.texture_size;
    }

    // ==================== Baking entry point ====================

    /**
     * Bakes this model into a {@link BlockStateModelPart}.
     * <p>
     * The logic originates from lines 1204-1299 of the current {@code VanillaModelManager.ModelBaking.bakeModel()},
     * moved here to achieve separation of the Unbaked→Baked stages.
     *
     * @param textures  resolved texture slots (TextureSlots already handles IIcon lookup)
     * @param rotationX X-axis rotation angle (any angle is supported, e.g. 22.5°)
     * @param rotationY Y-axis rotation angle (any angle is supported, e.g. 22.5°)
     * @param rotationZ Z-axis rotation angle (any angle is supported, e.g. 22.5°)
     * @return the baked render part
     */
    @Override
    public BlockStateModelPart bake(TextureSlots textures, float rotationX, float rotationY, float rotationZ) {
        // 1. Fetch texture_size
        int[] texSize = json.texture_size;

        // 2. Build iconMap (backward compatible with BlockJsonModelBake.bakeElement's Map<String, IIcon> parameter)
        Map<String, IIcon> iconMap = textures.toIconMap();

        // 3. Check elements (aligned with 26.1.2: particleMaterial is independent of geometry baking —
        //    the particle slot must still be captured, otherwise an empty-geometry model loses its declared particle texture)
        if (json.elements == null || json.elements.isEmpty()) {
            CatFrame.logger.warn("[ModelJsonUnbakedAdapter] bake: model '{}' has no elements", modelPath);
            return BlockStateModelPart.fromQuads(null, null, textures.getIcon("particle"));
        }

        // 4. Iterate elements and bake them into BakedQuads
        List<BakedQuad> quads = new ArrayList<>();
        for (ModelJson.Element element : json.elements) {
            List<BakedQuad> elementQuads = JsonModelBake.bakeElement(element, iconMap, texSize);
            if (elementQuads != null) {
                quads.addAll(elementQuads);
            }
        }

        // 5. Generate side quads for builtin/generated models (pixel-level edge extrusion shading)
        if (json.builtinGenerated && !iconMap.isEmpty()) {
            int totalSideQuads = 0;
            for (Map.Entry<String, IIcon> texEntry : iconMap.entrySet()) {
                // Derive tintIndex from the texture key name (layer0→0, layer1→1, ... others→-1)
                int layerTint = -1;
                String texKey = texEntry.getKey();
                if (texKey.startsWith("layer")) {
                    try {
                        layerTint = Integer.parseInt(texKey.substring(5));
                    } catch (NumberFormatException ignored) {
                    }
                }
                List<BakedQuad> sideQuads = ItemModelGenerator.bakeSideFaces(texEntry.getValue(), layerTint);
                if (sideQuads != null) {
                    quads.addAll(sideQuads);
                    totalSideQuads += sideQuads.size();
                }
            }
            if (totalSideQuads > 0) {
                CatFrame.logger.debug("[ModelJsonUnbakedAdapter] bake: generated {} side quads for '{}'",
                        totalSideQuads, modelPath);
            }
        }

        // 6. Propagate the model-level guiLight to all quads
        if (json.guiLight != null) {
            for (BakedQuad q : quads) {
                q.guiLight = json.guiLight;
            }
        }

        // 6.1 Propagate the model-level ambientocclusion to quads whose element did not declare it individually.
        // Aligned with 26.1.2 SimpleModelWrapper.useAmbientOcclusion: the model-level flag drives
        // the AO/flat dispatch. Element-level "ambientocclusion" is a CatFrame extension and keeps
        // precedence (child overrides model default).
        if (json.ambientocclusion != null) {
            for (BakedQuad q : quads) {
                if (q.ambientOcclusion == null) {
                    q.ambientOcclusion = json.ambientocclusion;
                }
            }
        }

        // 7. display transforms are already propagated via BlockStateModelPart's partDisplay field,
        //    so they no longer need to be set on each BakedQuad

        // 8. Apply rotations (deep copy, does not pollute the base cache)
        //    The order must match Minecraft blockstate: X axis first, then Y, then Z.
        //    3D rotations do not commute; applying Y before X would place axis=x logs along the Z axis,
        //    and orient x:180 top stairs incorrectly (a 180° flip conjugates and negates the Y rotation).
        if (rotationX != 0) {
            quads = JsonModelBake.applyXRotation(quads, rotationX);
        }
        if (rotationY != 0) {
            quads = JsonModelBake.applyYRotation(quads, rotationY);
        }
        if (rotationZ != 0) {
            quads = JsonModelBake.applyZRotation(quads, rotationZ);
        }

        // 8.5 Post-pass atlas membership scan (aligned with 26.1.2 BakedQuad.MaterialInfo.of, which
        //     determines RenderType from sprite.atlasLocation()): executed after the rotation deep copy,
        //     so it naturally covers the copies; covers both element quads and side quads.
        for (BakedQuad q : quads) {
            q.blockAtlas = TextureSlots.isBlockAtlas(q.icon);
        }

        CatFrame.logger.debug("[ModelJsonUnbakedAdapter] bake: '{}' | elements={} | quads={} | rotX={} rotY={} rotZ={}",
                modelPath, json.elements.size(), quads.size(), rotationX, rotationY, rotationZ);

        // 9. Capture the textures.particle slot (the only bake-time retention point; the slot is null when missing —
        //    ParticleIconResolver maps missingno per high-version semantics).
        //    Reference chains ("#texture") have already been expanded by ModelResolver, and TextureSlots have been resolved to IIcon.
        IIcon particleIcon = textures.getIcon("particle");

        // 10. Wrap as BlockStateModelPart (carrying display transforms + the particle slot)
        return BlockStateModelPart.fromQuads(quads, json.display, particleIcon);
    }
}
