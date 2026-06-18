package decok.dfcdvadstf.catframe.model;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.IIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import decok.dfcdvadstf.catframe.CatFrame;

public class BlockJsonModelBake {
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
        // 保存原始 from/to（像素坐标），UV 计算依赖原始未旋转的 bounds
        final float[] elemFrom = e.from;
        final float[] elemTo = e.to;
        // 获取元素级别的 ambientocclusion 和 shade 设置
        final Boolean elemAO = e.ambientocclusion;
        final Boolean elemShade = e.shade;
        double x0 = e.from[0] / 16.0, y0 = e.from[1] / 16.0, z0 = e.from[2] / 16.0;
        double x1 = e.to[0] / 16.0, y1 = e.to[1] / 16.0, z1 = e.to[2] / 16.0;
        double[][] C = new double[8][3];
        C[idx(0, 0, 0)] = new double[]{x0, y0, z0};
        C[idx(1, 0, 0)] = new double[]{x1, y0, z0};
        C[idx(0, 1, 0)] = new double[]{x0, y1, z0};
        C[idx(1, 1, 0)] = new double[]{x1, y1, z0};
        C[idx(0, 0, 1)] = new double[]{x0, y0, z1};
        C[idx(1, 0, 1)] = new double[]{x1, y0, z1};
        C[idx(0, 1, 1)] = new double[]{x0, y1, z1};
        C[idx(1, 1, 1)] = new double[]{x1, y1, z1};
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
            for (int i = 0; i < 8; i++) {
                double[] r = rotateAxisExact(C[i][0], C[i][1], C[i][2], oxPx, oyPx, ozPx, ax, ang);
                C[i][0] = r[0];
                C[i][1] = r[1];
                C[i][2] = r[2];
            }
        }
        emitFaceFromCorners(out, e.faces.north, iconMap, C, new int[]{idx(1, 1, 0), idx(1, 0, 0), idx(0, 0, 0), idx(0, 1, 0)}, EnumFacing.NORTH, elemFrom, elemTo, elemAO, elemShade, textureSize);
        emitFaceFromCorners(out, e.faces.south, iconMap, C, new int[]{idx(0, 1, 1), idx(0, 0, 1), idx(1, 0, 1), idx(1, 1, 1)}, EnumFacing.SOUTH, elemFrom, elemTo, elemAO, elemShade, textureSize);
        emitFaceFromCorners(out, e.faces.west, iconMap, C, new int[]{idx(0, 1, 0), idx(0, 0, 0), idx(0, 0, 1), idx(0, 1, 1)}, EnumFacing.WEST, elemFrom, elemTo, elemAO, elemShade, textureSize);
        emitFaceFromCorners(out, e.faces.east, iconMap, C, new int[]{idx(1, 1, 1), idx(1, 0, 1), idx(1, 0, 0), idx(1, 1, 0)}, EnumFacing.EAST, elemFrom, elemTo, elemAO, elemShade, textureSize);
        emitFaceFromCorners(out, e.faces.down, iconMap, C, new int[]{idx(0, 0, 1), idx(0, 0, 0), idx(1, 0, 0), idx(1, 0, 1)}, EnumFacing.DOWN, elemFrom, elemTo, elemAO, elemShade, textureSize);
        emitFaceFromCorners(out, e.faces.up, iconMap, C, new int[]{idx(0, 1, 0), idx(0, 1, 1), idx(1, 1, 1), idx(1, 1, 0)}, EnumFacing.UP, elemFrom, elemTo, elemAO, elemShade, textureSize);
        return out;
    }

    private static void emitFaceFromCorners(List<BakedQuad> out, ModelJson.Face f, Map<String, IIcon> iconMap, double[][] C, int[] id, EnumFacing facing,
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
            vx[i] = C[id[i]][0];
            vy[i] = C[id[i]][1];
            vz[i] = C[id[i]][2];
        }
        float[] up = new float[4], vp = new float[4];
        assignUVForFace(f, facing, id, up, vp, elemFrom, elemTo, textureSize);
        BakedQuad q = new BakedQuad();
        for (int i = 0; i < 4; i++) {
            q.vx[i] = vx[i];
            q.vy[i] = vy[i];
            q.vz[i] = vz[i];
            q.up[i] = up[i];
            q.vp[i] = vp[i];
        }
        q.icon = icon;
        q.face = facing;
        q.tintIndex = f.tintIndex;
        q.cullface = parseCullface(f.cullface);
        q.faceNormal = normal(q.vx, q.vy, q.vz);
        // 传递元素级别的 AO 和 shade 设置
        q.ambientOcclusion = elemAO;
        q.shadeEnabled = elemShade;
        out.add(q);
    }

    private static void assignUVForFace(ModelJson.Face f, EnumFacing face, int[] ids, float[] outU, float[] outV,
                                        float[] elemFrom, float[] elemTo, int[] textureSize) {
        // Auto-compute default UV from element from/to (pixel space) if not specified in JSON
        if (f.uv == null) {
            f.uv = computeDefaultUV(elemFrom, elemTo, face);
        }
        final float u0 = f.uv[0], v0 = f.uv[1], u1 = f.uv[2], v1 = f.uv[3];
        final float du = u1 - u0, dv = v1 - v0;
        int steps = (f.rotation == null) ? 0 : ((Integer.parseInt(f.rotation) / 90) & 3);
        if (face == EnumFacing.UP || face == EnumFacing.DOWN) {
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
        // [C5] texture_size 字段在 JSON 中被解析但此处不用于 UV 计算。
        // Blockbench 导出的 UV 值已基于 texture_size 换算到 16x16 抽象空间：
        // 实际映射为 texture_size * (uv / 16) = 材质的实际像素位置。
        // IIcon.getInterpolatedU/V() 也使用 0-16 范围，因此无需额外缩放。
        // 参考：https://en.wiki.vg/File_Formats#Model
    }

    /**
     * Compute default UV from element bounds in pixel space (0-16).
     * Independent of rotation — matches vanilla Minecraft behavior.
     */
    private static float[] computeDefaultUV(float[] from, float[] to, EnumFacing face) {
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

    static double[] rotateAxisExact(double x, double y, double z, float oxPx, float oyPx, float ozPx, char axis, float angleDeg) {
        double ox = oxPx / 16.0, oy = oyPx / 16.0, oz = ozPx / 16.0;
        double px = x - ox, py = y - oy, pz = z - oz;
        double a = Math.toRadians(angleDeg), c = Math.cos(a), s = Math.sin(a);
        double rx = px, ry = py, rz = pz;
        switch (Character.toLowerCase(axis)) {
            case 'x':
                ry = py * c - pz * s;
                rz = py * s + pz * c;
                break;
            case 'y':
                rx = px * c - pz * s;
                rz = px * s + pz * c;
                break;
            case 'z':
                rx = px * c - py * s;
                ry = px * s + py * c;
                break;
            default:
                return new double[]{x, y, z};
        }
        return new double[]{rx + ox, ry + oy, rz + oz};
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
        double a = Math.toRadians(degY);
        double cos = Math.cos(a), sin = Math.sin(a);
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad src : quads) {
            BakedQuad q = deepCopyQuad(src);
            for (int i = 0; i < 4; i++) {
                double px = q.vx[i] - 0.5, pz = q.vz[i] - 0.5;
                q.vx[i] = px * cos - pz * sin + 0.5;
                q.vz[i] = px * sin + pz * cos + 0.5;
            }
            // 旋转法线
            if (q.faceNormal != null) {
                double nx = q.faceNormal[0], nz = q.faceNormal[2];
                q.faceNormal[0] = nx * cos - nz * sin;
                q.faceNormal[2] = nx * sin + nz * cos;
            }
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
        double a = Math.toRadians(degX);
        double cos = Math.cos(a), sin = Math.sin(a);
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad src : quads) {
            BakedQuad q = deepCopyQuad(src);
            for (int i = 0; i < 4; i++) {
                double py = q.vy[i] - 0.5, pz = q.vz[i] - 0.5;
                q.vy[i] = py * cos - pz * sin + 0.5;
                q.vz[i] = py * sin + pz * cos + 0.5;
            }
            // 旋转法线
            if (q.faceNormal != null) {
                double ny = q.faceNormal[1], nz = q.faceNormal[2];
                q.faceNormal[1] = ny * cos - nz * sin;
                q.faceNormal[2] = ny * sin + nz * cos;
            }
            // X 轴旋转会改变面朝向，需要重新计算 EnumFacing
            q.face = recomputeFace(q);
            result.add(q);
        }
        return result;
    }

    /**
     * 深拷贝一个 BakedQuad 的顶点数据，生成独立副本。
     */
    private static BakedQuad deepCopyQuad(BakedQuad src) {
        BakedQuad q = new BakedQuad();
        System.arraycopy(src.vx, 0, q.vx, 0, 4);
        System.arraycopy(src.vy, 0, q.vy, 0, 4);
        System.arraycopy(src.vz, 0, q.vz, 0, 4);
        System.arraycopy(src.up, 0, q.up, 0, 4);
        System.arraycopy(src.vp, 0, q.vp, 0, 4);
        if (src.faceNormal != null) {
            q.faceNormal = new double[3];
            System.arraycopy(src.faceNormal, 0, q.faceNormal, 0, 3);
        }
        q.icon = src.icon;
        q.face = src.face;
        q.tintIndex = src.tintIndex;
        q.cullface = src.cullface;
        q.ambientOcclusion = src.ambientOcclusion;
        q.shadeEnabled = src.shadeEnabled;
        q.guiLight = src.guiLight;
        q.modelDisplay = src.modelDisplay;
        return q;
    }

    /**
     * 根据面法线重新确定 EnumFacing（X 轴旋转后面朝向可能改变）。
     */
    private static EnumFacing recomputeFace(BakedQuad q) {
        if (q.faceNormal == null) return q.face;
        double ax = Math.abs(q.faceNormal[0]), ay = Math.abs(q.faceNormal[1]), az = Math.abs(q.faceNormal[2]);
        if (ay >= ax && ay >= az) return q.faceNormal[1] > 0 ? EnumFacing.UP : EnumFacing.DOWN;
        if (ax >= ay && ax >= az) return q.faceNormal[0] > 0 ? EnumFacing.EAST : EnumFacing.WEST;
        return q.faceNormal[2] > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }

    /**
     * Parse cullface string (e.g. "south", "up") to EnumFacing. Returns null if invalid or null.
     */
    private static EnumFacing parseCullface(String cullface) {
        if (cullface == null || cullface.isEmpty()) return null;
        switch (cullface.toLowerCase()) {
            case "down":
                return EnumFacing.DOWN;
            case "up":
                return EnumFacing.UP;
            case "north":
                return EnumFacing.NORTH;
            case "south":
                return EnumFacing.SOUTH;
            case "west":
                return EnumFacing.WEST;
            case "east":
                return EnumFacing.EAST;
            default:
                return null;
        }
    }

    public static double[] normal(double[] vx, double[] vy, double[] vz) {
        double ax = vx[1] - vx[0], ay = vy[1] - vy[0], az = vz[1] - vz[0];
        double bx = vx[2] - vx[0], by = vy[2] - vy[0], bz = vz[2] - vz[0];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bz;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return len == 0 ? new double[]{0, 1, 0} : new double[]{nx / len, ny / len, nz / len};
    }

    public static class BakedQuad {
        public final double[] vx = new double[4];
        public final double[] vy = new double[4];
        public final double[] vz = new double[4];
        public final float[] up = new float[4];
        public final float[] vp = new float[4];
        public double[] faceNormal = new double[3];
        public IIcon icon;
        public EnumFacing face;
        /**
         * Tint index from JSON face. -1 = no tint, 0+ = use block.colorMultiplier for biome color.
         */
        public int tintIndex = -1;
        /**
         * Cullface direction from JSON face. null means no cullface.
         */
        public EnumFacing cullface;
        /**
         * 环境光遮蔽标记: null 表示使用模型级别默认值, true=启用 AO, false=禁用 AO
         */
        public Boolean ambientOcclusion = null;
        /**
         * 方向阴影标记: null 表示使用模型级别默认值, true=启用, false=禁用(自发光)
         */
        public Boolean shadeEnabled = null;

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
         * 模型级别的 display transforms（烘焙时从 ModelJson 传播）。
         * 键为 display 场景（"gui", "firstperson_righthand", "thirdperson_righthand" 等）。
         * 由 DisplayTransformExtension 在渲染时读取并应用。
         */
        public Map<String, ModelJson.DisplayTransform> modelDisplay = null;
    }
}
