package decok.dfcdvadstf.catframe.proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.command.CommandTitle;
import decok.dfcdvadstf.catframe.adapter.vanilla.ClientOverlayHandler;
import decok.dfcdvadstf.catframe.adapter.vanilla.ClientScreenGraphicsHandler;
import decok.dfcdvadstf.catframe.adapter.vanilla.LanguageReloadListener;
import decok.dfcdvadstf.catframe.adapter.vanilla.model.ResourcePackModelDetector;
import decok.dfcdvadstf.catframe.adapter.vanilla.model.VanillaStateDefinitions;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.extension.LeavesGraphicsExtension;
import decok.dfcdvadstf.catframe.model.render.extension.tint.LeavesInHandTintProvider;
import decok.dfcdvadstf.catframe.model.render.extension.tint.LeavesTintProvider;
import decok.dfcdvadstf.catframe.model.render.extension.tint.RedstoneWireTintProvider;
import decok.dfcdvadstf.catframe.model.render.extension.tint.TintRegistry;
import decok.dfcdvadstf.catframe.ui.components.ActionBarOverlay;
import decok.dfcdvadstf.catframe.ui.components.TitleOverlay;
import decok.dfcdvadstf.catframe.ui.components.toast.ToastOverlay;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayManager;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        // Drive GuiGraphicsExtractor's deferred pipeline (item/PiP/tooltip) via Forge
        // DrawScreenEvent Pre/Post so it works in GuiContainer screens too
        // (which override drawScreen and never trigger the GuiScreen mixin injections).
        MinecraftForge.EVENT_BUS.register(new ClientScreenGraphicsHandler());

        // Bridge OverlayManager into the HUD render/tick loop and the screen draw pass
        // (pure Forge; also hosts the welcome-toast trigger), then register the
        // ActionBar
        // and the centred Title as HUD-context overlays and the Toast system as a
        // BOTH-context overlay (HUD + any open screen, main menu included).
        MinecraftForge.EVENT_BUS.register(new ClientOverlayHandler());
        OverlayManager.INSTANCE.register(ActionBarOverlay.INSTANCE);
        OverlayManager.INSTANCE.register(TitleOverlay.INSTANCE);
        OverlayManager.INSTANCE.register(ToastOverlay.INSTANCE);

        VanillaStateDefinitions.registerVanillaStateDefinitions();
        // Reference-only namespace registration — discovery itself now runs
        // incrementally at
        // TextureStitchEvent.Pre (first stitch fires after ALL mods' preInit), see
        // ModelManagerDataLoader.init() invoked from TexturesStitch.
        // 纯引用型命名空间登记 —— 发现流程本身已移至 TextureStitchEvent.Pre 增量执行
        // （第一次缝合在全体 mod preInit 之后），见 TexturesStitch 调用的
        // ModelManagerDataLoader.init()。
        ModelManagerDataLoader.registerNamespace(Tags.MODID);

        /// Note: There is no need to manually register blueyPlushy models here.
        /// BlueyPlushyItem extends ModernItem and implements IItemStateProvider;
        /// the Tier-3 scan performed by ModelManagerDataLoader.init() at texture stitch
        /// automatically
        /// discovers all registered
        /// (GameRegistry.registerItem) and registers them as models using the items
        /// themselves, marking them as persistent,
        /// in Step 4c of Baking.registerAllModels().
        /// The mapping between models and items is based on the registration ID,
        /// consistent with the vanilla version.

        // Register tint providers and graphics extensions
        TintRegistry.register(new LeavesTintProvider());
        TintRegistry.register(new LeavesInHandTintProvider());
        TintRegistry.register(new RedstoneWireTintProvider());
        ModelRenderRegistry.register(new LeavesGraphicsExtension());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Register language reload listener for resource pack translation overrides
        LanguageReloadListener.register();
        ResourcePackModelDetector.register();

        // Client-side /title command — all Title/ActionBar state lives in client
        // singletons and CatFrame has no network channel, so the command executes
        // locally and <targets> narrows to the local player (see CommandTitle docs).
        // 客户端 /title 命令 —— Title/ActionBar 状态全在客户端单例、无网络通道，
        // 故本地执行，<targets> 收敛为本地玩家（详见 CommandTitle 类注释）。
        ClientCommandHandler.instance.registerCommand(new CommandTitle());
    }
}
