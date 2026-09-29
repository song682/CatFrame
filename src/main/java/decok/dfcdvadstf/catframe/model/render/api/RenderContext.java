package decok.dfcdvadstf.catframe.model.render.api;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import java.util.Map;

/**
 * Context for rendering a single quad. The extension chain
 * {@link decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry#apply(RenderContext)}
 * iterates over {@link IModelRenderExtension} in registration order; each extension may read / modify this object.
 *
 * <h3>Field semantics</h3>
 * <ul>
 *   <li>Input fields (final): quad, phase, world/x/y/z/block, stack and other environment info.</li>
 *   <li>Output fields (mutable):
 *     <ul>
 *       <li>{@link #skip}: when true this quad is discarded (used for face culling).</li>
 *       <li>{@link #color}: 0xRRGGBB color multiplier, defaults to 0xFFFFFF.
 *           Prefer accumulating via {@link #mulColor(int)} so multiple extensions can stack.</li>
 *       <li>{@link #brightnessOverride}: when ≥0 this brightness is forced (for shading / emissive),
 *           -1 means reuse {@link #baselineBrightness}.</li>
 *       <li>{@link #shade}: directional lighting coefficient (top face 1.0, side 0.8, bottom 0.5, etc.),
 *           sent to the Tessellator together with color.</li>
 *       <li>{@link #aoBrightness} and {@link #aoColorMul}: per-vertex AO data (4 values per face),
 *           available only during the BLOCK_WORLD phase. When all values are -1 it degrades to uniform rendering.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class RenderContext {
    // ==================== Inputs ====================
    public final RenderPhase phase;
    public final BakedQuad quad;

    // Block-world only (item phases: world=null, x=y=z=0, block=null)
    public final IBlockAccess world;
    public final int x, y, z;
    public final Block block;

    // Item phases only (block phase: stack=null)
    public final ItemStack stack;

    /**
     * Blockstate properties, computed at model resolution time by CatFrame's dynamic property
     * resolver ({@code VanillaBlockResolvers}), for example the north/east/south/west connection
     * states of glass panes / iron bars, the facing/half/shape of stairs, the power of redstone wire, etc.
     * <p>
     * Available only during the {@link RenderPhase#BLOCK_WORLD} / {@link RenderPhase#BLOCK_DESTROY} phases;
     * null for item phases or blocks whose dynamic properties were not computed.
     * <p>
     * Read-only contract: returns an unmodifiable view that extensions must not modify; the reference is
     * valid only during the extension chain invocation (same frame, same thread) and must not be retained across frames.
     */
    @Nullable
    public final Map<String, String> blockstateProps;

    /**
     * Item properties, computed by CatFrame's item property system
     * ({@code ItemProperties.buildProperties}), for example damage / max_damage /
     * using_item / use_duration / display_context, etc., used to evaluate the items JSON decision tree.
     * <p>
     * Available only during the {@code ITEM_*} phases; null for block phases.
     * <p>
     * Read-only lazy map ({@code LazyPropertyMap}): reading a key triggers the corresponding provider's
     * computation and caching; avoid full traversals via {@code entrySet()/values()} (they trigger all evaluations).
     * The reference is valid only during the extension chain invocation (same frame, same thread) and must not be retained across frames.
     */
    @Nullable
    public final Map<String, Comparable<?>> itemProps;

    /**
     * [S1] The block's metadata value, carried by the render pipeline on submit (historically used for GUI block tinting).
     * Defaults to 0 and is set by the render pipeline when metadata is known.
     */
    public int metadata = 0;

    /**
     * Base brightness precomputed by the renderer (from neighboring block light). Extensions may read but not modify it.
     */
    public final int baselineBrightness;
    /**
     * Per-vertex AO brightness (packed int, skyLight<<16 | blockLight format).
     * -1 means the vertex has no per-vertex data and degrades to the uniform {@link #effectiveBrightness()}.
     * Filled in by VanillaModelManager only during the {@link RenderPhase#BLOCK_WORLD} phase.
     */
    public final int[] aoBrightness = {-1, -1, -1, -1};
    /**
     * Per-vertex AO occlusion coefficient (0.0~1.0, 1.0 = no occlusion), equivalent to vanilla block.getAmbientOcclusionLightValue().
     * Multiplied into the final color at render time: {@code finalColor = color * shade * aoColorMul[i]}.
     */
    public final float[] aoColorMul = {1.0f, 1.0f, 1.0f, 1.0f};
    // ==================== Outputs (mutable) ====================
    public boolean skip = false;
    public int color = 0xFFFFFF;
    public int brightnessOverride = -1;
    public float shade;
    /**
     * Texture override. When this field is non-null, the renderer uses this IIcon instead of
     * {@link BakedQuad#icon} for UV sampling.
     * Suitable for runtime texture switching (for example swapping leaf textures based on graphics quality).
     */
    public IIcon iconOverride = null;

    /**
     * Per-vertex UV override (model space 0-16, same units as {@link BakedQuad#up}/{@link BakedQuad#vp}).
     * <p>
     * null = no override (default); non-null must be an array of length 8, laid out as
     * {@code [u0,v0,u1,v1,u2,v2,u3,v3]}, corresponding to pipeline vertex call order 0..3. The pipeline uses this
     * array instead of {@code q.up[i]}/{@code q.vp[i]} as the arguments to {@code icon.getInterpolatedU/V}:
     * the sprite's padding / UV shrink / animation frames / atlas mapping are still entirely handled by icon. Orthogonal
     * to {@link #iconOverride} (the latter decides which sprite to use, this field decides the sampling position on it).
     * <p>
     * The reference is valid only during the extension chain invocation (same frame, same thread) and must not be
     * retained across frames; extensions should own and reuse the array (render hot path, must not allocate per quad).
     * The destroy decal projection and the solidColor textureless pass do not consume this field; read it via
     * {@link #effectiveUvOverride()} (which includes length defense).
     */
    @Nullable
    public float[] uvOverride = null;

    /**
     * Display transform matrix (vector space).
     * Computed and set by {@link decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension}
     * during the extension chain; the pipeline applies this matrix to transform vertex coordinates before submitting vertices.
     */
    @Nullable
    public Matrix4d displayTransform = null;

    /**
     * Legacy-signature constructor compatibility shim: blockstateProps / itemProps are both null.
     */
    public RenderContext(RenderPhase phase, BakedQuad quad,
                         IBlockAccess world, int x, int y, int z, Block block,
                         ItemStack stack,
                         int baselineBrightness, float defaultShade) {
        this(phase, quad, world, x, y, z, block, stack,
                baselineBrightness, defaultShade, null, null);
    }

    public RenderContext(RenderPhase phase, BakedQuad quad,
                         IBlockAccess world, int x, int y, int z, Block block,
                         ItemStack stack,
                         int baselineBrightness, float defaultShade,
                         @Nullable Map<String, String> blockstateProps,
                         @Nullable Map<String, Comparable<?>> itemProps) {
        this.phase = phase;
        this.quad = quad;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.block = block;
        this.stack = stack;
        this.baselineBrightness = baselineBrightness;
        this.shade = defaultShade;
        this.blockstateProps = blockstateProps;
        this.itemProps = itemProps;
    }

    /**
     * Multiplies the given 0xRRGGBB color into the current {@link #color} per channel.
     * Suited to "stacking"-style extensions (such as Tint + armor darkening).
     */
    public void mulColor(int rgb) {
        int r0 = (color >> 16) & 0xFF, g0 = (color >> 8) & 0xFF, b0 = color & 0xFF;
        int r1 = (rgb >> 16) & 0xFF, g1 = (rgb >> 8) & 0xFF, b1 = rgb & 0xFF;
        int r = (r0 * r1) / 255;
        int g = (g0 * g1) / 255;
        int b = (b0 * b1) / 255;
        this.color = (r << 16) | (g << 8) | b;
    }

    /**
     * The final brightness in use (override takes priority, otherwise baseline).
     */
    public int effectiveBrightness() {
        return brightnessOverride >= 0 ? brightnessOverride : baselineBrightness;
    }

    /**
     * The UV override array when its length is valid; returns null when null or shorter than 8 (treated as no override).
     */
    @Nullable
    public float[] effectiveUvOverride() {
        return (uvOverride != null && uvOverride.length >= 8) ? uvOverride : null;
    }
}
