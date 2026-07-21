package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.extension.ao.light.CardinalLighting;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.util.List;

/**
 * 顶点写入器：把原 {@code UniformRenderPipeline} 中"逐顶点写 Tessellator 的循环体"
 * 抽取为可复用的纯写入静态方法，对标原版 26w+ 管线中的 {@code QuadWriter}。
 * <p>
 * <b>职责边界（严格）</b>：本类<em>只写顶点</em> —— 运行扩展链
 * （{@link ModelRenderRegistry#apply(RenderContext)}）、计算亮度/颜色/UV、执行旋转与
 * display/preTransform 向量变换、调用 {@code t.addVertexWithUV}。
 * <b>不</b>做 {@code startDrawingQuads}/{@code draw}、<b>不</b>改 GL 状态、<b>不</b>绑纹理、
 * <b>不</b>调用 {@code applyBeforePart}/{@code applyAfterPart}（这些生命周期由
 * {@link FeatureRenderDispatcher} 按提交项管理）。
 * <p>
 * 逐顶点逻辑与原 {@code UniformRenderPipeline} 保持逐行一致，确保零渲染回归。
 */
public final class QuadWriter {

    private QuadWriter() {
    }

    /**
     * 写入方块 quads（世界或 GUI）。迁移自原 {@code renderBlockQuads} 的 for-quad 循环。
     *
     * @return 是否写入了任何顶点（供调用方决定是否 {@code t.draw()}）
     */
    public static boolean writeBlockQuads(RenderSubmit s, Tessellator t) {
        boolean isGui = (s.phase == RenderPhase.BLOCK_GUI);
        List<BakedQuad> allQuads = s.part.getAllQuads();

        double a = Math.toRadians(s.rotationDeg);
        double cos = Math.cos(a), sin = Math.sin(a);
        Point3d tmpVec = new Point3d();

        boolean hasVertices = false;
        for (BakedQuad q : allQuads) {
            int baseBrightness = isGui ? 255
                    : (s.world != null ? s.block.getMixedBrightnessForBlock(s.world, s.x, s.y, s.z) : 0);
            // 方向阴影：世界与 GUI 一致，均按面方向取 CardinalLighting 系数。
            float baseShade = CardinalLighting.DEFAULT.byFace(q.face);

            // 创建上下文并运行扩展链
            RenderContext ctx = new RenderContext(s.phase, q,
                    s.world, s.x, s.y, s.z, s.block, null, baseBrightness, baseShade);
            ctx.metadata = s.metadata;
            ModelRenderRegistry.apply(ctx);
            if (ctx.skip) continue;
            hasVertices = true;

            // 提交到 Tessellator
            boolean hasVertexAO = ctx.aoBrightness[0] >= 0;

            if (hasVertexAO && !isGui) {
                for (int i = 0; i < 4; i++) {
                    t.setBrightness(ctx.aoBrightness[i]);
                    float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade * ctx.aoColorMul[i];
                    t.setColorOpaque_F(cr, cg, cb);

                    double vx = q.vx(i), vy = q.vy(i), vz = q.vz(i);
                    if (s.rotationDeg != 0) {
                        double px = vx - 0.5, pz = vz - 0.5;
                        vx = px * cos - pz * sin + 0.5;
                        vz = px * sin + pz * cos + 0.5;
                    }
                    // 应用 display transform（仅 BLOCK_GUI 时有值）
                    if (ctx.displayTransform != null) {
                        tmpVec.set(vx, vy, vz);
                        ctx.displayTransform.transform(tmpVec);
                        vx = tmpVec.x; vy = tmpVec.y; vz = tmpVec.z;
                    }
                    IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
                    double U = icon.getInterpolatedU(q.up[i]);
                    double V = icon.getInterpolatedV(q.vp[i]);
                    t.addVertexWithUV(s.x + vx, s.y + vy, s.z + vz, U, V);
                }
            } else {
                t.setBrightness(ctx.effectiveBrightness());
                float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade;
                float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade;
                float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade;

                IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
                if (isGui) {
                    // GUI 模式使用 RGBA 以正确处理纹理 alpha 通道
                    t.setColorRGBA_F(cr, cg, cb, 1.0f);
                } else {
                    t.setColorOpaque_F(cr, cg, cb);
                }
                for (int i = 0; i < 4; i++) {
                    double vx = q.vx(i), vy = q.vy(i), vz = q.vz(i);
                    if (s.rotationDeg != 0) {
                        double px = vx - 0.5, pz = vz - 0.5;
                        vx = px * cos - pz * sin + 0.5;
                        vz = px * sin + pz * cos + 0.5;
                    }
                    // 应用 display transform（仅 BLOCK_GUI 时有值）
                    if (ctx.displayTransform != null) {
                        tmpVec.set(vx, vy, vz);
                        ctx.displayTransform.transform(tmpVec);
                        vx = tmpVec.x; vy = tmpVec.y; vz = tmpVec.z;
                    }
                    double U = icon.getInterpolatedU(q.up[i]);
                    double V = icon.getInterpolatedV(q.vp[i]);
                    t.addVertexWithUV(s.x + vx, s.y + vy, s.z + vz, U, V);
                }
            }
        }
        return hasVertices;
    }

    /**
     * 写入物品 quads（GUI / 手持 / 掉落 / 展示框）。迁移自原 {@code renderItemQuads} 的 for-quad 循环。
     *
     * @return 是否写入了任何顶点（供调用方决定是否 {@code t.draw()}）
     */
    public static boolean writeItemQuads(RenderSubmit s, Tessellator t) {
        List<BakedQuad> allQuads = s.part.getAllQuads();
        boolean gui = (s.phase == RenderPhase.ITEM_GUI);
        // [方案B] 非 GUI 物品阶段（手持 / 掉落 / 展示框）保留 GL_LIGHTING，
        // 改用逐面法线让 GL 计算方向光照，不再把 CardinalLighting 方向阴影烘焙进顶点色，
        // 避免“烘焙阴影 + GL 光照”双重着色导致的视角相关 bug。GUI 阶段维持烘焙阴影。
        boolean glLit = !gui;
        Matrix4d preTransform = s.preTransform;
        Point3d tmpVec = new Point3d();
        Vector3d tmpNormal = glLit ? new Vector3d() : null;

        int baseBrightness = gui ? 255 : 15728880;
        boolean hasVertices = false;

        for (BakedQuad q : allQuads) {
            // 方向阴影：GUI 按面方向烘焙 CardinalLighting 系数；
            // 非 GUI 交由 GL_LIGHTING 依逐面法线计算，baseShade 取 1.0 以免双重着色。
            float baseShade = glLit ? 1.0f : CardinalLighting.DEFAULT.byFace(q.face);
            RenderContext ctx = new RenderContext(s.phase, q,
                    s.world, s.x, s.y, s.z, s.block, s.stack, baseBrightness, baseShade);
            ModelRenderRegistry.apply(ctx);
            if (ctx.skip) continue;
            hasVertices = true;

            // 非 GUI 阶段：发送逐面法线（随 display / preTransform 旋转），供 GL_LIGHTING 使用。
            if (glLit) {
                writeQuadNormal(t, q, ctx.displayTransform, preTransform, tmpNormal);
            }

            t.setBrightness(ctx.effectiveBrightness());
            float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade;
            float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade;
            float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade;
            if (gui) {
                t.setColorRGBA_F(cr, cg, cb, 1.0f);
            } else {
                t.setColorOpaque_F(cr, cg, cb);
            }

            IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
            for (int i = 0; i < 4; i++) {
                double U = icon.getInterpolatedU(q.up[i]);
                double V = icon.getInterpolatedV(q.vp[i]);

                // 向量空间变换：先应用 display transform，再应用 preTransform
                // 顶点变换顺序：v' = M_pre × M_display × v
                tmpVec.set(q.vx(i), q.vy(i), q.vz(i));
                if (ctx.displayTransform != null) {
                    ctx.displayTransform.transform(tmpVec);
                }
                if (preTransform != null) {
                    preTransform.transform(tmpVec);
                }
                t.addVertexWithUV(tmpVec.x, tmpVec.y, tmpVec.z, U, V);
            }
        }
        return hasVertices;
    }

    /**
     * [方案B] 计算 quad 的逐面法线并写入 Tessellator。
     * <p>
     * 法线取自 {@link BakedQuad#face} 的方向向量，依次经 display / preTransform 的
     * 旋转部分变换（与软件变换后的顶点保持一致），归一化后调用 {@code t.setNormal}。
     * 长度由 {@code GL_NORMALIZE} 兜底（见 {@link FeatureRenderDispatcher}），故只需方向正确。
     */
    private static void writeQuadNormal(Tessellator t, BakedQuad q,
                                        Matrix4d displayTransform, Matrix4d preTransform,
                                        Vector3d tmp) {
        Direction face = q.face;
        if (face != null) {
            tmp.set(face.getStepX(), face.getStepY(), face.getStepZ());
        } else {
            tmp.set(0.0, 1.0, 0.0);
        }
        if (displayTransform != null) transformDirection(displayTransform, tmp);
        if (preTransform != null) transformDirection(preTransform, tmp);
        double len = tmp.length();
        if (len > 1.0e-6) tmp.scale(1.0 / len);
        t.setNormal((float) tmp.x, (float) tmp.y, (float) tmp.z);
    }

    /** 用 4x4 矩阵的 3x3 旋转部分变换方向向量（忽略平移）。 */
    private static void transformDirection(Matrix4d m, Vector3d v) {
        double x = m.m00 * v.x + m.m01 * v.y + m.m02 * v.z;
        double y = m.m10 * v.x + m.m11 * v.y + m.m12 * v.z;
        double z = m.m20 * v.x + m.m21 * v.y + m.m22 * v.z;
        v.set(x, y, z);
    }
}
