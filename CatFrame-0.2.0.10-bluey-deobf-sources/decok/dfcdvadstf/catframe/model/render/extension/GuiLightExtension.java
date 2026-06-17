package decok.dfcdvadstf.catframe.model.render.extension;

import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.RenderContext;

/**
 * 内建渲染扩展：处理 JSON model 中的 {@code "gui_light"} 字段。
 *
 * <p>根据 JSON model 的 {@code gui_light} 设置来控制方向阴影：
 * <ul>
 *   <li>{@code "front"} — 正面光照，无方向阴影（{@link RenderContext#shade} = 1.0）</li>
 *   <li>{@code "side"} 或 {@code null} — 侧面光照，保留默认方向阴影</li>
 * </ul>
 *
 * <p>本扩展只处理方向阴影（shade），不涉及 OpenGL GL_LIGHTING 状态。
 * GL_LIGHTING 由 {@link decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline}
 * 根据模型 {@code gui_light} 在渲染层面处理：
 * <ul>
 *   <li>{@code "front"}: 关闭 GL_LIGHTING，使用 lightmap 提供亮度变化</li>
 *   <li>{@code "side"}: 保持 GL_LIGHTING 开启，需正确设置顶点法线</li>
 * </ul>
 */
public final class GuiLightExtension implements IModelRenderExtension {

    @Override
    public void apply(RenderContext ctx) {
        String guiLight = ctx.quad.guiLight;
        if (guiLight == null) return;

        // "front" 光照模式：无方向阴影，所有面均匀受光
        if ("front".equals(guiLight)) {
            ctx.shade = 1.0f;
        }
        // "side" 模式：保留默认方向阴影（由 shadeByNormal 计算）
    }
}
