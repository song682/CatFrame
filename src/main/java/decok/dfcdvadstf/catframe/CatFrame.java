package decok.dfcdvadstf.catframe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import decok.dfcdvadstf.catframe.compact.forge.event.ODorTag;
import decok.dfcdvadstf.catframe.compact.vanilla.TexturesStitch;
import decok.dfcdvadstf.catframe.compact.vanilla.VanillaMetadataMapper;
import decok.dfcdvadstf.catframe.langguage.LanguageRegister;
import decok.dfcdvadstf.catframe.langguage.LocalizationManager;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.extension.LeavesGraphicsExtension;
import decok.dfcdvadstf.catframe.model.render.extension.tint.LeavesTintProvider;
import decok.dfcdvadstf.catframe.model.render.extension.tint.SpawnEggAndPotionTintProvider;
import decok.dfcdvadstf.catframe.tags.CatFrameTags;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Tags.MODID, name = Tags.NAME, version = Tags.VERSION, useMetadata = true)
public class CatFrame {
    public static Logger logger = LogManager.getLogger(Tags.NAME);
    public static CatFrameConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Pre initialization logic
        logger = event.getModLog();
        config = new CatFrameConfig(event.getSuggestedConfigurationFile());

        //
        MinecraftForge.EVENT_BUS.register(new TexturesStitch());
        MinecraftForge.EVENT_BUS.register(new ODorTag());
        MinecraftForge.EVENT_BUS.register(new VanillaMetadataMapper());

        BlueyPlushyItem blueyPlushy = null;
        if (config.enableBlueyPlushy) {
            blueyPlushy = new BlueyPlushyItem();
            GameRegistry.registerItem(blueyPlushy, "bluey_plushy");
            CatFrameTags.add("plushy", blueyPlushy);
        }

        if (event.getSide() == Side.CLIENT) {
            LanguageRegister.domain(Tags.MODID, "assets/catframe/lang");
            LocalizationManager.Loader.load();

            VanillaMetadataMapper.registerVanillaMetadataMappings();
            VanillaModelManager.DataLoading.registerNamespace("catframe");
            VanillaModelManager.DataLoading.init();

            if (blueyPlushy != null) {
                // 通过 ModernItem 的双模型 API 注册（纹理由 IItemJsonModel 扫描自动收集）
                VanillaModelManager.ModelRegistration.registerItemModel(
                        blueyPlushy, blueyPlushy.createItemModel());
            }

            LeavesTintProvider.register();
            SpawnEggAndPotionTintProvider.register();
            ModelRenderRegistry.register(new LeavesGraphicsExtension());
        }

        logger.info("Pre initialization logic complete");

    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        ODorTag.onInit();
        logger.info("Initialization logic complete");
    }
}
