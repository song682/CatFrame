package decok.dfcdvadstf.catframe.proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import decok.dfcdvadstf.catframe.BingoPlushyItem;
import decok.dfcdvadstf.catframe.BlueyPlushyItem;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.adapter.forge.event.ODorTag;
import decok.dfcdvadstf.catframe.adapter.forge.language.LanguageRegister;
import decok.dfcdvadstf.catframe.adapter.vanilla.model.RenderItemInFrameHandler;
import decok.dfcdvadstf.catframe.adapter.vanilla.model.TexturesStitch;
import decok.dfcdvadstf.catframe.core.RegisteredComponents;
import decok.dfcdvadstf.catframe.tags.impl.CatFrameTags;
import net.minecraftforge.common.MinecraftForge;

public class CommonProxy {
    protected BlueyPlushyItem blueyPlushy;
    protected BingoPlushyItem bingoPlushy;

    public void preInit(FMLPreInitializationEvent event) {
        // Scan CatFrame's own jar/directory for JSON lang files and inject them
        // 扫描 CatFrame 自身 jar/目录中的 JSON 语言文件并注入
        LanguageRegister.load();
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
