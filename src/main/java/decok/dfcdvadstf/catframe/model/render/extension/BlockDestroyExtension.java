package decok.dfcdvadstf.catframe.model.render.extension;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.util.IIcon;

/**
 * Rendering extension: block destroy overlay (crack overlay).
 * <p>
 * During {@link RenderPhase#BLOCK_DESTROY} (inside vanilla
 * {@code RenderGlobal.drawBlockDamageTexture} batch), for each quad:
 * <ul>
 *   <li>Sets {@link RenderContext#iconOverride} to the current destroy-stage icon
 *       ({@code destroy_stage_0~9}) injected by
 *       {@link decok.dfcdvadstf.catframe.model.RenderDispatcher#renderBlockDestroy}
 *       via {@link #setCurrentIcon};</li>
 *   <li>Forces full brightness (15728880), no directional shading (shade=1.0), pure white vertex
 *       color — matching 26.1.2 {@code BlockFeatureRenderer} destroy overlay semantics so that
 *       destroy decals remain clearly visible in darkness; crack effects are produced purely by
 *       the texture's black/white pixels and multiplicative blending.</li>
 * </ul>
 * <p>
 * <b>Thread safety</b>: {@link #CURRENT_ICON} is thread-local —
 * {@code renderBlockUsingTexture} is only called on the main thread during
 * {@code drawBlockDamageTexture}, and set/clear are wrapped in try/finally by the caller around
 * synchronized flushInline, so no thread leakage occurs.
 */
@SideOnly(Side.CLIENT)
public final class BlockDestroyExtension implements IModelRenderExtension {

    /** Current destroy icon being rendered on this thread (one of destroy_stage_0~9). */
    private static final ThreadLocal<IIcon> CURRENT_ICON = new ThreadLocal<>();

    public BlockDestroyExtension() {
    }

    /**
     * Set the current destroy icon for this thread. Must be called before flush and followed by
     * {@link #clearCurrentIcon()} after flush (caller must ensure via try/finally).
     *
     * @param icon current destroy-stage IIcon (null-safe, ignored)
     */
    public static void setCurrentIcon(IIcon icon) {
        CURRENT_ICON.set(icon);
    }

    /**
     * Clear the current destroy icon for this thread (called after flush).
     */
    public static void clearCurrentIcon() {
        CURRENT_ICON.remove();
    }

    @Override
    public void apply(RenderContext ctx) {
        // Only active during the destroy overlay phase
        if (ctx.phase != RenderPhase.BLOCK_DESTROY) return;

        IIcon icon = CURRENT_ICON.get();
        if (icon != null) {
            ctx.iconOverride = icon;
        }
        // Match 26.1.2 BlockFeatureRenderer: full light + white vertex color so destroy decals remain visible in darkness
        ctx.brightnessOverride = 15728880;
        ctx.shade = 1.0f;
        ctx.color = 0xFFFFFF;
    }
}
