package decok.dfcdvadstf.catframe.model.render.extension.tint;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * Built-in render extension: handles the {@code "tintindex"} field on JSON faces.
 * Depending on {@link RenderContext#phase} it fetches a block / item colour from
 * {@link TintRegistry} respectively and multiplies it into {@link RenderContext#color}.
 *
 * <p>This extension is installed automatically at the head of the chain on first use
 * by {@link decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry}; mods normally
 * do not need to touch it directly — they only need to register custom tint rules
 * through {@link TintRegistry}.</p>
 */
public final class TintRenderExtension implements IModelRenderExtension {

    /** Maximum tintIndex count of the per-part item tint memo (real tintIndex values are tiny). */
    private static final int MAX_CACHED_TINT = 16;

    /**
     * Per-part item tint memo: within one part render (all quads of a single ItemStack)
     * each {@code tintIndex} is resolved once instead of per quad, avoiding repeated
     * property-map build, decision-tree eval and NBT reads. Thread-local: the render path
     * may enter from any thread (e.g. Beddium multithreaded chunk meshing), each thread
     * keeps its own memo; reset in {@link #beforePart}.
     */
    private final ThreadLocal<TintMemo> itemTintMemo = ThreadLocal.withInitial(TintMemo::new);

    /** Single-threaded per-part tint memo: {@code cached[i]} marks whether {@code values[i]} has been resolved. */
    private static final class TintMemo {
        final int[] values = new int[MAX_CACHED_TINT];
        final boolean[] cached = new boolean[MAX_CACHED_TINT];
    }

    @Override
    public void beforePart(List<BakedQuad> allQuads, RenderPhase phase, BlockStateModelPart part) {
        // New part = new ItemStack; drop the previous part's tint memo.
        Arrays.fill(itemTintMemo.get().cached, false);
    }

    @Override
    public void apply(RenderContext ctx) {
        int idx = ctx.quad.tintIndex;
        if (idx < 0) return;

        int rgb;
        switch (ctx.phase) {
            case BLOCK_WORLD:
                rgb = TintRegistry.getBlockTint(ctx.world, ctx.x, ctx.y, ctx.z, ctx.block, idx);
                break;
            case ITEM_GUI:
            case ITEM_HAND_FIRST_PERSON:
            case ITEM_HAND_THIRD_PERSON:
            case DROPPED_ITEM_GROUND:
            case ITEM_FIXED:
                rgb = itemTint(ctx.stack, idx);
                break;
            case DROPPED_BLOCK_GROUND:
                // Dropped block: prefer the world context to obtain biome tinting,
                // falling back to Block.getRenderColor(0) or item tinting without one.
                if (ctx.block != null && ctx.world != null) {
                    rgb = TintRegistry.getBlockTint(ctx.world, ctx.x, ctx.y, ctx.z, ctx.block, idx);
                } else if (ctx.block != null) {
                    rgb = ctx.block.getRenderColor(0) & 0xFFFFFF;
                } else if (ctx.stack != null) {
                    // Without a world context, fall back to item tinting (e.g. a dropped leaves item).
                    rgb = itemTint(ctx.stack, idx);
                } else {
                    return;
                }
                break;
            default:
                return;
        }

        if (rgb != 0xFFFFFF) ctx.mulColor(rgb);
    }

    /**
     * Resolve an item tint through the per-part memo: reuse on hit, otherwise call
     * {@link TintRegistry#getItemTint} (decision-tree eval + NBT read) once and store it.
     * The stack is constant within a part, so keying the result by {@code tintIndex} is
     * exact; indices outside the cache range are passed through uncached.
     */
    private int itemTint(ItemStack stack, int idx) {
        if (idx < 0 || idx >= MAX_CACHED_TINT) {
            return TintRegistry.getItemTint(stack, idx);
        }
        TintMemo memo = itemTintMemo.get();
        if (!memo.cached[idx]) {
            memo.values[idx] = TintRegistry.getItemTint(stack, idx);
            memo.cached[idx] = true;
        }
        return memo.values[idx];
    }
}
