package decok.dfcdvadstf.catframe.proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.compact.vanilla.ClientOverlayHandler;
import decok.dfcdvadstf.catframe.compact.vanilla.ClientScreenGraphicsHandler;
import decok.dfcdvadstf.catframe.compact.vanilla.ClientToastHandler;
import decok.dfcdvadstf.catframe.compact.vanilla.LanguageReloadListener;
import decok.dfcdvadstf.catframe.compact.vanilla.model.ResourcePackModelDetector;
import decok.dfcdvadstf.catframe.compact.vanilla.model.VanillaMetadataMapper;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
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

        // Drive GuiGraphicsExtractor's deferred pipeline (item/PiP) via Forge
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
        // 注意：无需在此手动注册 blueyPlushy 的模型。
        // BlueyPlushyItem extends ModernItem implements IItemStateProvider，
        // ModelManagerDataLoader.init() 的 Tier-3 扫描会自动发现所有已注册
        // (GameRegistry.registerItem) 的 IItemStateProvider 物品，并在
        // Baking.registerAllModels() Step 4c 中以物品自身为模型注册并标记为 persistent。
        // 模型与物品的对应以注册 ID 为准，与原版一致。

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
