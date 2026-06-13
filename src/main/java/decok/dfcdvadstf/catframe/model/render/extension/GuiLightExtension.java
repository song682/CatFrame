package decok.dfcdvadstf.catframe.model.render.extension;

import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 内建渲染扩展：处理 JSON model 中的 {@code "gui_light"} 字段。
 *
 * <p>根据 JSON model 的 {@code gui_light} 设置来控制方向阴影和 OpenGL GL_LIGHTING 状态：
 * <ul>
 *   <li>{@code "front"} — 正面光照，无方向阴影（{@link RenderContext#shade} = 1.0），
 *       关闭 GL_LIGHTING（平面光照，如物品）</li>
 *   <li>{@code "side"} — 侧面光照，保留默认方向阴影，
 *       保持 GL_LIGHTING 开启（立体光照，如方块）</li>
 *   <li>未设置 — 默认 {@code "side"}，保留默认方向阴影</li>
 * </ul>
 *
 * <h3>GL_LIGHTING 生命周期</h3>
 * <p>GL_LIGHTING 状态的开关由 {@link UniformRenderPipeline} 在渲染前/后调用静态方法管理：
 * <ul>
 *   <li>{@link #needsFrontLighting(List)} — 检查模型是否需要正面光照（关闭 GL_LIGHTING）</li>
 *   <li>{@link #setupGLLighting(boolean)} — 设置 GL_LIGHTING 状态</li>
 *   <li>{@link #restoreGLLighting(boolean)} — 恢复 GL_LIGHTING 状态</li>
 * </ul>
 */
public final class GuiLightExtension implements IModelRenderExtension {

   @Override
    public void apply(RenderContext ctx) {
        String guiLight = ctx.quad.guiLight;
        if (guiLight == null) {
            // 未显式设置 gui_light 时默认 "side"（侧面光照，保留 GL_LIGHTING）
            ctx.quad.guiLight = "side";
            return;
        }

        // "front" 光照模式：无方向阴影，所有面均匀受光
        if ("front".equals(guiLight)) {
            ctx.shade = 1.0f;
        }
        // "side" 模式：保留默认方向阴影（由 shadeByNormal 计算）
    }

    // ==================== GL_LIGHTING 生命周期管理（供管线调用） ====================

    /**
     * 检查模型的 quads 是否需要正面光照（{@code gui_light = "front"}）。
     * <p>管线在开始渲染前调用此方法，决定是否需要关闭 GL_LIGHTING。
     *
     * @param quads 模型的全部 BakedQuad 列表
     * @return true 如果模型需要正面光照（应关闭 GL_LIGHTING）
     */
    public static boolean needsFrontLighting(List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) return false;
        return "front".equals(quads.get(0).guiLight);
    }

    /**
     * 根据是否需要正面光照来设置 GL_LIGHTING 状态。
     *
     * @param frontLighting true=关闭 GL_LIGHTING（正面光照），false=开启（侧面光照）
     * @return 修改前的 GL_LIGHTING 状态（用于后续恢复），true=之前是开启的
     */
    public static boolean setupGLLighting(boolean frontLighting) {
        boolean wasEnabled = GL11.glGetBoolean(GL11.GL_LIGHTING);
        if (frontLighting) {
            GL11.glDisable(GL11.GL_LIGHTING);
        } else {
            GL11.glEnable(GL11.GL_LIGHTING);
        }
        return wasEnabled;
    }

    /**
     * 恢复 GL_LIGHTING 状态。
     *
     * @param reEnable true=开启 GL_LIGHTING
     */
    public static void restoreGLLighting(boolean reEnable) {
        if (reEnable) {
            GL11.glEnable(GL11.GL_LIGHTING);
        }
    }
}
