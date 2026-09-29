package decok.dfcdvadstf.catframe.adapter.vanilla;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.adapter.forge.language.LanguageRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraftforge.common.MinecraftForge;

/**
 * Reloads CatFrame's JSON language translations whenever Minecraft's
 * resource manager reloads (initial startup, language switch, resource
 * pack changes). Reads JSON lang files via {@link IResourceManager} so
 * resource pack overrides are picked up automatically.
 * <p>
 * Registration is deferred via a one-shot client tick because the
 * resource manager is not available during mod init.
 */
public class LanguageReloadListener implements IResourceManagerReloadListener {

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        CatFrame.logger.debug("LanguageReloadListener: resource manager reloaded");
        LanguageRegister.reloadFromResourceManager(manager);
    }

/**
     * Registers this listener with Minecraft's resource manager.
     * Safe to call from mod init — if the resource manager isn't ready yet,
     * registration is deferred via a one-shot client tick event.
     */
    public static void register() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getResourceManager() instanceof IReloadableResourceManager) {
            IReloadableResourceManager rm = (IReloadableResourceManager) mc.getResourceManager();
            rm.registerReloadListener(new LanguageReloadListener());
            CatFrame.logger.info("LanguageReloadListener: registered with resource manager");
        } else {
            // Resource manager not ready yet — defer via one-shot client tick
            CatFrame.logger.debug("LanguageReloadListener: resource manager not ready, deferring registration");
            MinecraftForge.EVENT_BUS.register(new Object() {
                @SubscribeEvent
                public void onClientTick(TickEvent.ClientTickEvent event) {
                    if (event.phase == TickEvent.Phase.END) {
                        Minecraft mc2 = Minecraft.getMinecraft();
                        if (mc2 != null && mc2.getResourceManager() instanceof IReloadableResourceManager) {
                            ((IReloadableResourceManager) mc2.getResourceManager())
                                    .registerReloadListener(new LanguageReloadListener());
                            CatFrame.logger.info("LanguageReloadListener: registered (deferred)");
                            MinecraftForge.EVENT_BUS.unregister(this);
                        }
                    }
                }
            });
        }
    }
}
