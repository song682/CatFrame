package decok.dfcdvadstf.catframe.model.state.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;

/**
 * 物品模型渲染变换（{@code transformation}）解析器。
 * <p>
 * Parser for the optional {@code transformation} tag inside a
 * {@code minecraft:model} node of an ItemState definition.
 * <p>
 * 对应 items/*.json 中 {@code "type": "minecraft:model"} 节点的可选
 * {@code transformation} 标签（默认为单位变换）。该变换以物品自身位置为原点，
 * 永远在烘焙模型 display 指定的渲染变换（如主手/副手）<b>之后</b>应用。
 * <p>
 * 支持两种 JSON 形态（与 wiki 规范一致，见 markdown/transform-Itemstate.md）：
 * <ul>
 *   <li><b>数组</b>：16 个 float 组成的行主序（Row-major）矩阵。
 *       其中第 13、14、15 个值（底行前三元）对变换没有任何效果；
 *       第 16 个值会缩放前 12 个值（前 12 个数字除以此数字）。</li>
 *   <li><b>对象</b>：分解形式，按顺序依次应用
 *       {@code right_rotation} → {@code scale} → {@code left_rotation} → {@code translation}。
 *       旋转标签可为四元数数组（4 个 float，[x, y, z, w]，非单位四元数还会使模型缩放）
 *       或轴-角度对象（{@code angle} 为弧度制 float，{@code axis} 为 3 个 float 的旋转轴）。</li>
 * </ul>
 * <p>
 * 解析结果为 {@link Matrix4d}（向量空间纯软件变换，不触碰 GL 状态）；
 * 若变换等价于单位矩阵则返回 {@code null}，供渲染管线跳过逐顶点乘法。
 */
public final class ItemTransformation {

    /** 单位矩阵判定容差 / epsilon for identity detection */
    private static final double IDENTITY_EPSILON = 1.0e-6;

    private ItemTransformation() {
    }

    /**
     * 解析 {@code transformation} JSON 元素为变换矩阵。
     * <p>
     * Parses the {@code transformation} JSON element into a transform matrix.
     *
     * @param elem JSON 元素（数组或对象），可为 null
     * @return 变换矩阵；若元素为 null 或变换为单位变换则返回 {@code null}
     * @throws JsonParseException 若 JSON 结构非法（如数组长度不为 16）
     */
    @Nullable
    public static Matrix4d parse(@Nullable JsonElement elem) {
        if (elem == null || elem.isJsonNull()) return null;

        Matrix4d m;
        if (elem.isJsonArray()) {
            m = parseMatrix(elem.getAsJsonArray());
        } else if (elem.isJsonObject()) {
            m = parseDecomposed(elem.getAsJsonObject());
        } else {
            throw new JsonParseException(
                    "transformation must be a 16-float array or a decomposed object");
        }

        // 单位变换退化为 null，渲染期零开销
        // Identity collapses to null so the pipeline skips per-vertex multiplication
        return isIdentity(m) ? null : m;
    }

    // ==================== 数组形式（行主序矩阵） ====================

    /**
     * 解析 16 个 float 的行主序矩阵。
     * <p>
     * 规则：底行前三元（索引 12/13/14）无效果强制为 0；
     * 第 16 个值（索引 15）作为除数缩放前 12 个值，之后 m33 归一为 1。
     */
    private static Matrix4d parseMatrix(JsonArray arr) {
        if (arr.size() != 16) {
            throw new JsonParseException(
                    "transformation matrix must contain exactly 16 floats, got " + arr.size());
        }
        double[] v = new double[16];
        for (int i = 0; i < 16; i++) {
            v[i] = arr.get(i).getAsDouble();
        }

        // 第 16 个值缩放前 12 个值（除法）；为 0 时跳过以避免除零
        // The 16th value divides the first 12; skip when zero to avoid division by zero
        double divisor = v[15];
        if (divisor != 0.0 && divisor != 1.0) {
            for (int i = 0; i < 12; i++) {
                v[i] /= divisor;
            }
        }

        Matrix4d m = new Matrix4d();
        // 行主序：v[0..3] 为第一行，与 Matrix4d(m00..m33) 参数序一致
        m.m00 = v[0];  m.m01 = v[1];  m.m02 = v[2];  m.m03 = v[3];
        m.m10 = v[4];  m.m11 = v[5];  m.m12 = v[6];  m.m13 = v[7];
        m.m20 = v[8];  m.m21 = v[9];  m.m22 = v[10]; m.m23 = v[11];
        // 第 13/14/15 个值对变换无效果，第 16 个值已消耗为缩放除数
        m.m30 = 0.0;   m.m31 = 0.0;   m.m32 = 0.0;   m.m33 = 1.0;
        return m;
    }

    // ==================== 对象形式（分解变换） ====================

    /**
     * 解析分解形式：按 wiki 规范顺序依次应用
     * {@code right_rotation} → {@code scale} → {@code left_rotation} → {@code translation}。
     * <p>
     * 顶点变换语义 v' = T × L × S × R × v（right_rotation 最先作用于顶点）。
     * 缺失的标签按单位分量顶替（rotation=单位、scale=[1,1,1]、translation=[0,0,0]），
     * 与 {@code DisplayTransformExtension} 的默认值顶替策略一致。
     */
    private static Matrix4d parseDecomposed(JsonObject obj) {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // ④ translation — 最后作用于顶点，故最先左乘
        float[] t = readFloatArray(obj.get("translation"), 3, "translation");
        if (t != null) {
            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(t[0], t[1], t[2]));
            m.mul(tmp);
        }

        // ③ left_rotation
        Matrix4d left = parseRotation(obj.get("left_rotation"), "left_rotation");
        if (left != null) {
            m.mul(left);
        }

        // ② scale
        float[] s = readFloatArray(obj.get("scale"), 3, "scale");
        if (s != null) {
            tmp.setIdentity();
            tmp.m00 = s[0]; tmp.m11 = s[1]; tmp.m22 = s[2];
            m.mul(tmp);
        }

        // ① right_rotation — 最先作用于顶点，故最后右乘
        Matrix4d right = parseRotation(obj.get("right_rotation"), "right_rotation");
        if (right != null) {
            m.mul(right);
        }

        return m;
    }

    /**
     * 解析旋转标签（四元数数组或轴-角度对象）。
     * <p>
     * Parses a rotation tag: quaternion array {@code [x, y, z, w]} or an
     * axis-angle object {@code {"angle": <radians>, "axis": [x, y, z]}}.
     * <p>
     * 四元数不做归一化 —— 非单位四元数会按 |q|² 均匀缩放模型（与 wiki 语义一致）。
     * 轴-角度形式内部转为单位四元数（axis 归一化，angle 为弧度）。
     *
     * @return 旋转矩阵；标签缺失时返回 {@code null}（视为单位旋转）
     */
    @Nullable
    private static Matrix4d parseRotation(@Nullable JsonElement elem, String tagName) {
        if (elem == null || elem.isJsonNull()) return null;

        if (elem.isJsonArray()) {
            // 四元数形式：[x, y, z, w]
            float[] q = readFloatArray(elem, 4, tagName);
            if (q == null) return null;
            return quaternionToMatrix(q[0], q[1], q[2], q[3]);
        }

        if (elem.isJsonObject()) {
            // 轴-角度形式：{"angle": <弧度>, "axis": [x, y, z]}
            JsonObject rot = elem.getAsJsonObject();
            if (!rot.has("angle") || !rot.has("axis")) {
                throw new JsonParseException(
                        tagName + " axis-angle form must contain both 'angle' and 'axis'");
            }
            double angle = rot.get("angle").getAsDouble();
            float[] axis = readFloatArray(rot.get("axis"), 3, tagName + ".axis");
            if (axis == null) return null;

            // 轴归一化；零向量轴退化为单位旋转
            // Normalize the axis; a zero-length axis degrades to identity
            double len = Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
            if (len < IDENTITY_EPSILON) return null;

            double half = angle * 0.5;
            double sin = Math.sin(half) / len;
            return quaternionToMatrix(
                    axis[0] * sin, axis[1] * sin, axis[2] * sin, Math.cos(half));
        }

        throw new JsonParseException(
                tagName + " must be a 4-float quaternion array or an axis-angle object");
    }

    /**
     * 四元数 → 旋转矩阵（不归一化）。
     * <p>
     * Raw quaternion to rotation matrix without normalization: a non-unit
     * quaternion uniformly scales the model by |q|², matching the wiki semantics.
     */
    private static Matrix4d quaternionToMatrix(double x, double y, double z, double w) {
        Matrix4d m = new Matrix4d();
        m.m00 = w * w + x * x - y * y - z * z;
        m.m01 = 2.0 * (x * y - w * z);
        m.m02 = 2.0 * (x * z + w * y);
        m.m10 = 2.0 * (x * y + w * z);
        m.m11 = w * w - x * x + y * y - z * z;
        m.m12 = 2.0 * (y * z - w * x);
        m.m20 = 2.0 * (x * z - w * y);
        m.m21 = 2.0 * (y * z + w * x);
        m.m22 = w * w - x * x - y * y + z * z;
        m.m33 = 1.0;
        return m;
    }

    // ==================== 工具方法 ====================

    /**
     * 读取定长 float 数组标签。
     *
     * @return float 数组；标签缺失时返回 {@code null}
     * @throws JsonParseException 若元素非数组或长度不符
     */
    @Nullable
    private static float[] readFloatArray(@Nullable JsonElement elem, int expected, String tagName) {
        if (elem == null || elem.isJsonNull()) return null;
        if (!elem.isJsonArray()) {
            throw new JsonParseException(tagName + " must be an array of " + expected + " floats");
        }
        JsonArray arr = elem.getAsJsonArray();
        if (arr.size() != expected) {
            throw new JsonParseException(
                    tagName + " must contain exactly " + expected + " floats, got " + arr.size());
        }
        float[] out = new float[expected];
        for (int i = 0; i < expected; i++) {
            out[i] = arr.get(i).getAsFloat();
        }
        return out;
    }

    /**
     * 判定矩阵是否在容差内等于单位矩阵。
     */
    private static boolean isIdentity(Matrix4d m) {
        return Math.abs(m.m00 - 1.0) < IDENTITY_EPSILON
                && Math.abs(m.m11 - 1.0) < IDENTITY_EPSILON
                && Math.abs(m.m22 - 1.0) < IDENTITY_EPSILON
                && Math.abs(m.m33 - 1.0) < IDENTITY_EPSILON
                && Math.abs(m.m01) < IDENTITY_EPSILON && Math.abs(m.m02) < IDENTITY_EPSILON
                && Math.abs(m.m03) < IDENTITY_EPSILON && Math.abs(m.m10) < IDENTITY_EPSILON
                && Math.abs(m.m12) < IDENTITY_EPSILON && Math.abs(m.m13) < IDENTITY_EPSILON
                && Math.abs(m.m20) < IDENTITY_EPSILON && Math.abs(m.m21) < IDENTITY_EPSILON
                && Math.abs(m.m23) < IDENTITY_EPSILON && Math.abs(m.m30) < IDENTITY_EPSILON
                && Math.abs(m.m31) < IDENTITY_EPSILON && Math.abs(m.m32) < IDENTITY_EPSILON;
    }
}
