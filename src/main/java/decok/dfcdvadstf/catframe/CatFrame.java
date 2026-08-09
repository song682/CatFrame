package decok.dfcdvadstf.catframe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import decok.dfcdvadstf.catframe.compact.CompactBase;
import decok.dfcdvadstf.catframe.proxy.CommonProxy;
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
        // 不兼容模组检查：ItemPhysic 与 CatFrame 掉落物渲染接管互斥，检测到即崩溃
        // Incompatible-mod check: ItemPhysic conflicts with CatFrame's dropped-item
        // render takeover, crash immediately when detected
        CompactBase.rejectItemPhysic();

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
