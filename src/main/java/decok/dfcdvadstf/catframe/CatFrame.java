package decok.dfcdvadstf.catframe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import decok.dfcdvadstf.catframe.command.CommandTitle;
import decok.dfcdvadstf.catframe.proxy.CommonProxy;
import decok.dfcdvadstf.catframe.resources.builtin.BuiltinPackDescriptor;
import decok.dfcdvadstf.catframe.resources.builtin.BuiltinPackRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Tags.MODID,
        name = Tags.NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.7.10]",
        useMetadata = true
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

        // Registered here, not in the coremod: the coremod stage runs before
        // the config exists and before any game class may be loaded.
        if (config.enableBuiltinExampleResource) {
            BuiltinPackRegistry.register(new BuiltinPackDescriptor(
                    "example",
                    "resourcePack.catframe.builtin.example.name",
                    "resourcePack.catframe.builtin.example.description"));
        }

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
    }
}
