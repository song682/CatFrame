package decok.dfcdvadstf.catframe.model.render.extension;

import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
import java.util.List;

/**
 * Built-in extension that handles {@code display} transforms in JSON models,
 * semantically aligned with 26.1 {@code ItemTransform.apply()}.
 *
 * <h3>Transformation order (vertex space, vector form)</h3>
 * <ol>
 *   <li>{@code translate(-0.5, -0.5, -0.5)} — move model origin from block corner to center</li>
 *   <li>{@code scale(sx, sy, sz)} — display scale</li>
 *   <li>{@code rotate(rx) → rotate(ry) → rotate(rz)} — X→Y→Z rotation order
 *       (aligned with 26.1 {@code Quaternionf.rotationXYZ})</li>
 *   <li>{@code translate(tx, ty, tz)} — display translation (pixels × 0.0625 = block units)</li>
 * </ol>
 *
 * <h3>Field defaults and clamping (aligned with modern display transform spec)</h3>
 * <table border="1">
 *   <caption>display transform field specification</caption>
 *   <tr><th>Field</th><th>Default</th><th>Clamp range</th></tr>
 *   <tr><td>rotation</td><td>[0, 0, 0]</td><td>unlimited (degrees)</td></tr>
 *   <tr><td>translation</td><td>[0, 0, 0]</td><td>[-80, 80] (pixels, 1/16 block)</td></tr>
 *   <tr><td>scale</td><td>[1, 1, 1]</td><td>[-4, 4]</td></tr>
 * </table>
 *
 * <p>This implementation models display transforms as a {@link Matrix4d} and passes it via
 * {@link RenderContext#displayTransform} to the pipeline, which applies the matrix to vertex
 * coordinates before submission. No GL matrix stack is used.</p>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>{@code beforePart} — compute transform matrix based on phase and part.getDisplay()</li>
 *   <li>{@code apply} — write matrix to ctx.displayTransform for pipeline consumption</li>
 *   <li>{@code afterPart} — clear matrix reference</li>
 * </ul>
 *
 * <p>This extension is registered as a built-in extension in
 * {@link decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry} and dispatched by the pipeline.</p>
 */
public final class DisplayTransformExtension implements IModelRenderExtension {

    /**
     * Current part's display transform matrix, set in beforePart and cleared in afterPart.
     * Thread-local: rendering may enter from any thread (e.g. Beddium multithreaded chunk
     * compilation); sharing instance fields across threads would cause matrix overwrites.
     */
    private final ThreadLocal<Matrix4d> currentMatrix = new ThreadLocal<>();

    // ====== Diagnostic logging: state-driven (trigger on phase/key/dt signature changes, not per frame) ======
    // Diagnostic state is also thread-local to avoid cross-thread races (each thread tracks its own last state).
    private static final Logger LOGGER = LogManager.getLogger(DisplayTransformExtension.class);
    private static final ThreadLocal<RenderPhase> LAST_DISPLAY_PHASE = new ThreadLocal<>();
    private static final ThreadLocal<String> LAST_DISPLAY_KEY = new ThreadLocal<>();
    private static final ThreadLocal<String> LAST_DT_SIG = new ThreadLocal<>();

    @Override
    public void beforePart(List<BakedQuad> allQuads, RenderPhase phase, BlockStateModelPart part) {
        // Determine display key for current phase
        String displayKey = phase.getDisplayKey();
        if (displayKey == null) {
            currentMatrix.set(null);
            return;
        }

        // Get display transforms from part level (no longer read from BakedQuad.modelDisplay)
        ModelJson.DisplayTransform dt = null;
        if (part != null && part.getDisplay() != null) {
            dt = part.getDisplay().get(displayKey);
        }

        // ====== State-driven diagnostics: trigger only on phase/key/dt signature changes ======
        boolean shouldLog = false;
        if (CatFrameConfig.shouldLogDebug()) {
            String dtSig = dt != null ? String.format("rot=%s t=%s s=%s",
                    dt.rotation != null ? java.util.Arrays.toString(dt.rotation) : "null",
                    dt.translation != null ? java.util.Arrays.toString(dt.translation) : "null",
                    dt.scale != null ? java.util.Arrays.toString(dt.scale) : "null") : "NULL";
            RenderPhase lastPhase = LAST_DISPLAY_PHASE.get();
            String lastKey = LAST_DISPLAY_KEY.get();
            String lastSig = LAST_DT_SIG.get();
            boolean phaseChanged = (phase != lastPhase);
            boolean keyChanged = (displayKey != null && !displayKey.equals(lastKey));
            boolean dtChanged = !dtSig.equals(lastSig);
            shouldLog = (phaseChanged || keyChanged || dtChanged);
            if (shouldLog) {
                LAST_DISPLAY_PHASE.set(phase);
                LAST_DISPLAY_KEY.set(displayKey);
                LAST_DT_SIG.set(dtSig);
                LOGGER.info(String.format("[DFXDBG] DisplayExt phase=%s key=%s dt=%s",
                        phase.name(), displayKey, dtSig));
            }
        }
        // ====== End of diagnostics ======

        // Use default transform based on display key when dt is null
        if (dt == null) {
            dt = getDefaultTransform(displayKey);
            if (shouldLog) LOGGER.info(String.format("[DFXDBG] DisplayExt DEFAULT: key=%s dt=rot=%s t=%s s=%s",
                    displayKey,
                    java.util.Arrays.toString(dt.rotation),
                    java.util.Arrays.toString(dt.translation),
                    java.util.Arrays.toString(dt.scale)));
        }

        // Compute display transform matrix (vector space)
        currentMatrix.set(computeMatrix(dt));
    }

    @Override
    public void apply(RenderContext ctx) {
        ctx.displayTransform = currentMatrix.get();
    }

    @Override
    public void afterPart() {
        currentMatrix.remove();
    }

    /**
     * Clamp range for translation components (pixels, 1/16 block).
     * Aligned with modern ItemTransform.Deserializer spec.
     */
    private static final float TRANSLATION_CLAMP = 80.0f;

    /** Clamp range for scale components, aligned with modern ItemTransform.Deserializer spec. */
    private static final float SCALE_CLAMP = 4.0f;

    /**
     * Compute 4×4 transform matrix from display transform data.
     *
     * <p>Vertex transform order (applied to vertex v):
     * {@code v' = T(display) × RX × RY × RZ × S(display) × T(-0.5) × v}
     *
     * <p>Aligned with 26.1 ItemTransform.apply() semantics, consistent with legacy GL immediate mode
     * matrix computation.
     *
     * <p>Constraints applied when reading fields (modern spec):
     * <ul>
     *   <li>null/insufficient length → replace with defaults (rotation=[0,0,0], translation=[0,0,0], scale=[1,1,1])</li>
     *   <li>translation components clamped to [-80, 80]</li>
     *   <li>scale components clamped to [-4, 4]</li>
     *   <li>rotation has no range limit (degrees, free range)</li>
     * </ul>
     *
     * @param dt display transform data
     * @return 4×4 transform matrix
     */
    private static Matrix4d computeMatrix(ModelJson.DisplayTransform dt) {
        if (dt == null) return null;

        // ===== Read fields with defaults + clamping =====
        // Defaults: rotation=[0,0,0], translation=[0,0,0], scale=[1,1,1]
        // Clamping: translation ∈ [-80,80], scale ∈ [-4,4], rotation unrestricted
        float rx = (dt.rotation != null && dt.rotation.length > 0) ? dt.rotation[0] : 0f;
        float ry = (dt.rotation != null && dt.rotation.length > 1) ? dt.rotation[1] : 0f;
        float rz = (dt.rotation != null && dt.rotation.length > 2) ? dt.rotation[2] : 0f;
        float tx = clamp((dt.translation != null && dt.translation.length > 0) ? dt.translation[0] : 0f,
                -TRANSLATION_CLAMP, TRANSLATION_CLAMP);
        float ty = clamp((dt.translation != null && dt.translation.length > 1) ? dt.translation[1] : 0f,
                -TRANSLATION_CLAMP, TRANSLATION_CLAMP);
        float tz = clamp((dt.translation != null && dt.translation.length > 2) ? dt.translation[2] : 0f,
                -TRANSLATION_CLAMP, TRANSLATION_CLAMP);
        float sx = clamp((dt.scale != null && dt.scale.length > 0) ? dt.scale[0] : 1f,
                -SCALE_CLAMP, SCALE_CLAMP);
        float sy = clamp((dt.scale != null && dt.scale.length > 1) ? dt.scale[1] : 1f,
                -SCALE_CLAMP, SCALE_CLAMP);
        float sz = clamp((dt.scale != null && dt.scale.length > 2) ? dt.scale[2] : 1f,
                -SCALE_CLAMP, SCALE_CLAMP);

        Matrix4d m = new Matrix4d();
        m.setIdentity();

        // 4) translate(display) — pixels × 0.0625 = block units
        Matrix4d tDisplay = new Matrix4d();
        tDisplay.setIdentity();
        tDisplay.setTranslation(new Vector3d(tx * 0.0625, ty * 0.0625, tz * 0.0625));
        m.mul(tDisplay);

        // 3) rotate XYZ — aligned with 26.1 Quaternionf.rotationXYZ
        Matrix4d r = new Matrix4d();
        r.rotX(Math.toRadians(rx));
        m.mul(r);
        r.rotY(Math.toRadians(ry));
        m.mul(r);
        r.rotZ(Math.toRadians(rz));
        m.mul(r);

        // 2) scale
        Matrix4d s = new Matrix4d();
        s.setIdentity();
        s.m00 = sx; s.m11 = sy; s.m22 = sz;
        m.mul(s);

        // 1) translate(-0.5) — center offset (model origin at block corner in high-version models)
        Matrix4d tCenter = new Matrix4d();
        tCenter.setIdentity();
        tCenter.setTranslation(new Vector3d(-0.5, -0.5, -0.5));
        m.mul(tCenter);

        return m;
    }

    /** Clamp value to [min, max]. */
    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    /**
     * Return default transform for specified display key, aligned with 26.1.2 ItemTransform Deserializer defaults.
     *
     * <p>Provides reasonable defaults when model JSON lacks data for a given display key.</p>
     *
     * @param displayKey display key name
     * @return default DisplayTransform instance
     */
    private static ModelJson.DisplayTransform getDefaultTransform(String displayKey) {
        ModelJson.DisplayTransform dt = new ModelJson.DisplayTransform();
        dt.rotation = new float[]{0, 0, 0};
        dt.scale = new float[]{1, 1, 1};

        if (displayKey == null) {
            dt.translation = new float[]{0, 0, 0};
            return dt;
        }

        switch (displayKey) {
            case "firstperson_righthand":
                dt.rotation = new float[]{0, -90, 25};
                dt.translation = new float[]{1.13f, 3.2f, 1.13f};
                dt.scale = new float[]{0.68f, 0.68f, 0.68f};
                break;
            case "thirdperson_righthand":
            case "thirdperson_lefthand":
                // Default thirdperson left hand matches right hand (Y/Z rotations are 0, so mirroring yields same result);
                // if model JSON explicitly defines thirdperson_lefthand (e.g. handheld.json / stairs.json),
                // beforePart reads model data first and takes over; this default is not used in that case.
                dt.rotation = new float[]{0, 0, 0};
                dt.translation = new float[]{0, 3, 1};
                dt.scale = new float[]{0.55f, 0.55f, 0.55f};
                break;
            case "gui":
                dt.translation = new float[]{0, 0, 0};
                break;
            case "ground":
                dt.translation = new float[]{0, 2, 0};
                dt.scale = new float[]{0.5f, 0.5f, 0.5f};
                break;
            case "fixed":
                dt.translation = new float[]{0, 0, 0};
                dt.scale = new float[]{1, 1, 1};
                break;
            case "head":
                dt.rotation = new float[]{0, 180, 0};
                dt.translation = new float[]{0, 13, 7};
                break;
            default:
                dt.translation = new float[]{0, 0, 0};
                break;
        }
        return dt;
    }
}

