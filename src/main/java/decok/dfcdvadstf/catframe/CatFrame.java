package decok.dfcdvadstf.catframe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import decok.dfcdvadstf.catframe.proxy.CommonProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Tags.MODID,
        name = Tags.NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.7.10]",
        // Soft ordering only: jarutils is optional; when present, its post-init
        // parallel jar index must be ready before our post-init consumes it.
        // 仅软排序：jarutils 为可选模组；存在时其 post-init 的并发 jar 索引
        // 必须先于本模组 post-init 的消费就绪。
        dependencies = "after:jarutils",
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

        proxyCommon.preInit(event);

        logger.info("Pre initialization logic complete");

    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxyCommon.init(event);

        logger.info("Initialization logic complete");
    }
}
