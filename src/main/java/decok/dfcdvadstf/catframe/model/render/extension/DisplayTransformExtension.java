package decok.dfcdvadstf.catframe.model.render.extension;

import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.ModelJson;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import org.lwjgl.opengl.GL11;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;

import java.util.List;

/**
 * 内建渲染扩展：处理 JSON model 中的 {@code display} transforms，
 * 语义对齐 26.1 {@code ItemTransform.apply()}。
 *
 * <h3>变换顺序（顶点空间）</h3>
 * <ol>
 *   <li>{@code translate(-0.5, -0.5, -0.5)} — 模型原点从方块角移到中心</li>
 *   <li>{@code scale(sx, sy, sz)} — display 缩放</li>
 *   <li>{@code rotate(rx) → rotate(ry) → rotate(rz)} — X→Y→Z 顺序旋转
 *       （对齐 26.1 Quaternionf.rotationXYZ 语义。
 *        GL 代码中先写 RX 再写 RY 最后 RZ，
 *        矩阵结果为 RX × RY × RZ，
 *        顶点变换顺序为 RZ → RY → RX）</li>
 *   <li>{@code translate(tx, ty, tz)} — display 位移（像素 × 0.0625 = 方块单位）</li>
 * </ol>
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>{@code beforePart} — 读取第一个 quad 的 modelDisplay，确定 display key，
 *       执行 {@code glPushMatrix()} 并应用 transform</li>
 *   <li>{@code apply} — 无需操作（transform 已在 beforePart 应用）</li>
 *   <li>{@code afterPart} — 执行 {@code glPopMatrix()} 恢复矩阵</li>
 * </ul>
 *
 * <p>本扩展作为内建扩展注册到 {@link decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry}
 * 扩展链中，由管线统一调度，无需在管线中直接调用 GL 矩阵操作。
 */
public final class DisplayTransformExtension implements IModelRenderExtension {

    /** {@code true} 表示 beforePart 执行了 pushMatrix，afterPart 需要 popMatrix */
    private boolean matrixPushed = false;

    // ====== 诊断日志：状态驱动（按 phase/key/dt 签名变更触发，不逐帧打印） ======
    private static final Logger LOGGER = LogManager.getLogger(DisplayTransformExtension.class);
    private static RenderPhase lastDisplayPhase = null;
    private static String lastDisplayKey = null;
    private static String lastDtSig = null;

    @Override
    public void beforePart(List<BakedQuad> allQuads, RenderPhase phase) {
        // 确定当前阶段对应的 display key
        String displayKey = phaseToDisplayKey(phase.name());
        if (displayKey == null) {
            matrixPushed = false;
            return;
        }

        // 从第一个 quad 获取 modelDisplay（所有 quad 共享同一个模型）
        ModelJson.DisplayTransform dt = null;
        if (allQuads != null && !allQuads.isEmpty() && allQuads.get(0).modelDisplay != null) {
            dt = allQuads.get(0).modelDisplay.get(displayKey);
        }

        // ====== 状态驱动诊断：仅 phase/key/dt 签名变更时触发 ======
        String dtSig = dt != null ? String.format("rot=%s t=%s s=%s",
                dt.rotation != null ? java.util.Arrays.toString(dt.rotation) : "null",
                dt.translation != null ? java.util.Arrays.toString(dt.translation) : "null",
                dt.scale != null ? java.util.Arrays.toString(dt.scale) : "null") : "NULL";
        boolean phaseChanged = (phase != lastDisplayPhase);
        boolean keyChanged = (displayKey != null && !displayKey.equals(lastDisplayKey));
        boolean dtChanged = !dtSig.equals(lastDtSig);
        boolean shouldLog = (phaseChanged || keyChanged || dtChanged);
        if (shouldLog) {
            lastDisplayPhase = phase;
            lastDisplayKey = displayKey;
            lastDtSig = dtSig;
            LOGGER.info(String.format("[DFXDBG] DisplayExt phase=%s key=%s dt=%s",
                    phase.name(), displayKey, dtSig));
        }
        // ====== 诊断结束 ======

        if (dt == null) {
            if (shouldLog) LOGGER.info("[DFXDBG] DisplayExt SKIP: dt=null");
            matrixPushed = false;
            return;
        }

        // 应用 display transform：pushMatrix + transform
        matrixPushed = true;
        GL11.glPushMatrix();
        applyTransform(dt);

        // ====== 诊断：display transform 之后的矩阵（仅签名变更时） ======
        if (shouldLog) {
            FloatBuffer afterBuf = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterBuf);
            float a0 = afterBuf.get(0), a1 = afterBuf.get(1), a2 = afterBuf.get(2), a3 = afterBuf.get(3);
            float a4 = afterBuf.get(4), a5 = afterBuf.get(5), a6 = afterBuf.get(6), a7 = afterBuf.get(7);
            float a8 = afterBuf.get(8), a9 = afterBuf.get(9), a10 = afterBuf.get(10), a11 = afterBuf.get(11);
            float a12 = afterBuf.get(12), a13 = afterBuf.get(13), a14 = afterBuf.get(14), a15 = afterBuf.get(15);
            LOGGER.info(String.format("[DFXDBG] DisplayExt AFTER xform | key=%s", displayKey));
            LOGGER.info(String.format("  M = [%+.4f %+.4f %+.4f %+.4f]", a0, a4, a8, a12));
            LOGGER.info(String.format("      [%+.4f %+.4f %+.4f %+.4f]", a1, a5, a9, a13));
            LOGGER.info(String.format("      [%+.4f %+.4f %+.4f %+.4f]", a2, a6, a10, a14));
            LOGGER.info(String.format("      [%+.4f %+.4f %+.4f %+.4f]", a3, a7, a11, a15));
        }
        // ====== 诊断结束 ======
    }

    @Override
    public void apply(RenderContext ctx) {
        // Display transform 已在 beforePart 中应用，这里无需操作
        // 如果需要针对特定 quad 调整 transform，可以在这里修改 ctx
    }

    @Override
    public void afterPart() {
        if (matrixPushed) {
            GL11.glPopMatrix();
            matrixPushed = false;
        }
    }

    /**
     * 应用 JSON model 的 display transform，对齐 26.1 ItemTransform.apply() 语义。
     *
     * <p>高版本使用 {@code Quaternionf.rotationXYZ(rx, ry, rz)} 产生四元数
     * {@code q = qX * qY * qZ}，对应矩阵 {@code R = RX × RY × RZ}。
     * 顶点变换顺序为 RZ → RY → RX（RZ 最先作用于顶点）。
     *
     * <p>OpenGL 后乘（{@code glRotatef} 执行 {@code M = M × R}），
     * 因此代码中先写 RX 再写 RY 最后 RZ：
     * <ol>
     *   <li>{@code translate(tx, ty, tz)} — display 位移（最后作用于顶点）</li>
     *   <li>{@code rotate(rx, 1,0,0)} — X 轴旋转</li>
     *   <li>{@code rotate(ry, 0,1,0)} — Y 轴旋转</li>
     *   <li>{@code rotate(rz, 0,0,1)} — Z 轴旋转（最先作用于顶点）</li>
     *   <li>{@code scale(sx, sy, sz)} — display 缩放</li>
     *   <li>{@code translate(-0.5, -0.5, -0.5)} — 模型中心移到原点（最先作用于顶点）</li>
     * </ol>
     *
     * <p>等价顶点变换：{@code v' = translate(display) × RX × RY × RZ × scale × translate(-0.5) × v}</p>
     *
     * <p>注意：{@code S(16,-16,16)} 的 Y 翻转在高版本和低版本中均存在，
     * 因此旋转角度直接使用 block.json 原始值，无需取反。</p>
     *
     * @param dt display transform 数据（rotation/translation/scale 单位对齐高版本）
     */
    private static void applyTransform(ModelJson.DisplayTransform dt) {
        if (dt == null) return;

        // ④ translate(display) — 像素 × 0.0625 = 方块单位，对齐高版本 ItemTransform
        if (dt.translation != null && dt.translation.length >= 3) {
            GL11.glTranslatef(
                    dt.translation[0] * 0.0625f,
                    dt.translation[1] * 0.0625f,
                    dt.translation[2] * 0.0625f);
        }

        // ③ rotate XYZ — 对齐 26.1 Quaternionf.rotationXYZ(rx, ry, rz)：
        //    四元数 q = qX * qY * qZ，矩阵 R = RX × RY × RZ。
        //    GL 后乘：先写 RX 再写 RY 最后 RZ，
        //    矩阵结果 M = ... × RX × RY × RZ，
        //    顶点变换顺序 RZ → RY → RX。
        //
        //    S(16,-16,16) 的 Y 翻转在高版本和低版本中均存在，
        //    旋转角度直接使用 block.json 原始值，无需取反。
        if (dt.rotation != null && dt.rotation.length >= 3) {
            GL11.glRotatef(dt.rotation[0], 1, 0, 0);
            GL11.glRotatef(dt.rotation[1], 0, 1, 0);
            GL11.glRotatef(dt.rotation[2], 0, 0, 1);
        }

        // ② scale
        if (dt.scale != null && dt.scale.length >= 3) {
            GL11.glScalef(dt.scale[0], dt.scale[1], dt.scale[2]);
        }

        // ① translate(-0.5) — 中心偏移，高版本模型原点在方块角上
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
    }

    /**
     * 将渲染阶段名称映射到 JSON model 的 display 键名。
     *
     * <p>[S2] 当前 1.7.10 无副手系统，因此仅映射 righthand 变体。
     * 当未来模组提供副手上下文时，可扩展以下映射：
     * <ul>
     *   <li>{@code ITEM_HAND_FIRST_PERSON_LEFT} → {@code "firstperson_lefthand"}</li>
     *   <li>{@code ITEM_HAND_THIRD_PERSON_LEFT} → {@code "thirdperson_lefthand"}</li>
     * </ul>
     *
     * @param phaseName 渲染阶段名称（如 "ITEM_GUI", "ITEM_HAND_FIRST_PERSON"）
     * @return display 键名（如 "gui", "firstperson_righthand"），若无对应返回 null
     */
    public static String phaseToDisplayKey(String phaseName) {
        if (phaseName == null) return null;
        switch (phaseName) {
            case "ITEM_GUI":
            case "BLOCK_GUI":
                return "gui";
            case "ITEM_HAND_FIRST_PERSON":
                return "firstperson_righthand";
            case "ITEM_HAND_THIRD_PERSON":
                return "thirdperson_righthand";
            case "ITEM_HAND":
                return "firstperson_righthand";
            case "DROPPED_ITEM_GROUND":
            case "DROPPED_BLOCK_GROUND":
                return "ground";
            default:
                return null;
        }
    }
}
