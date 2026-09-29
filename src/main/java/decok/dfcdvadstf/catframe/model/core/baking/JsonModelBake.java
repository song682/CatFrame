package decok.dfcdvadstf.catframe.model.core.baking;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import net.minecraft.util.IIcon;

import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonModelBake {
    public static List<BakedQuad> bakeElement(ModelJson.Element e, Map<String, IIcon> iconMap) {
        return bakeElement(e, iconMap, null);
    }

    public static List<BakedQuad> bakeElement(ModelJson.Element e, Map<String, IIcon> iconMap, int[] textureSize) {
        List<BakedQuad> out = new ArrayList<>();
        // [C6] Null check: from/to are required JSON fields; a corrupted model may have them null
        if (e.from == null || e.to == null) {
            CatFrame.logger.warn("[BlockJsonModelBake] bakeElement: element has null from/to, skipping");
            return out;
        }
        // [C7] Range validation: every component must lie within [-16, 32] pixels (aligned with 26.1 CuboidModelElement)
        if (!isInBounds(e.from) || !isInBounds(e.to)) {
            CatFrame.logger.warn("[BlockJsonModelBake] bakeElement: from/to out of bounds [-16,32], from={} to={}, skipping",
                    java.util.Arrays.toString(e.from), java.util.Arrays.toString(e.to));
            return out;
        }
        // Keep the original from/to (pixel coordinates): UV computation relies on the original, unrotated bounds
        final float[] elemFrom = e.from;
        final float[] elemTo = e.to;
        // Read the element-level ambientocclusion and shade settings
        final Boolean elemAO = e.ambientocclusion;
        final Boolean elemShade = e.shade;
        // 26.3+: element-level shade direction override; takes precedence over "shade" when both are present
        final Direction elemShadeDir = parseShadeDirectionOverride(e.shadeDirectionOverride);
        double x0 = e.from[0] / 16.0, y0 = e.from[1] / 16.0, z0 = e.from[2] / 16.0;
        double x1 = e.to[0] / 16.0, y1 = e.to[1] / 16.0, z1 = e.to[2] / 16.0;
        Vector3d[] C = new Vector3d[8];
        C[idx(0, 0, 0)] = new Vector3d(x0, y0, z0);
        C[idx(1, 0, 0)] = new Vector3d(x1, y0, z0);
        C[idx(0, 1, 0)] = new Vector3d(x0, y1, z0);
        C[idx(1, 1, 0)] = new Vector3d(x1, y1, z0);
        C[idx(0, 0, 1)] = new Vector3d(x0, y0, z1);
        C[idx(1, 0, 1)] = new Vector3d(x1, y0, z1);
        C[idx(0, 1, 1)] = new Vector3d(x0, y1, z1);
        C[idx(1, 1, 1)] = new Vector3d(x1, y1, z1);
        // Rotation parsing: supports both the single-axis (angle/axis) and multi-axis (x/y/z) formats.
        // [FIX] Read-only into locals; never mutate the shared e.rotation: the ModelResolver cache makes the same
        //     Element reused concurrently by multiple baking threads, so in-place rewriting would race.
        boolean isMultiAxis = false;
        float rotAngle = 0f;
        char rotAxis = 0;
        float rotX = 0f, rotY = 0f, rotZ = 0f;
        boolean rotRescale = false;
        float[] rotOrigin = null;
        if (e.rotation != null) {
            rotRescale = e.rotation.rescale;
            rotOrigin = e.rotation.origin;
            if (e.rotation.angle != 0f || e.rotation.axis != null) {
                // Single-axis format: {"angle": <deg>, "axis": "x"|"y"|"z"}
                rotAngle = e.rotation.angle;
                if (e.rotation.axis != null && !e.rotation.axis.isEmpty()) {
                    rotAxis = Character.toLowerCase(e.rotation.axis.charAt(0));
                }
            } else if (e.rotation.x != 0f || e.rotation.y != 0f || e.rotation.z != 0f) {
                // Multi-axis format: {"x": <deg>, "y": <deg>, "z": <deg>}
                isMultiAxis = true;
                rotX = e.rotation.x;
                rotY = e.rotation.y;
                rotZ = e.rotation.z;
            }
        }

        // [C6] origin null fallback (Blockbench exports may omit origin)
        float[] o = rotOrigin;
        if (o == null) o = new float[]{8f, 8f, 8f};
        boolean originIsZero = (o.length >= 3 && o[0] == 0f && o[1] == 0f && o[2] == 0f);
        final float oxPx = originIsZero ? 8f : o[0];
        final float oyPx = originIsZero ? ((e.from[1] + e.to[1]) * 0.5f) : o[1];
        final float ozPx = originIsZero ? 8f : o[2];
        final double ox = oxPx / 16.0, oy = oyPx / 16.0, oz = ozPx / 16.0;

        boolean hasRotation = (!isMultiAxis && rotAngle != 0 && rotAxis != 0)
                           || (isMultiAxis && (rotX != 0 || rotY != 0 || rotZ != 0));

        if (hasRotation) {
            Matrix4d rot = new Matrix4d();
            rot.setIdentity();

            if (isMultiAxis) {
                // Multi-axis rotation: applied in X→Y→Z order (aligned with 26.1 Quaternionf.rotationXYZ).
                // Arbitrary angles are supported (not limited to multiples of 22.5°), and multiple axes may be non-zero at once.
                // Left-multiplication order: rot = Rz * Ry * Rx, so vertices are affected by Rx first → Ry → Rz.
                if (rotX != 0) {
                    Matrix4d rx = new Matrix4d();
                    rx.setIdentity();
                    rx.rotX(Math.toRadians(rotX));
                    rot.mul(rx, rot);
                }
                if (rotY != 0) {
                    Matrix4d ry = new Matrix4d();
                    ry.setIdentity();
                    ry.rotY(Math.toRadians(rotY));
                    rot.mul(ry, rot);
                }
                if (rotZ != 0) {
                    Matrix4d rz = new Matrix4d();
                    rz.setIdentity();
                    rz.rotZ(Math.toRadians(rotZ));
                    rot.mul(rz, rot);
                }
            } else {
                // Single-axis rotation: the angle/axis format
                double angRad = Math.toRadians(rotAngle);
                switch (rotAxis) {
                    case 'x': rot.rotX(angRad); break;
                    case 'y': rot.rotY(angRad); break;
                    case 'z': rot.rotZ(angRad); break;
                    default: break;
                }
            }

            // [W4] rescale: apply non-uniform scaling along each local axis before rotation so the largest
            // projected component returns to its original size after rotation, compensating the visual shrink
            // caused by rotation. Aligned with 26.1 CuboidRotation.computeRescale semantics: rotation × scale.
            if (rotRescale) {
                double[] rescaleS = computeRescaleFactors(rot);
                for (int i = 0; i < 8; i++) {
                    C[i].x = (C[i].x - ox) * rescaleS[0] + ox;
                    C[i].y = (C[i].y - oy) * rescaleS[1] + oy;
                    C[i].z = (C[i].z - oz) * rescaleS[2] + oz;
                }
            }

            // Apply the rotation: sub(origin) → rotate → add(origin)
            Vector3d origin = new Vector3d(ox, oy, oz);
            for (int i = 0; i < 8; i++) {
                C[i].sub(origin);
                rot.transform(C[i]);
                C[i].add(origin);
            }
        }
        emitFaceFromCorners(out, e.faces.north, iconMap, C, new int[]{idx(1, 1, 0), idx(1, 0, 0), idx(0, 0, 0), idx(0, 1, 0)}, Direction.NORTH, elemFrom, elemTo, elemAO, elemShade, elemShadeDir, textureSize);
        emitFaceFromCorners(out, e.faces.south, iconMap, C, new int[]{idx(0, 1, 1), idx(0, 0, 1), idx(1, 0, 1), idx(1, 1, 1)}, Direction.SOUTH, elemFrom, elemTo, elemAO, elemShade, elemShadeDir, textureSize);
        emitFaceFromCorners(out, e.faces.west, iconMap, C, new int[]{idx(0, 1, 0), idx(0, 0, 0), idx(0, 0, 1), idx(0, 1, 1)}, Direction.WEST, elemFrom, elemTo, elemAO, elemShade, elemShadeDir, textureSize);
        emitFaceFromCorners(out, e.faces.east, iconMap, C, new int[]{idx(1, 1, 1), idx(1, 0, 1), idx(1, 0, 0), idx(1, 1, 0)}, Direction.EAST, elemFrom, elemTo, elemAO, elemShade, elemShadeDir, textureSize);
        emitFaceFromCorners(out, e.faces.down, iconMap, C, new int[]{idx(0, 0, 1), idx(0, 0, 0), idx(1, 0, 0), idx(1, 0, 1)}, Direction.DOWN, elemFrom, elemTo, elemAO, elemShade, elemShadeDir, textureSize);
        emitFaceFromCorners(out, e.faces.up, iconMap, C, new int[]{idx(0, 1, 0), idx(0, 1, 1), idx(1, 1, 1), idx(1, 1, 0)}, Direction.UP, elemFrom, elemTo, elemAO, elemShade, elemShadeDir, textureSize);
        return out;
    }

    private static void emitFaceFromCorners(List<BakedQuad> out, ModelJson.Face f, Map<String, IIcon> iconMap, Vector3d[] C, int[] id, Direction facing,
                                            float[] elemFrom, float[] elemTo, Boolean elemAO, Boolean elemShade, Direction elemShadeDir, int[] textureSize) {
        if (f == null || f.texture == null) {
            return;
        }
        IIcon icon = iconMap.get(f.texture.substring(1));
        if (icon == null) {
            return;
        }
        double[] vx = new double[4], vy = new double[4], vz = new double[4];
        for (int i = 0; i < 4; i++) {
            Vector3d c = C[id[i]];
            vx[i] = c.x;
            vy[i] = c.y;
            vz[i] = c.z;
        }
        float[] up = new float[4], vp = new float[4];
        assignUVForFace(f, facing, id, up, vp, elemFrom, elemTo, textureSize);
        BakedQuad q = new BakedQuad();
        for (int i = 0; i < 4; i++) {
            q.vertices[i] = new Vector3d(vx[i], vy[i], vz[i]);
            q.up[i] = up[i];
            q.vp[i] = vp[i];
        }
        q.icon = icon;
        q.face = facing;
        q.tintIndex = f.tintIndex;
        q.cullface = parseCullface(f.cullface);
        // Use vecmath vector operations instead of the scalar normal() function
        Vector3d a = new Vector3d(q.vertices[1]);
        a.sub(q.vertices[0]);
        Vector3d b = new Vector3d(q.vertices[2]);
        b.sub(q.vertices[0]);
        q.faceNormal = new Vector3d();
        q.faceNormal.cross(a, b);
        q.faceNormal.normalize();
        // Propagate the element-level AO and shade settings
        q.ambientOcclusion = elemAO;
        q.shadeEnabled = elemShade;
        q.shadeDirectionOverride = elemShadeDir;
        out.add(q);
    }

    private static void assignUVForFace(ModelJson.Face f, Direction face, int[] ids, float[] outU, float[] outV,
                                        float[] elemFrom, float[] elemTo, int[] textureSize) {
        // Auto-compute default UV from element from/to (pixel space) if not specified in JSON
        if (f.uv == null) {
            f.uv = computeDefaultUV(elemFrom, elemTo, face);
        }
        final float u0 = f.uv[0], v0 = f.uv[1], u1 = f.uv[2], v1 = f.uv[3];
        final float du = u1 - u0, dv = v1 - v0;
        int steps = (f.rotation == null) ? 0 : ((Integer.parseInt(f.rotation) / 90) & 3);
        if (face == Direction.UP || face == Direction.DOWN) {
            switch (steps) {
                case 1:
                    steps = 3;
                    break;
                case 3:
                    steps = 1;
                    break;
            }
        }
        for (int i = 0; i < 4; i++) {
            int idx = ids[i];
            int ix = idx & 1;
            int iz = (idx >> 1) & 1;
            int iy = (idx >> 2) & 1;
            float s = 0, t = 0;
            switch (face) {

                case SOUTH:
                    s = ix;
                    t = 1 - iy;
                    break;

                case NORTH:
                    s = 1 - ix;
                    t = 1 - iy;
                    break;

                case EAST:
                    s = 1 - iz;
                    t = 1 - iy;
                    break;

                case WEST:
                    s = iz;
                    t = 1 - iy;
                    break;

                case DOWN:
                    s = ix;
                    t = 1 - iz;
                    break;
                case UP:
                    s = ix;
                    t = iz;
                    break;
            }
            float ss = s, tt = t;
            for (int r = 0; r < steps; r++) {
                float ns = 1f - tt;
                float nt = ss;
                ss = ns;
                tt = nt;
            }
            outU[i] = u0 + du * ss;
            outV[i] = v0 + dv * tt;
        }

        // UV values exported by Blockbench are already converted to the 16x16 abstract space based on
        // texture_size: the actual mapping is texture_size * (uv / 16) = the material's real pixel position.
        // IIcon.getInterpolatedU/V() also use the 0-16 range, so no extra scaling is needed.
        // Reference: https://en.wiki.vg/File_Formats#Model
    }

    /**
     * Compute default UV from element bounds in pixel space (0-16).
     * Independent of rotation — matches vanilla Minecraft behavior.
     */
    /**
     * Validates that every component of a float[3] lies within [-16, 32]
     * (aligned with the 26.1 CuboidModelElement bounds check).
     */
    private static boolean isInBounds(float[] v) {
        return v != null && v.length >= 3
            && v[0] >= -16.0f && v[0] <= 32.0f
            && v[1] >= -16.0f && v[1] <= 32.0f
            && v[2] >= -16.0f && v[2] <= 32.0f;
    }

    private static float[] computeDefaultUV(float[] from, float[] to, Direction face) {
        float x0 = from[0], y0 = from[1], z0 = from[2];
        float x1 = to[0], y1 = to[1], z1 = to[2];
        switch (face) {
            case NORTH:
                return new float[]{x0, 16 - y1, x1, 16 - y0};
            case SOUTH:
                return new float[]{16 - x1, 16 - y1, 16 - x0, 16 - y0};
            case EAST:
                return new float[]{16 - z1, 16 - y1, 16 - z0, 16 - y0};
            case WEST:
                return new float[]{z0, 16 - y1, z1, 16 - y0};
            case DOWN:
                return new float[]{x0, 16 - z1, x1, 16 - z0};
            case UP:
                return new float[]{x0, z0, x1, z1};
            default:
                return new float[]{0, 0, 16, 16};
        }
    }

    private static int idx(int ix, int iy, int iz) {
        return (iy << 2) | (iz << 1) | ix;
    }

    /**
     * Computes the rescale non-uniform scale factors, aligned with 26.1 {@code CuboidRotation.computeRescale}.
     * <p>General implementation: transform each axis's positive unit vector by the rotation matrix (equivalent to
     * taking the corresponding column of the rotation matrix), take the maximum absolute value of the transformed
     * components, and set the scale factor = 1 / that maximum.
     * For a single-axis rotation θ it degenerates to:
     * <ul>
     *   <li>Y-axis rotation: scaleX = scaleZ = 1 / max(|cosθ|, |sinθ|), scaleY = 1</li>
     *   <li>X-axis rotation: scaleY = scaleZ = 1 / max(|cosθ|, |sinθ|), scaleX = 1</li>
     *   <li>Z-axis rotation: scaleX = scaleY = 1 / max(|cosθ|, |sinθ|), scaleZ = 1</li>
     * </ul>
     * At 45° the factor is √2 (> 1): scale up first, then rotate, so the largest projected component returns to
     * its original size after rotation, compensating the visual shrink caused by rotation.
     * <p>[FIX] The reciprocal of the sum of absolute row values cannot be used: that value is always &gt; 1 for any
     * θ ∈ (0°, 90°), so its reciprocal is always &lt; 1 and would squeeze the element thinner (down to 1/√2 at 45°),
     * narrowing cross-type models.
     *
     * @param rot the rotation matrix (single-axis or multi-axis composition)
     * @return [sx, sy, sz] scale factors
     */
    private static double[] computeRescaleFactors(Matrix4d rot) {
        // 26.1 scaleFactorForAxis: rotation.transformDirection(axis.getPositive().getUnitVec3f()),
        // i.e. columns 0/1/2 obtained by transforming (1,0,0)/(0,1,0)/(0,0,1) (vecmath notation m<row><col>).
        return new double[]{
            scaleFactorForAxis(rot.m00, rot.m10, rot.m20),
            scaleFactorForAxis(rot.m01, rot.m11, rot.m21),
            scaleFactorForAxis(rot.m02, rot.m12, rot.m22)
        };
    }

    /**
     * 1 / max(|a|, |b|, |c|): the reciprocal of the largest absolute component after transforming a unit vector
     * (26.1 {@code scaleFactorForAxis}).
     */
    private static double scaleFactorForAxis(double a, double b, double c) {
        double maxComponent = Math.max(Math.abs(a), Math.max(Math.abs(b), Math.abs(c)));
        return maxComponent > 1e-10 ? 1.0 / maxComponent : 1.0;
    }

    /**
     * Applies a Y-axis rotation to a list of BakedQuads (around the block center 0.5, 0.5).
     * Rotates both vertex coordinates and faceNormal while keeping UVs unchanged.
     * <p>
     * [C1 fix] Returns a new, deep-copied list without modifying the original quads, preventing cache pollution.
     *
     * @param quads the quad list to rotate (not modified)
     * @param degY  Y-axis rotation angle (arbitrary angles supported, e.g. 22.5°)
     * @return the rotated new BakedQuad list
     */
    public static List<BakedQuad> applyYRotation(List<BakedQuad> quads, float degY) {
        if (quads == null || quads.isEmpty() || degY == 0) return quads;
        // Minecraft blockstate y rotation is clockwise when viewed from above (north→east→south→west),
        // whereas vecmath Matrix4d.rotY is counter-clockwise in a right-handed system (north→west); the two
        // directions are opposite, so a negated angle is used to match the Minecraft convention - otherwise
        // blocks such as glass panes would flip east/west.
        Matrix4d rotY = new Matrix4d();
        rotY.rotY(Math.toRadians(-degY));
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad src : quads) {
            BakedQuad q = deepCopyQuad(src);
            for (int i = 0; i < 4; i++) {
                // Tuple3d has no sub(double,double,double): offset manually
                q.vertices[i].x -= 0.5;
                q.vertices[i].z -= 0.5;
                rotY.transform(q.vertices[i]);
                q.vertices[i].x += 0.5;
                q.vertices[i].z += 0.5;
            }
            // Rotate the normal
            if (q.faceNormal != null) {
                rotY.transform(q.faceNormal);
            }
            // A Y-axis rotation changes the face orientation and cull direction: rotate face and cullface along with it,
            // otherwise connection blocks relying on cullface (such as glass panes) would flip east/west and cull incorrectly.
            q.face = recomputeFace(q);
            q.cullface = rotateCullface(q.cullface, rotY);
            // Reorder vertices by the new face after rotation (aligned with 26.1.2 FaceBakery.recalculateWinding),
            // otherwise AO brightness maps to the wrong corner (e.g. asymmetric bark brightness on S/N faces).
            recalculateWinding(q);
            result.add(q);
        }
        return result;
    }

    /**
     * Applies an X-axis rotation to a list of BakedQuads (around the block center 0.5, 0.5).
     * Rotates both vertex coordinates and faceNormal while keeping UVs unchanged.
     * <p>
     * [W3] Supports the x rotation field in blockstates.
     *
     * @param quads the quad list to rotate (not modified)
     * @param degX  X-axis rotation angle (arbitrary angles supported, e.g. 22.5°)
     * @return the rotated new BakedQuad list
     */
    public static List<BakedQuad> applyXRotation(List<BakedQuad> quads, float degX) {
        if (quads == null || quads.isEmpty() || degX == 0) return quads;
        Matrix4d rotX = new Matrix4d();
        rotX.rotX(Math.toRadians(degX));
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad src : quads) {
            BakedQuad q = deepCopyQuad(src);
            for (int i = 0; i < 4; i++) {
                q.vertices[i].y -= 0.5;
                q.vertices[i].z -= 0.5;
                rotX.transform(q.vertices[i]);
                q.vertices[i].y += 0.5;
                q.vertices[i].z += 0.5;
            }
            // Rotate the normal
            if (q.faceNormal != null) {
                rotX.transform(q.faceNormal);
            }
            // An X-axis rotation changes the face orientation and cull direction: recompute face and rotate cullface accordingly
            q.face = recomputeFace(q);
            q.cullface = rotateCullface(q.cullface, rotX);
            // Reorder vertices by the new face after rotation (aligned with 26.1.2 FaceBakery.recalculateWinding),
            // otherwise AO brightness maps to the wrong corner (e.g. the bark bottom brighter than the top on a sideways log).
            recalculateWinding(q);
            result.add(q);
        }
        return result;
    }

    /**
     * Applies a Z-axis rotation to a list of BakedQuads (around the block center 0.5, 0.5).
     * Rotates both vertex coordinates and faceNormal while keeping UVs unchanged.
     *
     * @param quads the quad list to rotate (not modified)
     * @param degZ  Z-axis rotation angle (arbitrary angles supported, e.g. 22.5°)
     * @return the rotated new BakedQuad list
     */
    public static List<BakedQuad> applyZRotation(List<BakedQuad> quads, float degZ) {
        if (quads == null || quads.isEmpty() || degZ == 0) return quads;
        Matrix4d rotZ = new Matrix4d();
        rotZ.rotZ(Math.toRadians(degZ));
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad src : quads) {
            BakedQuad q = deepCopyQuad(src);
            for (int i = 0; i < 4; i++) {
                q.vertices[i].x -= 0.5;
                q.vertices[i].y -= 0.5;
                rotZ.transform(q.vertices[i]);
                q.vertices[i].x += 0.5;
                q.vertices[i].y += 0.5;
            }
            // Rotate the normal
            if (q.faceNormal != null) {
                rotZ.transform(q.faceNormal);
            }
            // A Z-axis rotation changes the face orientation and cull direction: recompute face and rotate cullface accordingly
            q.face = recomputeFace(q);
            q.cullface = rotateCullface(q.cullface, rotZ);
            // Reorder vertices by the new face after rotation (aligned with 26.1.2 FaceBakery.recalculateWinding),
            // otherwise AO brightness maps to the wrong corner.
            recalculateWinding(q);
            result.add(q);
        }
        return result;
    }

    /**
     * Deep-copies the vertex data of a BakedQuad into an independent copy.
     */
    private static BakedQuad deepCopyQuad(BakedQuad src) {
        BakedQuad q = new BakedQuad();
        for (int i = 0; i < 4; i++) {
            q.vertices[i] = src.vertices[i] != null ? new Vector3d(src.vertices[i]) : null;
        }
        System.arraycopy(src.up, 0, q.up, 0, 4);
        System.arraycopy(src.vp, 0, q.vp, 0, 4);
        if (src.faceNormal != null) {
            q.faceNormal = new Vector3d(src.faceNormal);
        }
        q.icon = src.icon;
        q.face = src.face;
        q.tintIndex = src.tintIndex;
        q.cullface = src.cullface;
        q.ambientOcclusion = src.ambientOcclusion;
        q.shadeEnabled = src.shadeEnabled;
        q.shadeDirectionOverride = src.shadeDirectionOverride;
        q.guiLight = src.guiLight;
        q.solidColor = src.solidColor;
        return q;
    }

    /**
     * Transforms the cullface normal vector with the same rotation matrix used for the geometry, then maps it back
     * to the nearest {@link Direction}.
     * <p>Guarantees that the cullface (occlusion test direction) always stays consistent with the rotated geometry
     * orientation, avoiding culling errors from still testing neighbors along the original direction.
     *
     * @param cull the original cullface (may be null)
     * @param rot  the same rotation matrix used for vertices/normals
     * @return the rotated cullface, or null when the input is null
     */
    private static Direction rotateCullface(Direction cull, Matrix4d rot) {
        if (cull == null) return null;
        Vector3d n = cull.getNormalVec3d();
        rot.transform(n);
        return Direction.getApproximateNearest(n.x, n.y, n.z);
    }

    /**
     * Re-derives the Direction from the face normal (the orientation may change after an X-axis rotation).
     */
    private static Direction recomputeFace(BakedQuad q) {
        if (q.faceNormal == null) return q.face;
        double ax = Math.abs(q.faceNormal.x), ay = Math.abs(q.faceNormal.y), az = Math.abs(q.faceNormal.z);
        if (ay >= ax && ay >= az) return q.faceNormal.y > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= ay && ax >= az) return q.faceNormal.x > 0 ? Direction.EAST : Direction.WEST;
        return q.faceNormal.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Reorders vertices after rotation (aligned with 26.1.2 {@code FaceBakery.recalculateWinding}).
     * <p>
     * X/Y rotations change the face orientation: {@code q.face} has been recomputed from the new normal, but the
     * vertex array still keeps the pre-rotation conventional order. AO shading (AmbientVertexRemap) relies on the
     * "v0 = top-left corner, counter-clockwise seen from outside the face" FaceInfo vertex convention, so a wrong
     * order writes corner brightness to the wrong position - e.g. after an UP face is turned into SOUTH by x:90,
     * the original v0 (north-top) lands on (south-bottom), making the bark bottom brighter than the top on a
     * sideways log and the bark S/N faces asymmetric. This method reorders vertices/up/vp by the rotated
     * {@code q.face} using the FaceInfo selector, moving UVs along with the vertices (consistent with 26.1.2).
     */
    private static void recalculateWinding(BakedQuad q) {
        Direction face = q.face;
        if (face == null) return;
        // FaceInfo slot selector: {a axis, a axis takes MAX, b axis, b axis takes MAX} (0=x, 1=y, 2=z),
        // aligned with the 26.1.2 FaceInfo vertex convention (v0=top-left, counter-clockwise seen from outside the face).
        final int[][] sel;
        switch (face) {
            case DOWN:  sel = new int[][]{{0,0,2,1},{0,0,2,0},{0,1,2,0},{0,1,2,1}}; break;
            case UP:    sel = new int[][]{{0,0,2,0},{0,0,2,1},{0,1,2,1},{0,1,2,0}}; break;
            case NORTH: sel = new int[][]{{0,1,1,1},{0,1,1,0},{0,0,1,0},{0,0,1,1}}; break;
            case SOUTH: sel = new int[][]{{0,0,1,1},{0,0,1,0},{0,1,1,0},{0,1,1,1}}; break;
            case WEST:  sel = new int[][]{{1,1,2,0},{1,0,2,0},{1,0,2,1},{1,1,2,1}}; break;
            default:    sel = new int[][]{{1,1,2,1},{1,0,2,1},{1,0,2,0},{1,1,2,0}}; break; // EAST
        }
        // The midpoint of the in-face axis coordinate range serves as the bucket threshold (rotated vertices are floats, so (min+max)/2 is more robust)
        double midA = midCoord(q, sel[0][0]);
        double midB = midCoord(q, sel[0][2]);
        Vector3d[] nv = new Vector3d[4];
        float[] nu = new float[4], nvp = new float[4];
        for (int slot = 0; slot < 4; slot++) {
            boolean wantA = sel[slot][1] == 1, wantB = sel[slot][3] == 1;
            int aAxis = sel[slot][0], bAxis = sel[slot][2];
            for (int src = 0; src < 4; src++) {
                Vector3d v = q.vertices[src];
                if (v == null) continue;
                if ((coord(v, aAxis) > midA) == wantA && (coord(v, bAxis) > midB) == wantB) {
                    nv[slot] = q.vertices[src];
                    nu[slot] = q.up[src];
                    nvp[slot] = q.vp[src];
                    break;
                }
            }
        }
        System.arraycopy(nv, 0, q.vertices, 0, 4);
        System.arraycopy(nu, 0, q.up, 0, 4);
        System.arraycopy(nvp, 0, q.vp, 0, 4);
    }

    /** Takes the coordinate midpoint of the quad's four vertices along the given axis ((min+max)/2). */
    private static double midCoord(BakedQuad q, int axis) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            Vector3d v = q.vertices[i];
            if (v == null) continue;
            double c = coord(v, axis);
            min = Math.min(min, c);
            max = Math.max(max, c);
        }
        return (min + max) * 0.5;
    }

    /** Gets the given axis coordinate of a vector (0=x, 1=y, 2=z). */
    private static double coord(Vector3d v, int axis) {
        switch (axis) {
            case 0: return v.x;
            case 1: return v.y;
            default: return v.z;
        }
    }

    /**
     * Parse the 26.3+ {@code shade_direction_override} value (up/down/north/south/east/west) to a Direction.
     * Returns null when absent; values already rejected by the JSON deserializer are null here.
     */
    private static Direction parseShadeDirectionOverride(String value) {
        return Direction.byName(value != null && !value.isEmpty() ? value.toLowerCase() : null);
    }

    /**
     * Parse cullface string (e.g. "south", "up") to Direction. Returns null if invalid or null.
     */
    private static Direction parseCullface(String cullface) {
        return Direction.byName(cullface != null && !cullface.isEmpty() ? cullface.toLowerCase() : null);
    }

    public static class BakedQuad {
        /** 3D coordinates of the 4 vertices (replacing the old scalar vx/vy/vz arrays) */
        public final Vector3d[] vertices = new Vector3d[4];
        public final float[] up = new float[4];
        public final float[] vp = new float[4];
        public Vector3d faceNormal;
        public IIcon icon;
        public Direction face;
        /**
         * Tint index from JSON face. -1 = no tint, 0+ = use block.colorMultiplier for biome color.
         */
        public int tintIndex = -1;
        /**
         * Cullface direction from JSON face. null means no cullface.
         */
        public Direction cullface;
        /**
         * Ambient occlusion flag: null = use the model-level default, true = enable AO, false = disable AO
         */
        public Boolean ambientOcclusion = null;
        /**
         * Directional shading flag: null = use the model-level default, true = enabled, false = disabled (emissive)
         */
        public Boolean shadeEnabled = null;

        /**
         * 26.3+ shading direction override ({@code shade_direction_override}): null = shade by the quad's actual
         * face direction (default); non-null = every face of this element uses this direction for the cardinal
         * shading lookup instead (the actual face direction still drives light-sampling positions).
         */
        public Direction shadeDirectionOverride = null;

        /**
         * Atlas ownership flag (mirroring how 26.1.2 {@code BakedQuad.MaterialInfo} picks a RenderType by
         * {@code sprite.atlasLocation()}): written during baking by the {@code ModelJsonUnbakedAdapter} post-scan and
         * used at render time to choose the atlas to bind.
         * true = blocks atlas (default, consistent with the missingno/null icon fallback), false = items atlas.
         */
        public boolean blockAtlas = true;

        /**
         * Convenience method: gets the X coordinate of vertex i.
         * Replaces the old vx[i] references when submitting to the Tessellator.
         */
        public double vx(int i) { return vertices[i] != null ? vertices[i].x : 0; }

        /**
         * Convenience method: gets the Y coordinate of vertex i.
         * Replaces the old vy[i] references when submitting to the Tessellator.
         */
        public double vy(int i) { return vertices[i] != null ? vertices[i].y : 0; }

        /**
         * Convenience method: gets the Z coordinate of vertex i.
         * Replaces the old vz[i] references when submitting to the Tessellator.
         */
        public double vz(int i) { return vertices[i] != null ? vertices[i].z : 0; }

        /**
         * Lighting mode (model level).
         * <ul>
         *   <li>{@code "front"} - front lighting (item / flat lighting)</li>
         *   <li>{@code "side"} - side lighting (block / 3D lighting)</li>
         *   <li>{@code null} - not set</li>
         * </ul>
         */
        public String guiLight = null;


        /**
         * Solid-color fill (ARGB; 0 means no solid color, normal texture sampling).
         * <p>Used for the side quads of builtin/generated item models: during baking the color is sampled directly
         * from the texture's edge pixels and applied as a vertex-color multiplier at render time, ensuring the sides
         * show a solid color consistent with the edge pixels and avoiding texture-atlas sampling deviations.
         */
        public int solidColor = 0;
    }
}
