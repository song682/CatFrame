package decok.dfcdvadstf.catframe.model.core.baking;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import net.minecraft.util.IIcon;

import javax.vecmath.AxisAngle4d;
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
        // [C6] 空值检查：from/to 为 JSON 必需字段，损坏模型可能为 null
        if (e.from == null || e.to == null) {
            CatFrame.logger.warn("[BlockJsonModelBake] bakeElement: element has null from/to, skipping");
            return out;
        }
        // [C7] 范围校验：每个分量必须在 [-16, 32] 像素范围内（对齐 26.1 CuboidModelElement）
        if (!isInBounds(e.from) || !isInBounds(e.to)) {
            CatFrame.logger.warn("[BlockJsonModelBake] bakeElement: from/to out of bounds [-16,32], from={} to={}, skipping",
                    java.util.Arrays.toString(e.from), java.util.Arrays.toString(e.to));
            return out;
        }
        // 保存原始 from/to（像素坐标），UV 计算依赖原始未旋转的 bounds
        final float[] elemFrom = e.from;
        final float[] elemTo = e.to;
        // 获取元素级别的 ambientocclusion 和 shade 设置
        final Boolean elemAO = e.ambientocclusion;
        final Boolean elemShade = e.shade;
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
        // 归一化旋转格式：兼容 x/y/z 字段（高版本 Blockbench 格式）→ angle/axis
        if (e.rotation != null && e.rotation.angle == 0f && e.rotation.axis == null) {
            if (e.rotation.x != 0f) {
                e.rotation.angle = e.rotation.x;
                e.rotation.axis = "x";
            } else if (e.rotation.y != 0f) {
                e.rotation.angle = e.rotation.y;
                e.rotation.axis = "y";
            } else if (e.rotation.z != 0f) {
                e.rotation.angle = e.rotation.z;
                e.rotation.axis = "z";
            }
        }

        if (e.rotation != null && e.rotation.angle != 0) {
            char ax = Character.toLowerCase(e.rotation.axis.charAt(0));
            float ang = e.rotation.angle;
            float[] o = e.rotation.origin;
            // [C6] origin 空值兜底（Blockbench 导出可能不含 origin）
            if (o == null) o = new float[]{8f, 8f, 8f};
            boolean originIsZero = (o.length >= 3 && o[0] == 0f && o[1] == 0f && o[2] == 0f);
            final float oxPx = originIsZero ? 8f : o[0];
            final float oyPx = originIsZero ? ((e.from[1] + e.to[1]) * 0.5f) : o[1];
            final float ozPx = originIsZero ? 8f : o[2];

            // [W4] rescale：旋转前沿各局部坐标轴应用非均匀缩放，
            // 使旋转后的最大投影分量恢复至原始大小，补偿旋转造成的视觉收缩。
            // 对齐 26.1 CuboidRotation.computeRescale 语义：rotation × scale。
            if (e.rotation.rescale) {
                double[] rescaleS = computeRescaleFactors(ax, ang);
                double ox = oxPx / 16.0, oy = oyPx / 16.0, oz = ozPx / 16.0;
                for (int i = 0; i < 8; i++) {
                    C[i].x = (C[i].x - ox) * rescaleS[0] + ox;
                    C[i].y = (C[i].y - oy) * rescaleS[1] + oy;
                    C[i].z = (C[i].z - oz) * rescaleS[2] + oz;
                }
            }

            double angleRad = Math.toRadians(ang);
            Vector3d axisVec = new Vector3d(ax == 'x' ? 1 : 0, ax == 'y' ? 1 : 0, ax == 'z' ? 1 : 0);
            Vector3d origin = new Vector3d(oxPx / 16.0, oyPx / 16.0, ozPx / 16.0);
            Matrix4d rot = new Matrix4d();
            rot.setIdentity();
            rot.setRotation(new AxisAngle4d(axisVec, angleRad));
            for (int i = 0; i < 8; i++) {
                C[i].sub(origin);
                rot.transform(C[i]);
                C[i].add(origin);
            }
        }
        emitFaceFromCorners(out, e.faces.north, iconMap, C, new int[]{idx(1, 1, 0), idx(1, 0, 0), idx(0, 0, 0), idx(0, 1, 0)}, Direction.NORTH, elemFrom, elemTo, elemAO, elemShade, textureSize);
        emitFaceFromCorners(out, e.faces.south, iconMap, C, new int[]{idx(0, 1, 1), idx(0, 0, 1), idx(1, 0, 1), idx(1, 1, 1)}, Direction.SOUTH, elemFrom, elemTo, elemAO, elemShade, textureSize);
        emitFaceFromCorners(out, e.faces.west, iconMap, C, new int[]{idx(0, 1, 0), idx(0, 0, 0), idx(0, 0, 1), idx(0, 1, 1)}, Direction.WEST, elemFrom, elemTo, elemAO, elemShade, textureSize);
        emitFaceFromCorners(out, e.faces.east, iconMap, C, new int[]{idx(1, 1, 1), idx(1, 0, 1), idx(1, 0, 0), idx(1, 1, 0)}, Direction.EAST, elemFrom, elemTo, elemAO, elemShade, textureSize);
        emitFaceFromCorners(out, e.faces.down, iconMap, C, new int[]{idx(0, 0, 1), idx(0, 0, 0), idx(1, 0, 0), idx(1, 0, 1)}, Direction.DOWN, elemFrom, elemTo, elemAO, elemShade, textureSize);
        emitFaceFromCorners(out, e.faces.up, iconMap, C, new int[]{idx(0, 1, 0), idx(0, 1, 1), idx(1, 1, 1), idx(1, 1, 0)}, Direction.UP, elemFrom, elemTo, elemAO, elemShade, textureSize);
        return out;
    }

    private static void emitFaceFromCorners(List<BakedQuad> out, ModelJson.Face f, Map<String, IIcon> iconMap, Vector3d[] C, int[] id, Direction facing,
                                            float[] elemFrom, float[] elemTo, Boolean elemAO, Boolean elemShade, int[] textureSize) {
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
        // 用 vecmath 向量运算替代标量 normal() 函数
        Vector3d a = new Vector3d(q.vertices[1]);
        a.sub(q.vertices[0]);
        Vector3d b = new Vector3d(q.vertices[2]);
        b.sub(q.vertices[0]);
        q.faceNormal = new Vector3d();
        q.faceNormal.cross(a, b);
        q.faceNormal.normalize();
        // 传递元素级别的 AO 和 shade 设置
        q.ambientOcclusion = elemAO;
        q.shadeEnabled = elemShade;
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

        // Blockbench 导出的 UV 值已基于 texture_size 换算到 16x16 抽象空间：
        // 实际映射为 texture_size * (uv / 16) = 材质的实际像素位置。
        // IIcon.getInterpolatedU/V() 也使用 0-16 范围，因此无需额外缩放。
        // 参考：https://en.wiki.vg/File_Formats#Model
    }

    /**
     * Compute default UV from element bounds in pixel space (0-16).
     * Independent of rotation — matches vanilla Minecraft behavior.
     */
    /**
     * 校验 float[3] 的每个分量是否在 [-16, 32] 范围内（对齐 26.1 CuboidModelElement 边界检查）。
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
     * 计算 rescale 非均匀缩放因子，对齐 26.1 {@code CuboidRotation.computeRescale}。
     * <p>对旋转轴对应的单位向量做变换，取变换后各分量的最大绝对值，缩放因子 = 1 / maxComponent。
     * 对于单轴旋转 θ：
     * <ul>
     *   <li>Y 轴旋转：scaleX = scaleZ = 1 / max(|cosθ|, |sinθ|), scaleY = 1</li>
     *   <li>X 轴旋转：scaleY = scaleZ = 1 / max(|cosθ|, |sinθ|), scaleX = 1</li>
     *   <li>Z 轴旋转：scaleX = scaleY = 1 / max(|cosθ|, |sinθ|), scaleZ = 1</li>
     * </ul>
     *
     * @param axis     旋转轴（'x', 'y', 'z'）
     * @param angleDeg 旋转角度（度）
     * @return [sx, sy, sz] 缩放因子
     */
    private static double[] computeRescaleFactors(char axis, float angleDeg) {
        double a = Math.toRadians(angleDeg);
        double cos = Math.abs(Math.cos(a));
        double sin = Math.abs(Math.sin(a));
        double maxComp = Math.max(cos, sin);
        double s = maxComp > 1e-10 ? 1.0 / maxComp : 1.0;
        switch (Character.toLowerCase(axis)) {
            case 'x': return new double[]{1.0, s, s};
            case 'y': return new double[]{s, 1.0, s};
            case 'z': return new double[]{s, s, 1.0};
            default:  return new double[]{1.0, 1.0, 1.0};
        }
    }

    /**
     * 对一组 BakedQuad 应用 Y 轴旋转（绕方块中心 0.5, 0.5）。
     * 同时旋转顶点坐标和 faceNormal，保持 UV 不变。
     * <p>
     * [C1 修复] 返回深拷贝的新列表，不修改原始 quad，防止缓存污染。
     *
     * @param quads   待旋转的 quad 列表（不会被修改）
     * @param degY    Y 轴旋转角度（0/90/180/270）
     * @return 旋转后的新 BakedQuad 列表
     */
    public static List<BakedQuad> applyYRotation(List<BakedQuad> quads, int degY) {
        if (quads == null || quads.isEmpty() || degY == 0) return quads;
        // Minecraft blockstate 的 y 旋转是「俯视顺时针」（北→东→南→西），
        // 而 vecmath Matrix4d.rotY 是右手系逆时针（北→西），二者方向相反，
        // 故取负角度对齐 Minecraft 约定，否则会出现玻璃板等方块东西方向反转。
        Matrix4d rotY = new Matrix4d();
        rotY.rotY(Math.toRadians(-degY));
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad src : quads) {
            BakedQuad q = deepCopyQuad(src);
            for (int i = 0; i < 4; i++) {
                // Tuple3d 没有 sub(double,double,double)，手动偏移
                q.vertices[i].x -= 0.5;
                q.vertices[i].z -= 0.5;
                rotY.transform(q.vertices[i]);
                q.vertices[i].x += 0.5;
                q.vertices[i].z += 0.5;
            }
            // 旋转法线
            if (q.faceNormal != null) {
                rotY.transform(q.faceNormal);
            }
            // Y 轴旋转会改变面朝向与遮挡方向：同步旋转 face 与 cullface，
            // 否则玻璃板等依赖 cullface 的连接方块会出现东西方向错乱、剔除错误。
            q.face = recomputeFace(q);
            q.cullface = rotateCullface(q.cullface, rotY);
            result.add(q);
        }
        return result;
    }

    /**
     * 对一组 BakedQuad 应用 X 轴旋转（绕方块中心 0.5, 0.5）。
     * 同时旋转顶点坐标和 faceNormal，保持 UV 不变。
     * <p>
     * [W3] 支持 blockstate 中的 x 旋转字段。
     *
     * @param quads   待旋转的 quad 列表（不会被修改）
     * @param degX    X 轴旋转角度（0/90/180/270）
     * @return 旋转后的新 BakedQuad 列表
     */
    public static List<BakedQuad> applyXRotation(List<BakedQuad> quads, int degX) {
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
            // 旋转法线
            if (q.faceNormal != null) {
                rotX.transform(q.faceNormal);
            }
            // X 轴旋转会改变面朝向与遮挡方向，需要重新计算 face 并同步旋转 cullface
            q.face = recomputeFace(q);
            q.cullface = rotateCullface(q.cullface, rotX);
            result.add(q);
        }
        return result;
    }

    /**
     * 深拷贝一个 BakedQuad 的顶点数据，生成独立副本。
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
        q.guiLight = src.guiLight;
        q.solidColor = src.solidColor;
        return q;
    }

    /**
     * 用与几何体相同的旋转矩阵变换 cullface 的法向量，再映射回最近的 {@link Direction}。
     * <p>保证 cullface（遮挡检测方向）始终与旋转后的几何朝向一致，
     * 避免旋转后仍以原始方向检测相邻方块导致的剔除错误。
     *
     * @param cull 原始 cullface（可为 null）
     * @param rot  与顶点/法线相同的旋转矩阵
     * @return 旋转后的 cullface，输入为 null 时返回 null
     */
    private static Direction rotateCullface(Direction cull, Matrix4d rot) {
        if (cull == null) return null;
        Vector3d n = cull.getNormalVec3d();
        rot.transform(n);
        return Direction.getApproximateNearest(n.x, n.y, n.z);
    }

    /**
     * 根据面法线重新确定 Direction（X 轴旋转后面朝向可能改变）。
     */
    private static Direction recomputeFace(BakedQuad q) {
        if (q.faceNormal == null) return q.face;
        double ax = Math.abs(q.faceNormal.x), ay = Math.abs(q.faceNormal.y), az = Math.abs(q.faceNormal.z);
        if (ay >= ax && ay >= az) return q.faceNormal.y > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= ay && ax >= az) return q.faceNormal.x > 0 ? Direction.EAST : Direction.WEST;
        return q.faceNormal.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Parse cullface string (e.g. "south", "up") to Direction. Returns null if invalid or null.
     */
    private static Direction parseCullface(String cullface) {
        return Direction.byName(cullface != null && !cullface.isEmpty() ? cullface.toLowerCase() : null);
    }

    public static class BakedQuad {
        /** 4 个顶点的 3D 坐标（替换旧的 vx/vy/vz 标量数组） */
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
         * 环境光遮蔽标记: null 表示使用模型级别默认值, true=启用 AO, false=禁用 AO
         */
        public Boolean ambientOcclusion = null;
        /**
         * 方向阴影标记: null 表示使用模型级别默认值, true=启用, false=禁用(自发光)
         */
        public Boolean shadeEnabled = null;

        /**
         * 便利方法：获取顶点 i 的 X 坐标。
         * 在向 Tessellator 提交时替代旧的 vx[i] 引用。
         */
        public double vx(int i) { return vertices[i] != null ? vertices[i].x : 0; }

        /**
         * 便利方法：获取顶点 i 的 Y 坐标。
         * 在向 Tessellator 提交时替代旧的 vy[i] 引用。
         */
        public double vy(int i) { return vertices[i] != null ? vertices[i].y : 0; }

        /**
         * 便利方法：获取顶点 i 的 Z 坐标。
         * 在向 Tessellator 提交时替代旧的 vz[i] 引用。
         */
        public double vz(int i) { return vertices[i] != null ? vertices[i].z : 0; }

        /**
         * 光照模式（模型级别）。
         * <ul>
         *   <li>{@code "front"} — 正面光照（物品/平面光照）</li>
         *   <li>{@code "side"} — 侧面光照（方块/3D 光照）</li>
         *   <li>{@code null} — 未设置</li>
         * </ul>
         */
        public String guiLight = null;


        /**
         * 纯色填充（ARGB，0 表示不使用纯色，正常纹理采样）。
         * <p>用于 builtin/generated 物品模型的侧面 quad：
         * 烘焙阶段从纹理边缘像素直接采样颜色，渲染阶段作为顶点颜色乘数应用，
         * 确保侧面显示为与边缘像素一致的纯色，避免纹理图集采样偏差。
         */
        public int solidColor = 0;
    }
}
