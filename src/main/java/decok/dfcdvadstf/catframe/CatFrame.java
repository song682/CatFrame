package decok.dfcdvadstf.catframe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import decok.dfcdvadstf.catframe.compact.forge.event.ODorTag;
import decok.dfcdvadstf.catframe.compact.vanilla.ClientEventHandler;
import decok.dfcdvadstf.catframe.compact.vanilla.LanguageReloadListener;
import decok.dfcdvadstf.catframe.compact.vanilla.model.TexturesStitch;
import decok.dfcdvadstf.catframe.compact.vanilla.model.VanillaMetadataMapper;
import decok.dfcdvadstf.catframe.component.RegisteredComponents;
import decok.dfcdvadstf.catframe.langguage.LanguageRegister;
import decok.dfcdvadstf.catframe.compact.vanilla.model.ResourcePackModelDetector;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
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

        // Register data components
        RegisteredComponents.registerAll();

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
            // load() is now triggered automatically by LanguageRegister.domain()

            // Register client event handler (welcome toast + HUD toast rendering)
            MinecraftForge.EVENT_BUS.register(new ClientEventHandler());

            VanillaMetadataMapper.registerVanillaMetadataMappings();
            ModelManagerDataLoader.registerNamespace("catframe");
            ModelManagerDataLoader.init();

            if (blueyPlushy != null) {
                // 通过 ModernItem 的双模型 API 注册（纹理由 IItemState 扫描自动收集）
                // ModernItem 自身已实现 IItemState，直接注册为模型实例
                ModelRegistry.registerItemModel(blueyPlushy, blueyPlushy);
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

        // Register language reload listener for resource pack translation overrides
        LanguageReloadListener.register();
        ResourcePackModelDetector.register();

        logger.info("Initialization logic complete");
    }
}
