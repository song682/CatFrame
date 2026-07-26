package decok.dfcdvadstf.catframe.compact.vanilla;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.components.toast.SimpleToast;
import decok.dfcdvadstf.catframe.ui.components.toast.ToastManager;
import decok.dfcdvadstf.catframe.ui.components.toast.ToastOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/**
 * <p>
 * 客户端事件处理器<br>
 * 监听世界加载事件以显示欢迎 Toast。
 * </p>
 * <p>
 * Client-side event handler — listens for world load to show the welcome Toast.
 * </p>
 * <p>
 * Toast 渲染已迁移至 Overlay 体系：{@link ToastOverlay} 作为 {@code BOTH} 上下文的
 * Overlay 注册进 {@code OverlayManager}，由 {@code ClientOverlayHandler} 在 HUD 与
 * 任意打开的界面（含主菜单）上驱动绘制；本类不再直接挂接渲染事件。
 * </p>
 * <p>
 * Toast rendering has migrated to the Overlay system: {@link ToastOverlay} is registered
 * with {@code OverlayManager} as a {@code BOTH}-context overlay and driven by
 * {@code ClientOverlayHandler} on both the HUD and any open screen (main menu included);
 * this class no longer hooks render events directly.
 * </p>
 */
@SideOnly(Side.CLIENT)
public class ClientToastHandler {

    /** Whether the welcome toast has been shown this session / 本次会话是否已显示欢迎 Toast */
    private static boolean welcomeShown = false;

    /**
     * @return The global ToastManager (owned by {@link ToastOverlay}) / 全局 Toast 管理器（由 {@link ToastOverlay} 持有）
     */
    public static ToastManager getToastManager() {
        return ToastOverlay.INSTANCE.getToastManager();
    }

    /**
     * Triggered when any entity joins a world. We filter for the local player only.
     * <p>任意实体加入世界时触发，仅在本地玩家加入时显示欢迎 Toast。</p>
     */
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.entity == Minecraft.getMinecraft().thePlayer && !welcomeShown) {
            welcomeShown = true;
            getToastManager().addToast(new SimpleToast(
                    "\u00a7bCatFrame", "Welcome!"
            ).setShowSound(new ResourceLocation("random.orb")));
        }
    }
}
