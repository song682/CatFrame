package decok.dfcdvadstf.catframe.mixin.middle;

import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 空几何保护：被 CatFrame 完全接管（方块几何收集到 CatAtlas 后期批次）的 chunk
 * 不再向 vanilla Tessellator 写入任何顶点，此时 vanilla {@code getVertexState} 内部
 * {@code new PriorityQueue(vertexCount / 4, ...)} 以容量 0 构造而抛
 * {@link IllegalArgumentException}（Java 8 的 {@code PriorityQueue} 要求容量 ≥ 1）。
 * <p>
 * 顶点数不足一个 quad 时直接返回 null：调用方 {@code WorldRenderer.postRenderBlocks}
 * 把 null 存入 chunk 的 VertexState 字段，{@code renderChunk} 的 null 检查自动跳过
 * 绘制；Tessellator 的 isDrawing 保持原状，vanilla 后续流程（display list / VBO 提交）
 * 照常执行 —— 不提前 draw，避免破坏批次状态。
 * <p>
 * Empty-geometry guard: fully CatFrame-owned chunks write no vanilla vertices, so
 * getVertexState would build a 0-capacity PriorityQueue; return null instead and let
 * renderChunk's null check skip the chunk while the vanilla batch flow continues.
 */
@Mixin(Tessellator.class)
public class MixinTessellator {

    @Shadow
    private int vertexCount;

    @Inject(method = "getVertexState", at = @At("HEAD"), cancellable = true)
    private void catframe$guardZeroVertices(CallbackInfoReturnable<Object> cir) {
        if (this.vertexCount < 4) {
            cir.setReturnValue(null);
        }
    }
}
