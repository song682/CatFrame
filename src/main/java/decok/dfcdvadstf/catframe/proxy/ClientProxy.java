package decok.dfcdvadstf.catframe.proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.compact.forge.language.LanguageRegister;
import decok.dfcdvadstf.catframe.compact.vanilla.ClientOverlayHandler;
import decok.dfcdvadstf.catframe.compact.vanilla.ClientScreenGraphicsHandler;
import decok.dfcdvadstf.catframe.compact.vanilla.ClientToastHandler;
import decok.dfcdvadstf.catframe.compact.vanilla.LanguageReloadListener;
import decok.dfcdvadstf.catframe.compact.vanilla.model.ResourcePackModelDetector;
import decok.dfcdvadstf.catframe.compact.vanilla.model.VanillaMetadataMapper;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.extension.LeavesGraphicsExtension;
import decok.dfcdvadstf.catframe.model.render.extension.tint.LeavesInHandTintProvider;
import decok.dfcdvadstf.catframe.model.render.extension.tint.LeavesTintProvider;
import decok.dfcdvadstf.catframe.model.render.extension.tint.SpawnEggAndPotionTintProvider;
import decok.dfcdvadstf.catframe.model.render.extension.tint.TintRegistry;
import decok.dfcdvadstf.catframe.ui.components.ActionBarOverlay;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayManager;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        // Register client event handler (welcome toast + HUD toast rendering)
        MinecraftForge.EVENT_BUS.register(new ClientToastHandler());

        // Drive GuiGraphicsExtractor's deferred pipeline (item/PiP/tooltip) via Forge
        // DrawScreenEvent Pre/Post so it works in GuiContainer screens too
        // (which override drawScreen and never trigger the GuiScreen mixin injections).
        MinecraftForge.EVENT_BUS.register(new ClientScreenGraphicsHandler());

        // Bridge OverlayManager into the HUD render/tick loop (pure Forge), then
        // register the ActionBar as a HUD-context overlay so it shows in-game.
        MinecraftForge.EVENT_BUS.register(new ClientOverlayHandler());
        OverlayManager.INSTANCE.register(ActionBarOverlay.INSTANCE);

        VanillaMetadataMapper.registerVanillaMetadataMappings();
        ModelManagerDataLoader.registerNamespace(Tags.MODID);
        ModelManagerDataLoader.init();

        if (blueyPlushy != null) {
            // ModernItem 自身已实现 IItemState，直接注册为模型实例
            ModelRegistry.registerItemModel(blueyPlushy, blueyPlushy);
        }

        // Register tint providers and graphics extensions
        TintRegistry.register(new LeavesTintProvider());
        TintRegistry.register(new LeavesInHandTintProvider());
        TintRegistry.register(new SpawnEggAndPotionTintProvider());
        ModelRenderRegistry.register(new LeavesGraphicsExtension());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Register language reload listener for resource pack translation overrides
        LanguageReloadListener.register();
        ResourcePackModelDetector.register();
    }
}
