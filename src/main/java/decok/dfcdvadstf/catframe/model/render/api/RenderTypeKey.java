package decok.dfcdvadstf.catframe.model.render.api;

import net.minecraft.util.ResourceLocation;

/**
 * 渲染分组注册表条目的只读句柄，对标原版 26w+ 延迟渲染管线中的 render type。
 * <p>
 * 编码"纹理图集 + 混合（solid/translucent）"分组，携带<b>显式排序键</b>
 * （{@link #sortKey()}，越小越先 flush，保证半透明正确叠加在不透明之上）。
 * <p>
 * 内建分组见 {@link decok.dfcdvadstf.catframe.model.render.pipeline.RenderTypeRegistry}；
 * 第三方模组可通过注册表注册新分组（含自定义排序位置）。
 * <p>
 * A read-only handle for a render-group registry entry, modelling the render
 * type concept of the vanilla 26w+ deferred pipeline. Carries an explicit
 * sort key (lower flushes earlier) so translucent layers stack correctly.
 */
public interface RenderTypeKey {

    /** 分组的稳定标识（唯一注册键，如 {@code catframe:block_solid}）。 */
    ResourceLocation id();

    /** 该分组绑定的纹理图集（blocks / items atlas）。 */
    ResourceLocation atlas();

    /** 该分组是否需要开启 alpha 混合（半透明）。 */
    boolean blend();

    /** 显式排序键：越小越先 flush；同键按注册顺序稳定排列。 */
    int sortKey();
}
