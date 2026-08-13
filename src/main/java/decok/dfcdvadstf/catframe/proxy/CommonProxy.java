package decok.dfcdvadstf.catframe.proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import decok.dfcdvadstf.catframe.BingoPlushyItem;
import decok.dfcdvadstf.catframe.BlueyPlushyItem;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.compact.forge.event.ODorTag;
import decok.dfcdvadstf.catframe.compact.forge.language.LanguageRegister;
import decok.dfcdvadstf.catframe.compact.vanilla.model.RenderItemInFrameHandler;
import decok.dfcdvadstf.catframe.compact.vanilla.model.TexturesStitch;
import decok.dfcdvadstf.catframe.core.RegisteredComponents;
import decok.dfcdvadstf.catframe.tags.impl.CatFrameTags;
import net.minecraftforge.common.MinecraftForge;

public class CommonProxy {
    protected BlueyPlushyItem blueyPlushy;
    protected BingoPlushyItem bingoPlushy;

    public void preInit(FMLPreInitializationEvent event) {
        // load() is now triggered automatically by LanguageRegister.domain()
        LanguageRegister.domain(Tags.MODID, "assets/catframe/lang");
        // Register data components
        RegisteredComponents.registerAll();
        MinecraftForge.EVENT_BUS.register(new TexturesStitch());
        MinecraftForge.EVENT_BUS.register(new ODorTag());
        MinecraftForge.EVENT_BUS.register(new RenderItemInFrameHandler());

        if (CatFrame.config.enableBlueyPlushy) {
            blueyPlushy = new BlueyPlushyItem();
            bingoPlushy = new BingoPlushyItem();
            GameRegistry.registerItem(blueyPlushy, "bluey_plushy");
            GameRegistry.registerItem(bingoPlushy, "bingo_plushy");
            CatFrameTags.add(Tags.MODID, "plushy", blueyPlushy);
            CatFrameTags.add(Tags.MODID, "plushy", bingoPlushy);
        }
    }

    public void init(FMLInitializationEvent event) {
        ODorTag.onInit();
    }
    public void postInit(FMLPostInitializationEvent event) {}
}
