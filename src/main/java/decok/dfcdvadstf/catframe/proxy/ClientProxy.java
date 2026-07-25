package decok.dfcdvadstf.catframe.proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.compact.vanilla.ClientOverlayHandler;
import decok.dfcdvadstf.catframe.compact.vanilla.ClientScreenGraphicsHandler;
import decok.dfcdvadstf.catframe.compact.vanilla.ClientToastHandler;
import decok.dfcdvadstf.catframe.compact.vanilla.LanguageReloadListener;
import decok.dfcdvadstf.catframe.compact.vanilla.model.ResourcePackModelDetector;
import decok.dfcdvadstf.catframe.compact.vanilla.model.VanillaStateDefinitions;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.extension.LeavesGraphicsExtension;
import decok.dfcdvadstf.catframe.model.render.extension.tint.LeavesInHandTintProvider;
import decok.dfcdvadstf.catframe.model.render.extension.tint.LeavesTintProvider;
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

        VanillaStateDefinitions.registerVanillaStateDefinitions();
        ModelManagerDataLoader.registerNamespace(Tags.MODID);
        ModelManagerDataLoader.init();

        /// Note: There is no need to manually register blueyPlushy models here.
        /// BlueyPlushyItem extends ModernItem and implements IItemStateProvider;
        /// the Tier-3 scan performed by ModelManagerDataLoader.init() automatically discovers all registered
        /// (GameRegistry.registerItem) and registers them as models using the items themselves, marking them as persistent,
        /// in Step 4c of Baking.registerAllModels().
        /// The mapping between models and items is based on the registration ID, consistent with the vanilla version.

        // Register tint providers and graphics extensions
        TintRegistry.register(new LeavesTintProvider());
        TintRegistry.register(new LeavesInHandTintProvider());
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
