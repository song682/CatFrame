package decok.dfcdvadstf.catframe;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import decok.dfcdvadstf.catframe.command.CommandDataPack;
import decok.dfcdvadstf.catframe.command.CommandFunction;
import decok.dfcdvadstf.catframe.command.CommandTitle;
import decok.dfcdvadstf.catframe.datapack.DataPackManager;
import decok.dfcdvadstf.catframe.proxy.CommonProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(
        modid = Tags.MODID,
        name = Tags.NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.7.10]",
        useMetadata = true,
        customProperties = {
                @Mod.CustomProperty(k = "license", v = "MIT"),
                @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/song682/CatFrame/issues"),
                @Mod.CustomProperty(k = "iconFile", v = "assets/catframe/logo.png"),
                @Mod.CustomProperty(k = "backgroundFile", v = "assets/catalogue/background.png")
        }
)
public class CatFrame {
    public static Logger logger = LogManager.getLogger(Tags.NAME);
    public static CatFrameConfig config;

    @SidedProxy(
            serverSide = "decok.dfcdvadstf.catframe.proxy.CommonProxy",
            clientSide = "decok.dfcdvadstf.catframe.proxy.ClientProxy"
    )
    public static CommonProxy proxyCommon;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Pre initialization logic
        logger = event.getModLog();
        config = new CatFrameConfig(event.getSuggestedConfigurationFile());

        proxyCommon.preInit(event);

        logger.info("Pre initialization logic complete");

    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxyCommon.init(event);

        logger.info("Initialization logic complete");
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandTitle());
        // Registered before the pipeline runs: #minecraft:load may call /function.
        event.registerServerCommand(new CommandFunction());

        // Data pack pipeline: discover + apply on the first server start, and
        // reapply when the active world changed (singleplayer world switch).
        // Failures are logged and must never block world loading.
        try {
            File configDir = Loader.instance().getConfigDir();
            File gameDir = configDir.getParentFile() != null ? configDir.getParentFile() : configDir;
            File worldDir = event.getServer().getEntityWorld().getSaveHandler().getWorldDirectory();
            DataPackManager.bootstrap(configDir, gameDir, worldDir);
        } catch (RuntimeException e) {
            logger.error("Failed to bootstrap data packs", e);
        }

        event.registerServerCommand(new CommandDataPack());
    }
}
