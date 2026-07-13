package decok.dfcdvadstf.catframe.compact.vanilla;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.components.toast.SimpleToast;
import decok.dfcdvadstf.catframe.ui.components.toast.ToastManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/**
 * <p>
 * 客户端事件处理器<br>
 * 监听世界加载事件以显示欢迎 Toast，并在 HUD 上渲染 Toast 队列。
 * </p>
 * <p>
 * Client-side event handler — listens for world load to show the welcome Toast,
 * and renders the Toast queue on the HUD overlay.
 * </p>
 */
@SideOnly(Side.CLIENT)
public class ClientToastHandler {

    /** Global ToastManager instance / 全局 Toast 管理器实例 */
    private static final ToastManager TOAST_MANAGER = new ToastManager(Minecraft.getMinecraft());

    /** Whether the welcome toast has been shown this session / 本次会话是否已显示欢迎 Toast */
    private static boolean welcomeShown = false;

    /**
     * @return The global ToastManager / 全局 Toast 管理器
     */
    public static ToastManager getToastManager() {
        return TOAST_MANAGER;
    }

    /**
     * Triggered when any entity joins a world. We filter for the local player only.
     * <p>任意实体加入世界时触发，仅在本地玩家加入时显示欢迎 Toast。</p>
     */
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.entity == Minecraft.getMinecraft().thePlayer && !welcomeShown) {
            welcomeShown = true;
            TOAST_MANAGER.addToast(new SimpleToast(
                    "\u00a7bCatFrame", "Welcome!"
            ));
        }
    }

    /**
     * Render Toasts on the HUD after the vanilla overlay is drawn.
     * <p>在原版 HUD 覆盖层绘制完成后渲染 Toast 队列。</p>
     */
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
            TOAST_MANAGER.update();
            TOAST_MANAGER.render();
        }
    }
}
