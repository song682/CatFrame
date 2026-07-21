package decok.dfcdvadstf.catframe.compact.vanilla;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/**
 * <p>
 * Overlay HUD 驱动器（纯 Forge 实现）<br>
 * 将 {@link OverlayManager} 接入游戏内 HUD 渲染与客户端 tick 循环，使 HUD 上下文的
 * Overlay 无需打开界面即可显示——与 {@code ClientToastHandler} 同属 {@code RenderGameOverlayEvent} 路径。
 * </p>
 * <p>
 * Overlay HUD driver (pure Forge). Bridges {@link OverlayManager} into the in-game HUD render
 * pass and the client tick loop, so HUD-context overlays render without any open screen —
 * the same {@code RenderGameOverlayEvent} path used by {@code ClientToastHandler}.
 * </p>
 * <p>
 * 屏幕上下文（{@code SCREEN}）的 Overlay 不受此处影响，仍由界面自身的 {@code drawScreen}
 * 调用 {@link OverlayManager#renderAll} 渲染。
 * </p>
 */
@SideOnly(Side.CLIENT)
public class ClientOverlayHandler {

    /**
     * Advance every registered overlay once per client tick while the game is not paused.
     * <p>游戏未暂停时，每客户端 tick 推进一次所有已注册 Overlay。</p>
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.isGamePaused()) {
            return;
        }
        OverlayManager.INSTANCE.updateAll();
    }

    /**
     * Render HUD-context overlays after the vanilla HUD is drawn.
     * <p>在原版 HUD 绘制完成后渲染 HUD 上下文的 Overlay。</p>
     */
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.hideGUI || mc.thePlayer == null) {
            return;
        }
        OverlayManager.INSTANCE.renderHud(event.partialTicks);
    }
}
