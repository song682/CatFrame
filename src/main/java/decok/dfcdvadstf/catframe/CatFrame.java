package decok.dfcdvadstf.catframe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import decok.dfcdvadstf.catframe.proxy.ClientProxy;
import decok.dfcdvadstf.catframe.proxy.CommonProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Tags.MODID, name = Tags.NAME, version = Tags.VERSION, useMetadata = true)
public class CatFrame {
    public static Logger logger = LogManager.getLogger(Tags.NAME);
    public static CatFrameConfig config;

    @SidedProxy(
            serverSide = "decok.dfcdvadstf.catframe.proxy.CommonProxy",
            clientSide = "decok.dfcdvadstf.catframe.proxy.ClientProxy"
    )
    public static CommonProxy proxyCommon;
    public static ClientProxy proxyClient;

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
}
