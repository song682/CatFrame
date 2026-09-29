package decok.dfcdvadstf.catframe.adapter.vanilla;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent;

/**
 * <p>
 * The frame-lifecycle driver for {@link GuiGraphicsExtractor} (pure Forge). Uses Forge's
 * {@link DrawScreenEvent} to reset and flush the deferred render pipeline around each screen
 * draw, replacing the previous mixin injections on {@code GuiScreen.drawScreen} HEAD/RETURN
 * (which never fired for {@code GuiContainer} subclasses that override {@code drawScreen}).
 * </p>
 * <p>
 * <b>Why switch to Forge events:</b> {@code GuiContainer} (inventory, chests, furnaces, creative, etc.)
 * <b>overrides {@code drawScreen} without calling {@code super}</b>,
 * so mixin injections on {@code GuiScreen.drawScreen} HEAD/RETURN never fired in container screens;
 * deferred pipeline elements (item models / PiP / component tooltips) would be lost without the
 * end-of-frame flush. Forge's {@link DrawScreenEvent.Pre}/{@link DrawScreenEvent.Post} wrap
 * {@code currentScreen.drawScreen} and fire for <b>all</b> screens (including {@code GuiContainer}
 * subclasses), enabling reliable frame lifecycle driving.
 * </p>
 */
@SideOnly(Side.CLIENT)
public class ClientScreenGraphicsHandler {

    /**
     * Frame start: reset deferred render state before screen draw (PiP / tooltip).
     * <p>Item models now render immediately (at {@code item()} call site), no end-of-frame flush needed.</p>
     * <p>Mirrors the original {@code MixinGuiScreen} {@code drawScreen} HEAD injection.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDrawScreenPre(DrawScreenEvent.Pre event) {
        GuiGraphicsExtractor.getInstance().resetForNewFrame();
    }

    /**
     * Frame end: flush deferred elements (PiP entities / tooltip) after screen draw,
     * ensuring tooltip always renders on top.
     * <p>Item models now render immediately, no item flush here.</p>
     * <p>Mirrors the original {@code MixinGuiScreen} {@code drawScreen} RETURN injection.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDrawScreenPost(DrawScreenEvent.Post event) {
        GuiGraphicsExtractor.getInstance().extractDeferredElements();
    }
}
