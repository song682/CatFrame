package decok.dfcdvadstf.catframe.model.render.extension;

import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
import java.util.List;

/**
 * 内建渲染扩展：处理 JSON model 中的 {@code display} transforms，
 * 语义对齐 26.1 {@code ItemTransform.apply()}。
 *
 * <h3>变换顺序（顶点空间，向量模式）</h3>
 * <ol>
 *   <li>{@code translate(-0.5, -0.5, -0.5)} — 模型原点从方块角移到中心</li>
 *   <li>{@code scale(sx, sy, sz)} — display 缩放</li>
 *   <li>{@code rotate(rx) → rotate(ry) → rotate(rz)} — X→Y→Z 顺序旋转
 *       （对齐 26.1 Quaternionf.rotationXYZ 语义）</li>
 *   <li>{@code translate(tx, ty, tz)} — display 位移（像素 × 0.0625 = 方块单位）</li>
 * </ol>
 *
 * <h3>字段默认值与范围钳制（对齐高版本 display transform 规范）</h3>
 * <table border="1">
 *   <caption>display transform 字段规范</caption>
 *   <tr><th>字段</th><th>默认值</th><th>钳制范围</th></tr>
 *   <tr><td>rotation</td><td>[0, 0, 0]</td><td>无限制（角度制）</td></tr>
 *   <tr><td>translation</td><td>[0, 0, 0]</td><td>[-80, 80]（像素单位，1/16 方块）</td></tr>
 *   <tr><td>scale</td><td>[1, 1, 1]</td><td>[-4, 4]</td></tr>
 * </table>
 *
 * <p>本实现采用向量空间建模：将 display transform 计算为 {@link Matrix4d} 矩阵，
 * 通过 {@link RenderContext#displayTransform} 传递给管线，由管线在提交顶点前
 * 统一执行矩阵乘法变换顶点坐标。不再使用 GL 矩阵栈操作。</p>
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>{@code beforePart} — 根据 phase 和 part.getDisplay() 计算变换矩阵</li>
 *   <li>{@code apply} — 将矩阵写入 ctx.displayTransform，管线在顶点提交时消费</li>
 *   <li>{@code afterPart} — 清除矩阵引用</li>
 * </ul>
 *
 * <p>本扩展作为内建扩展注册到 {@link decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry}
 * 扩展链中，由管线统一调度。</p>
 */
public final class DisplayTransformExtension implements IModelRenderExtension {

    /** 当前 part 的 display transform 矩阵，由 beforePart 设置、afterPart 清空 */
    private Matrix4d currentMatrix = null;

    // ====== 诊断日志：状态驱动（按 phase/key/dt 签名变更触发，不逐帧打印） ======
    private static final Logger LOGGER = LogManager.getLogger(DisplayTransformExtension.class);
    private static RenderPhase lastDisplayPhase = null;
    private static String lastDisplayKey = null;
    private static String lastDtSig = null;

    @Override
    public void beforePart(List<BakedQuad> allQuads, RenderPhase phase, BlockStateModelPart part) {
        // 确定当前阶段对应的 display key
        String displayKey = phase.getDisplayKey();
        if (displayKey == null) {
            currentMatrix = null;
            return;
        }

        // 从 part 级别获取 display transforms（不再从 BakedQuad.modelDisplay 读取）
        ModelJson.DisplayTransform dt = null;
        if (part != null && part.getDisplay() != null) {
            dt = part.getDisplay().get(displayKey);
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

        // 当 dt == null 时，使用基于 display key 的默认变换
        if (dt == null) {
            dt = getDefaultTransform(displayKey);
            if (shouldLog) LOGGER.info(String.format("[DFXDBG] DisplayExt DEFAULT: key=%s dt=rot=%s t=%s s=%s",
                    displayKey,
                    java.util.Arrays.toString(dt.rotation),
                    java.util.Arrays.toString(dt.translation),
                    java.util.Arrays.toString(dt.scale)));
        }

        // 计算 display transform 矩阵（向量空间）
        currentMatrix = computeMatrix(dt);
    }

    @Override
    public void apply(RenderContext ctx) {
        ctx.displayTransform = currentMatrix;
    }

    @Override
    public void afterPart() {
        currentMatrix = null;
    }

    /**
     * translation 各分量钳制范围（像素单位，1/16 方块）。
     * 对齐高版本 ItemTransform.Deserializer 规范。
     */
    private static final float TRANSLATION_CLAMP = 80.0f;

    /** scale 各分量钳制范围，对齐高版本 ItemTransform.Deserializer 规范。 */
    private static final float SCALE_CLAMP = 4.0f;

    /**
     * 从 display transform 数据计算 4×4 变换矩阵。
     *
     * <p>顶点变换顺序（作用于顶点 v）：
     * {@code v' = T(display) × RX × RY × RZ × S(display) × T(-0.5) × v}
     *
     * <p>对齐 26.1 ItemTransform.apply() 语义，
     * 与旧版 GL 即时模式的矩阵计算结果一致。
     *
     * <p>在读取字段时同步执行高版本规范约束：
     * <ul>
     *   <li>null / 长度不足 → 对应默认值顶替（rotation=[0,0,0], translation=[0,0,0], scale=[1,1,1]）</li>
     *   <li>translation 各分量钳制到 [-80, 80]</li>
     *   <li>scale 各分量钳制到 [-4, 4]</li>
     *   <li>rotation 无范围限制（角度制自由范围）</li>
     * </ul>
     *
     * @param dt display transform 数据
     * @return 4×4 变换矩阵
     */
    private static Matrix4d computeMatrix(ModelJson.DisplayTransform dt) {
        if (dt == null) return null;

        // ===== 读取字段值，同步执行默认值顶替 + 范围钳制 =====
        // 默认值：rotation=[0,0,0], translation=[0,0,0], scale=[1,1,1]
        // 钳制：translation ∈ [-80,80], scale ∈ [-4,4], rotation 无限制
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

        // ④ translate(display) — 像素 × 0.0625 = 方块单位
        Matrix4d tDisplay = new Matrix4d();
        tDisplay.setIdentity();
        tDisplay.setTranslation(new Vector3d(tx * 0.0625, ty * 0.0625, tz * 0.0625));
        m.mul(tDisplay);

        // ③ rotate XYZ — 对齐 26.1 Quaternionf.rotationXYZ
        Matrix4d r = new Matrix4d();
        r.rotX(Math.toRadians(rx));
        m.mul(r);
        r.rotY(Math.toRadians(ry));
        m.mul(r);
        r.rotZ(Math.toRadians(rz));
        m.mul(r);

        // ② scale
        Matrix4d s = new Matrix4d();
        s.setIdentity();
        s.m00 = sx; s.m11 = sy; s.m22 = sz;
        m.mul(s);

        // ① translate(-0.5) — 中心偏移，高版本模型原点在方块角上
        Matrix4d tCenter = new Matrix4d();
        tCenter.setIdentity();
        tCenter.setTranslation(new Vector3d(-0.5, -0.5, -0.5));
        m.mul(tCenter);

        return m;
    }

    /**
     * 将值钳制到 [min, max] 范围。
     */
    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    /**
     * 返回指定 display key 的默认变换，对齐 26.1.2 ItemTransform Deserializer 默认值。
     *
     * <p>当模型 JSON 缺少某个 display key 的数据时，本方法提供合理的默认值。
     *
     * @param displayKey display 键名
     * @return 默认的 DisplayTransform 实例
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
                dt.translation = new float[]{0, 0, 0};
                dt.scale = new float[]{0.68f, 0.68f, 0.68f};
                break;
            case "thirdperson_righthand":
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
                dt.scale = new float[]{0.5f, 0.5f, 0.5f};
                break;
            default:
                dt.translation = new float[]{0, 0, 0};
                break;
        }
        return dt;
    }
}

