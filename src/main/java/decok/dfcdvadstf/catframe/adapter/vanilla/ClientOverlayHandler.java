package decok.dfcdvadstf.catframe.adapter.vanilla;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.ui.UiTextureAtlasManager;
import decok.dfcdvadstf.catframe.ui.components.toast.SimpleToast;
import decok.dfcdvadstf.catframe.ui.components.toast.ToastOverlay;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayManager;
import decok.dfcdvadstf.catframe.Tags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/**
 * <p>
 * Overlay HUD driver (pure Forge). Bridges {@link OverlayManager} into the in-game HUD render
 * pass and the client tick loop, so HUD-context overlays render without any open screen.
 * </p>
 * <p>
 * Screen-context ({@code SCREEN} / {@code BOTH}) overlays are driven via Forge's
 * {@link DrawScreenEvent.Post} → {@link OverlayManager#renderAll}. The event fires for
 * <b>every</b> {@code GuiScreen} (including the vanilla main menu and {@code GuiContainer}
 * subclasses), so no world or player entity is required.
 * </p>
 * <p>
 * This class also absorbed the welcome-toast trigger from the removed
 * {@code ClientToastHandler} — Toasts now live inside the Overlay system
 * ({@link ToastOverlay}), so their Forge event triggers converge on this single bridge.
 * </p>
 */
@SideOnly(Side.CLIENT)
public class ClientOverlayHandler {

    /** Whether the welcome toast has been shown this session */
    private static boolean welcomeShown = false;

    /**
     * Triggered when any entity joins a world. We filter for the local player only and
     * show the one-shot welcome Toast (migrated from the removed {@code ClientToastHandler}).
     * Gated by the {@code welcomeToast} config option — skipped entirely when disabled.
     */
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!CatFrame.config.welcomeToast) {
            return;
        }
        if (event.entity == Minecraft.getMinecraft().thePlayer && !welcomeShown) {
            welcomeShown = true;
            ToastOverlay.INSTANCE.getToastManager().addToast(new SimpleToast(
                    I18n.format("toast.title"), I18n.format("toast.description", Tags.VERSION)
            ).setShowSound(new ResourceLocation("random.orb")));
        }
    }

    /**
     * Advance every registered overlay once per client tick while the game is not paused.
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
        // [Render three-domain architecture] UI atlas animation tick (CatAtlas region re-upload; zero overhead when assets are static)
        UiTextureAtlasManager.tickAnimations();
    }

    /**
     * Render HUD-context overlays after the vanilla HUD is drawn.
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
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawScreenPost(DrawScreenEvent.Post event) {
        OverlayManager.INSTANCE.renderAll(event.mouseX, event.mouseY, event.renderPartialTicks);
    }
}
