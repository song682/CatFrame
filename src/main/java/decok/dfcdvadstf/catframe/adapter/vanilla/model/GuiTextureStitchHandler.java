package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.GuiTextureStitchEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * UI 图集缝合驱动（渲染三域架构）：把原版 {@code TextureStitchEvent} 的资源同步点
 * （startGame + refreshResources 两次全量重载）桥接为 UI 域<b>独立</b>事件链
 * {@link GuiTextureStitchEvent}（Pre / On / Post）。
 * <p>
 * 触发时机：
 * <ul>
 *   <li>{@code TextureStitchEvent.Pre}（type 0 blocks 图集，FML 资源重载起点）→
 *       post {@code GuiTextureStitchEvent.Pre}（收集）+ {@code GuiTextureStitchEvent.On}
 *       （缝合 + 上传）—— UI 图集是独立 GL 纹理，与 blocks 缝合并行无冲突；</li>
 *   <li>{@code TextureStitchEvent.Post}（type 1 items 图集，最后完成的原版图集）→
 *       post {@code GuiTextureStitchEvent.Post}（查表发布）—— 保证所有原版纹理已加载
 *       完毕后 UI 查表才就绪。</li>
 * </ul>
 * <p>
 * UI 图集不参与原版 blocks/items 缝合（不 registerIcon 进 TextureMap），消费端
 * 只服务 CatFrame 自家 UI（theme / 组件树 / tooltip）；原版 GUI 与第三方 GUI
 * 保持逐张绑定，零影响。
 * <p>
 * 阶段 A 骨架期：仅驱动事件链（日志留痕）；收集 / 缝合 / 发布消费端由阶段 B 填充。
 *
 * <p>Bridges the vanilla TextureStitchEvent sync points into the independent
 * GuiTextureStitchEvent chain (Pre/On/Post) that drives the CatFrame GUI atlas.
 */
@SideOnly(Side.CLIENT)
public class GuiTextureStitchHandler {

    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() != 0) {
            return;
        }
        MinecraftForge.EVENT_BUS.post(new GuiTextureStitchEvent.Pre());
        MinecraftForge.EVENT_BUS.post(new GuiTextureStitchEvent.On());
    }

    @SubscribeEvent
    public void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() != 1) {
            return;
        }
        MinecraftForge.EVENT_BUS.post(new GuiTextureStitchEvent.Post());
    }
}
