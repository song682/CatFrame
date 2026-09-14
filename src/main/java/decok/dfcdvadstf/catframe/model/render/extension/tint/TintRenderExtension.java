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
 * 内建渲染扩展：处理 JSON face 中的 {@code "tintindex"} 字段。
 * 根据 {@link RenderContext#phase} 分别从 {@link TintRegistry} 拿
 * 方块 / 物品颜色，乘入 {@link RenderContext#color}。
 *
 * <p>本扩展由 {@link decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry}
 * 在首次使用时自动安装到链头，模组通常不需要直接接触它——只需通过
 * {@link TintRegistry} 注册自定义染色规则即可。</p>
 */
public final class TintRenderExtension implements IModelRenderExtension {

    /** per-part 物品 tint 记忆缓存的最大 tintIndex 数（实际 tintIndex 很小）。 */
    private static final int MAX_CACHED_TINT = 16;

    /**
     * per-part 物品 tint 记忆缓存：同一次部件渲染内（一个 ItemStack 的所有 quad），
     * 每个 {@code tintIndex} 只解析一次，避免逐 quad 重复 {@code buildProperties} +
     * 决策树求值 + NBT 读取。线程局部（ThreadLocal）：渲染路径可从任意线程进入
     * （如 Beddium 多线程区块编译），各线程持有独立的记忆；在 {@link #beforePart} 重置。
     *
     * <p>Per-part item tint memo: within one part render (all quads of a single ItemStack)
     * each {@code tintIndex} is resolved once instead of per quad, avoiding repeated
     * property-map build, decision-tree eval and NBT reads. Thread-local: the render path
     * may enter from any thread (e.g. Beddium multithreaded chunk meshing), each thread
     * keeps its own memo; reset in {@link #beforePart}.
     */
    private final ThreadLocal<TintMemo> itemTintMemo = ThreadLocal.withInitial(TintMemo::new);

    /** 单线程 per-part tint 记忆体：{@code cached[i]} 标记 {@code values[i]} 是否已解析。 */
    private static final class TintMemo {
        final int[] values = new int[MAX_CACHED_TINT];
        final boolean[] cached = new boolean[MAX_CACHED_TINT];
    }

    @Override
    public void beforePart(List<BakedQuad> allQuads, RenderPhase phase, BlockStateModelPart part) {
        // 新部件 = 新 ItemStack，清空上一部件的 tint 记忆。
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
                // 落地方块：优先使用世界上下文获取生物群系染色，
                // 无世界上下文时回退到 Block.getRenderColor(0) 或物品染色
                if (ctx.block != null && ctx.world != null) {
                    rgb = TintRegistry.getBlockTint(ctx.world, ctx.x, ctx.y, ctx.z, ctx.block, idx);
                } else if (ctx.block != null) {
                    rgb = ctx.block.getRenderColor(0) & 0xFFFFFF;
                } else if (ctx.stack != null) {
                    // 无世界上下文时退回到物品染色（如树叶掉落物）
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
     * 经 per-part 记忆缓存解析物品 tint：命中则复用，未命中才真正调用
     * {@link TintRegistry#getItemTint}（内部走决策树求值 + NBT 读取）并存入缓存。
     * 同一部件内 {@code ctx.stack} 恒定，故按 {@code tintIndex} 缓存结果完全正确；
     * tintIndex 超出缓存范围时不缓存、直接透传。
     *
     * <p>Resolve an item tint through the per-part memo: reuse on hit, otherwise call
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
