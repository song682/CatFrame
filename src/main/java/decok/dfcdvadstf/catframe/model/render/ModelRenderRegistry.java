package decok.dfcdvadstf.catframe.model.render;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension;
import decok.dfcdvadstf.catframe.model.render.extension.BlockDestroyExtension;
import decok.dfcdvadstf.catframe.model.render.extension.FaceCullExtension;
import decok.dfcdvadstf.catframe.model.render.extension.GuiLightExtension;
import decok.dfcdvadstf.catframe.model.render.extension.LightPolicyExtension;
import decok.dfcdvadstf.catframe.model.render.extension.ao.AOComputeExtension;
import decok.dfcdvadstf.catframe.model.render.extension.ao.AOShadeExtension;
import decok.dfcdvadstf.catframe.model.render.extension.tint.TintRenderExtension;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * General model render extension registry (internal implementation).
 * <p>
 * <b>External entry point</b>: external mods should register extensions via the
 * {@link decok.dfcdvadstf.catframe.model.render.api.ModelRenderExtensions} facade;
 * this class's registration API is reserved for internal use (lazy loading of built-in extensions).
 * By registering an {@link IModelRenderExtension}, mods can modify, cull, tint, or brighten each quad
 * baked by CatFrame.
 *
 * <h3>When extensions are applied</h3>
 * VanillaModelManager calls {@link #apply(RenderContext)} in three places: block world rendering,
 * item GUI rendering, and item hand rendering. Extensions should decide whether to take effect based
 * on {@link RenderContext#phase}.
 *
 * <h3>Built-in extensions (in registration order)</h3>
 * <ul>
 *   <li>{@link AOComputeExtension}: head of the chain. In the BLOCK_WORLD phase, computes per-vertex
 *       AO and writes results to {@link RenderContext#aoBrightness} and {@link RenderContext#aoColorMul}.</li>
 *   <li>{@link LightPolicyExtension}: phase default brightness policy. For item phases, computes
 *       via beforePart relay and writes to {@link RenderContext#brightnessOverride} in apply;
 *       for block world, only computes default brightness for AO-degraded quads. Must run after AOCompute.</li>
 *   <li>{@link FaceCullExtension}: processes {@code "cullface"} in JSON faces and automatically
 *       culls hidden faces based on whether adjacent blocks are fully opaque.</li>
 *   <li>{@link AOShadeExtension}: processes {@code "ambientocclusion"} and {@code "shade"} in
 *       JSON elements to control brightness and directional shading for emissive/uniformly lit blocks.</li>
 *   <li>{@link GuiLightExtension}: processes {@code "gui_light"} in JSON models to control
 *       directional shading and GL_LIGHTING state.</li>
 *   <li>{@link TintRenderExtension}: processes {@code "tintindex"} in JSON faces and automatically
 *       calls {@link decok.dfcdvadstf.catframe.model.render.extension.tint.TintRegistry} to provide
 *       biome- or NBT-based tinting for grass blocks, leaves, water, dyed items, etc.</li>
 *   <li>{@link DisplayTransformExtension}: processes {@code "display"} in JSON models and applies
 *       corresponding GL transforms (translation, rotation, scale) for GUI and hand rendering.</li>
 * </ul>
 *
 * <h3>Priority contract (v0.5+)</h3>
 * Each extension has an integer {@code priority}: <b>smaller priority values execute first</b>.
 * Extensions with equal priority are stably ordered by registration order. Built-in extensions use
 * negative priorities to stay at the head of the chain; mod extensions default to
 * {@link #DEFAULT_PRIORITY}=0 (so they see modifications from built-in extensions). To insert before
 * built-in extensions, pass a priority smaller than {@link #BUILTIN_PRIORITY_BASE}. If any extension
 * sets {@link RenderContext#skip} to true, the chain terminates immediately and that quad is not drawn.
 *
 * <h3>Error isolation (v0.5+)</h3>
 * Exceptions (including Errors) thrown by a single extension are caught and logged; they do not
 * affect subsequent extensions in the chain or the overall render.
 *
 * <h3>Thread safety (v0.5+)</h3>
 * The extension list is a {@link CopyOnWriteArrayList} (concurrent-safe for registration/unregistration
 * and render traversal). The render path may enter from any thread (e.g., Beddium multithreaded chunk
 * compilation). Extension instances themselves must not hold cross-thread mutable shared state
 * (use ThreadLocal for per-instance temporary state, or write to {@link RenderContext}).
 */
@SideOnly(Side.CLIENT)
public final class ModelRenderRegistry {

    /** Default priority for mod extensions: extensions with priority 0 run after built-in extensions (negative priority). */
    public static final int DEFAULT_PRIORITY = 0;

    /** Priority base for built-in extensions: FaceCull runs first, followed by other built-ins with increasing priority. */
    public static final int BUILTIN_PRIORITY_BASE = -1000;

    private static final List<ExtEntry> EXTS = new CopyOnWriteArrayList<>();
    private static boolean defaultsInstalled = false;

    private ModelRenderRegistry() {
    }

    /**
     * Register a custom render extension (default priority 0, appended to the end of the same-priority group).
     * Mods should call this during client init. Registering the same instance again repositions it with the new priority.
     */
    public static void register(IModelRenderExtension ext) {
        register(ext, DEFAULT_PRIORITY);
    }

    /**
     * Register a render extension with explicit priority. Smaller priority values execute first;
     * extensions with equal priority are stably ordered by registration order. Registering the same
     * instance again updates its priority and repositions it.
     *
     * @param ext      render extension (null ignored)
     * @param priority priority (negative values can be inserted before built-in extensions)
     */
    public static void register(IModelRenderExtension ext, int priority) {
        ensureDefaults();
        if (ext == null) return;
        // Remove old entry if re-registering to ensure the same instance appears only once with the latest priority
        EXTS.removeIf(e -> e.ext == ext);
        insertSorted(ext, priority);
    }

    /**
     * Unregister an extension.
     */
    public static void unregister(IModelRenderExtension ext) {
        if (ext != null) EXTS.removeIf(e -> e.ext == ext);
    }

    /**
     * Number of currently registered extensions (including built-ins).
     */
    public static int size() {
        ensureDefaults();
        return EXTS.size();
    }

    /**
     * Internal use by renderer: calls each extension's {@link IModelRenderExtension#beforePart} in priority order.
     * Called once before processing the quad list. Exceptions from individual extensions are isolated (logged and then execution continues).
     */
    public static void applyBeforePart(List<BakedQuad> allQuads, RenderPhase phase, BlockStateModelPart part) {
        ensureDefaults();
        for (ExtEntry e : EXTS) {
            try {
                e.ext.beforePart(allQuads, phase, part);
            } catch (Throwable t) {
                logExtError(e, "beforePart", t);
            }
        }
    }

    /**
     * Internal use by renderer: calls each extension's {@link IModelRenderExtension#afterPart} in priority order.
     * Called once after processing all quads. Exceptions from individual extensions are isolated (logged and then execution continues).
     */
    public static void applyAfterPart() {
        ensureDefaults();
        for (ExtEntry e : EXTS) {
            try {
                e.ext.afterPart();
            } catch (Throwable t) {
                logExtError(e, "afterPart", t);
            }
        }
    }

    /**
     * Internal use by renderer: applies all extensions in priority order.
     * If {@link RenderContext#skip} is set to true, the chain terminates immediately.
     * Exceptions from individual extensions are isolated (logged and then execution continues for subsequent extensions).
     */
    public static void apply(RenderContext ctx) {
        ensureDefaults();
        for (ExtEntry e : EXTS) {
            try {
                e.ext.apply(ctx);
            } catch (Throwable t) {
                logExtError(e, "apply", t);
            }
            if (ctx.skip) return;
        }
    }

    /**
     * Log exceptions from a single extension (error isolation).
     */
    private static void logExtError(ExtEntry e, String hook, Throwable t) {
        CatFrame.logger.warn("[ModelRenderRegistry] extension {} failed in {}(): {}",
                e.ext.getClass().getName(), hook, t.toString(), t);
    }

    /**
     * Stable insertion: find the first entry with priority > new priority and insert before it.
     * Entries with equal priority keep their relative order; new entry is appended to the end of the same group.
     */
    private static void insertSorted(IModelRenderExtension ext, int priority) {
        int insertAt = EXTS.size();
        for (int i = 0; i < EXTS.size(); i++) {
            if (EXTS.get(i).priority > priority) {
                insertAt = i;
                break;
            }
        }
        EXTS.add(insertAt, new ExtEntry(ext, priority));
    }

    /**
     * Install built-in extensions (lazy loading, triggered on first register or apply).
     * Built-in extensions use negative priorities starting from {@link #BUILTIN_PRIORITY_BASE} in increasing order,
     * staying at the head of the chain in their defined order.
     */
    private static void ensureDefaults() {
        if (defaultsInstalled) return;
        defaultsInstalled = true;
        int p = BUILTIN_PRIORITY_BASE;
        // FaceCullExtension must run before AOComputeExtension: cull hidden faces before computing AO
        insertSorted(new FaceCullExtension(), p++);
        insertSorted(new AOComputeExtension(), p++);
        // Phase default brightness policy: must run after AOCompute (reads aoBrightness to detect AO-degraded quads).
        // Item brightness is computed via beforePart relay (relay protocol documented in LightPolicyExtension).
        insertSorted(new LightPolicyExtension(), p++);
        insertSorted(new AOShadeExtension(), p++);
        insertSorted(new GuiLightExtension(), p++);
        insertSorted(new TintRenderExtension(), p++);
        insertSorted(new DisplayTransformExtension(), p++);
        // Destroy decal extension: only active in the BLOCK_DESTROY phase (iconOverride + full brightness + pure white).
        insertSorted(new BlockDestroyExtension(), p++);
    }

    /**
     * Extension entry: extension instance and registration priority.
     */
    private static final class ExtEntry {
        final IModelRenderExtension ext;
        final int priority;

        ExtEntry(IModelRenderExtension ext, int priority) {
            this.ext = ext;
            this.priority = priority;
        }
    }
}
