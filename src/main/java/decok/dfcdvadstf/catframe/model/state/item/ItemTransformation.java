package decok.dfcdvadstf.catframe.model.state.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;

/**
 * Parser for the item model render transform ({@code transformation}).
 * <p>
 * Parser for the optional {@code transformation} tag inside a
 * {@code minecraft:model} node of an ItemState definition.
 * <p>
 * Corresponds to the optional {@code transformation} tag of a
 * {@code "type": "minecraft:model"} node in items/*.json (identity transform by default).
 * The transform uses the item's own position as the origin and is always applied
 * <b>after</b> the render transform specified by the baked model's display (e.g. main hand /
 * off hand).
 * <p>
 * Two JSON forms are supported (consistent with the wiki spec, see
 * markdown/transform-Itemstate.md):
 * <ul>
 *   <li><b>Array</b>: 16 floats forming a row-major matrix.
 *       The 13th, 14th, and 15th values (the first three elements of the bottom row) have no
 *       effect on the transform; the 16th value scales the first 12 values (the first 12
 *       numbers are divided by this number).</li>
 *   <li><b>Object</b>: decomposed form, applied in order
 *       {@code right_rotation} → {@code scale} → {@code left_rotation} → {@code translation}.
 *       A rotation tag may be a quaternion array (4 floats, [x, y, z, w]; a non-unit
 *       quaternion also scales the model) or an axis-angle object ({@code angle} is a float in
 *       radians, {@code axis} is a 3-float rotation axis).</li>
 * </ul>
 * <p>
 * The parse result is a {@link Matrix4d} (vector-space, pure software transform, no GL state
 * touched); if the transform is equivalent to the identity matrix it returns {@code null},
 * letting the render pipeline skip the per-vertex multiplication.
 */
public final class ItemTransformation {

    /** epsilon for identity detection */
    private static final double IDENTITY_EPSILON = 1.0e-6;

    private ItemTransformation() {
    }

    /**
     * Parses the {@code transformation} JSON element into a transform matrix.
     *
     * @param elem JSON element (array or object), may be null
     * @return the transform matrix; {@code null} if the element is null or the transform is the identity
     * @throws JsonParseException if the JSON structure is invalid (e.g. array length not 16)
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

        // Identity collapses to null so the pipeline skips per-vertex multiplication
        return isIdentity(m) ? null : m;
    }

    // ==================== Array form (row-major matrix) ====================

    /**
     * Parses a row-major matrix of 16 floats.
     * <p>
     * Rules: the first three elements of the bottom row (indices 12/13/14) have no effect and
     * are forced to 0; the 16th value (index 15) acts as a divisor scaling the first 12
     * values, after which m33 is normalized to 1.
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

        // The 16th value divides the first 12; skip when zero to avoid division by zero
        double divisor = v[15];
        if (divisor != 0.0 && divisor != 1.0) {
            for (int i = 0; i < 12; i++) {
                v[i] /= divisor;
            }
        }

        Matrix4d m = new Matrix4d();
        // Row-major: v[0..3] is the first row, matching the Matrix4d(m00..m33) parameter order
        m.m00 = v[0];  m.m01 = v[1];  m.m02 = v[2];  m.m03 = v[3];
        m.m10 = v[4];  m.m11 = v[5];  m.m12 = v[6];  m.m13 = v[7];
        m.m20 = v[8];  m.m21 = v[9];  m.m22 = v[10]; m.m23 = v[11];
        // The 13th/14th/15th values have no effect on the transform, and the 16th value has already been consumed as the scale divisor
        m.m30 = 0.0;   m.m31 = 0.0;   m.m32 = 0.0;   m.m33 = 1.0;
        return m;
    }

    // ==================== Object form (decomposed transform) ====================

    /**
     * Parses the decomposed form: applied in wiki-spec order
     * {@code right_rotation} → {@code scale} → {@code left_rotation} → {@code translation}.
     * <p>
     * Vertex transform semantics: v' = T × L × S × R × v (right_rotation acts on the vertex
     * first). Missing tags are substituted with identity components (rotation=identity,
     * scale=[1,1,1], translation=[0,0,0]), consistent with the default-substitution strategy
     * of {@code DisplayTransformExtension}.
     */
    private static Matrix4d parseDecomposed(JsonObject obj) {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // ④ translation — acts on the vertex last, so it is left-multiplied first
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

        // ① right_rotation — acts on the vertex first, so it is right-multiplied last
        Matrix4d right = parseRotation(obj.get("right_rotation"), "right_rotation");
        if (right != null) {
            m.mul(right);
        }

        return m;
    }

    /**
     * Parses a rotation tag (quaternion array or axis-angle object).
     * <p>
     * Parses a rotation tag: quaternion array {@code [x, y, z, w]} or an
     * axis-angle object {@code {"angle": <radians>, "axis": [x, y, z]}}.
     * <p>
     * The quaternion is not normalized—a non-unit quaternion uniformly scales the model by
     * |q|² (consistent with the wiki semantics). The axis-angle form is converted internally
     * to a unit quaternion (axis normalized, angle in radians).
     *
     * @return the rotation matrix; {@code null} when the tag is missing (treated as identity rotation)
     */
    @Nullable
    private static Matrix4d parseRotation(@Nullable JsonElement elem, String tagName) {
        if (elem == null || elem.isJsonNull()) return null;

        if (elem.isJsonArray()) {
            // Quaternion form: [x, y, z, w]
            float[] q = readFloatArray(elem, 4, tagName);
            if (q == null) return null;
            return quaternionToMatrix(q[0], q[1], q[2], q[3]);
        }

        if (elem.isJsonObject()) {
            // Axis-angle form: {"angle": <radians>, "axis": [x, y, z]}
            JsonObject rot = elem.getAsJsonObject();
            if (!rot.has("angle") || !rot.has("axis")) {
                throw new JsonParseException(
                        tagName + " axis-angle form must contain both 'angle' and 'axis'");
            }
            double angle = rot.get("angle").getAsDouble();
            float[] axis = readFloatArray(rot.get("axis"), 3, tagName + ".axis");
            if (axis == null) return null;

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
     * Quaternion → rotation matrix (no normalization).
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

    // ==================== Utility methods ====================

    /**
     * Reads a fixed-length float array tag.
     *
     * @return the float array; {@code null} when the tag is missing
     * @throws JsonParseException if the element is not an array or the length does not match
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
     * Determines whether the matrix equals the identity matrix within tolerance.
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
