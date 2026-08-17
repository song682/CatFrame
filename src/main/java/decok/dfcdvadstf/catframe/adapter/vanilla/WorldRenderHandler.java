package decok.dfcdvadstf.catframe.adapter.vanilla;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.model.render.pipeline.WorldRenderBuffer;
import net.minecraftforge.client.event.RenderWorldEvent;

/**
 * 世界渲染后期处理器：在 {@code RenderWorldEvent.Post}（vanilla 全部 chunk 批次
 * 绘制完成后、雾状态仍有效时）批量 flush CatFrame 方块 —— 绑定 CatAtlas 绘制，
 * quad 携带的 CatSprite（CatAtlas 空间 UV）直接采样；纹理表未命中的引用已在
 * 烘焙期解析为 CatAtlas missing（紫黑格）。
 * <p>
 * Flushes the buffered CatFrame world blocks after the vanilla world pass, bound
 * to the CatAtlas; table misses were already resolved to the CatAtlas missing
 * square during baking.
 */
@SideOnly(Side.CLIENT)
public class WorldRenderHandler {

    @SubscribeEvent
    public void onRenderWorldPost(RenderWorldEvent.Post event) {
        // [Hot Update 撤回方案] 原版后端（默认）：BLOCK_WORLD 已在 chunk 编译时内联
        // 写入原版批次，缓冲恒为空 —— 门控防开关切换遗留数据被 Post 路径绘制。
        // Vanilla backend: world quads were already written inline during chunk
        // builds; the gate prevents any leftover buffer from being flushed here.
        if (!CatFrameConfig.catAtlasBackend) {
            return;
        }
        WorldRenderBuffer.flushAndClear();
    }
}
