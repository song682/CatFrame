package decok.dfcdvadstf.catframe.compact.vanilla;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent;
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
 * 屏幕上下文（{@code SCREEN} / {@code BOTH}）的 Overlay 由 Forge 的
 * {@link DrawScreenEvent.Post} 驱动 {@link OverlayManager#renderAll}。该事件由
 * {@code ForgeHooksClient.drawScreen} 包裹在 {@code currentScreen.drawScreen} 之后触发，
 * 对<b>所有</b> {@code GuiScreen}（含原版主菜单 {@code GuiMainMenu} 与
 * {@code GuiContainer} 子类）生效，因此无需世界或玩家实体即可在任意界面上绘制。
 * </p>
 * <p>
 * Screen-context ({@code SCREEN} / {@code BOTH}) overlays are driven via Forge's
 * {@link DrawScreenEvent.Post} → {@link OverlayManager#renderAll}. The event fires for
 * <b>every</b> {@code GuiScreen} (including the vanilla main menu and {@code GuiContainer}
 * subclasses), so no world or player entity is required.
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

    /**
     * Render screen-context ({@code SCREEN} / {@code BOTH}) overlays after any screen is drawn.
     * Fires for all {@code GuiScreen}s — vanilla main menu included — and never touches
     * {@code thePlayer}, so it is NPE-safe outside a world. Runs at {@code LOWEST} priority
     * so overlays land on top of everything else drawn by {@code DrawScreenEvent} listeners.
     * <p>在任意界面绘制完成后渲染屏幕上下文（{@code SCREEN} / {@code BOTH}）的 Overlay。
     * 对所有 {@code GuiScreen}（含原版主菜单）生效，且完全不接触 {@code thePlayer}，
     * 无世界环境下不会 NPE。以 {@code LOWEST} 优先级运行，确保 Overlay 绘制在其他
     * {@code DrawScreenEvent} 监听者之上。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawScreenPost(DrawScreenEvent.Post event) {
        OverlayManager.INSTANCE.renderAll(event.mouseX, event.mouseY, event.renderPartialTicks);
    }
}
