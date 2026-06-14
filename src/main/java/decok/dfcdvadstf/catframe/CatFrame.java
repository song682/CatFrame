package decok.dfcdvadstf.catframe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.langguage.LanguageRegister;
import decok.dfcdvadstf.catframe.tags.CatFrameTags;
import decok.dfcdvadstf.catframe.langguage.LocalizationManager;
import decok.dfcdvadstf.catframe.model.JsonBlock;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.extension.LeavesGraphicsExtension;
import decok.dfcdvadstf.catframe.model.render.extension.tint.LeavesTintProvider;
import net.minecraft.init.Blocks;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

@Mod(modid = Tags.MODID, name = Tags.NAME, version = Tags.VERSION, useMetadata = true)
public class CatFrame {
    public static Logger logger = LogManager.getLogger(Tags.NAME);

    @SideOnly(Side.CLIENT)
    private static void registerVanillaMetadataMappings() {
        // log: low 2 bits = wood type, high 2 bits = rotation (0=y, 1=x, 2=z, 3=bark→y)
        final String[] woods = {"oak", "spruce", "birch", "jungle"};
        final String[] axes = {"y", "x", "z"};
        VanillaModelManager.DataLoading.registerMetadataMapping(Blocks.log, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", woods[meta & 3]);
            props.put("axis", axes[(meta >> 2) % 3]);
            return props;
        });

        // log2: low 1 bit = wood type, high 2 bits = rotation (same scheme)
        final String[] woods2 = {"acacia", "dark_oak"};
        VanillaModelManager.DataLoading.registerMetadataMapping(Blocks.log2, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", woods2[meta & 1]);
            props.put("axis", axes[(meta >> 2) % 3]);
            return props;
        });

        // leaves / leaves2 / wool / stained_glass / planks 等方块的 metadata→property 映射
        // 已在 metadata_map.json 中声明，VanillaModelManager 烘焙时自动注册，无需手写 Java。
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Pre initialization logic
        logger = event.getModLog();
        MinecraftForge.EVENT_BUS.register(this);

        // ==================== 配置加载 ====================
        CatFrameConfig.init(event);

        // ==================== Bluey 物品注册（受配置控制）====================
        BlueyPlushyItem blueyPlushy = null;
        if (CatFrameConfig.enableBlueyPlushy) {
            blueyPlushy = new BlueyPlushyItem();
            GameRegistry.registerItem(blueyPlushy, "bluey_plushy");
            CatFrameTags.add("plushy", blueyPlushy);
        }

        // --- Register vanilla metadata-to-property mappings (1.7.10 compat) ---
        if (event.getSide() == Side.CLIENT) {
            // 注册本 mod 的语言域，然后初始化 JSON 本地化系统
            LanguageRegister.domain(Tags.MODID, "assets/catframe/lang");
            LocalizationManager.Loader.load();

            registerVanillaMetadataMappings();
            VanillaModelManager.DataLoading.registerNamespace("catframe");
            VanillaModelManager.DataLoading.init();

            // ==================== Bluey 客户端注册（在 DataLoading.init 之后）====================
            if (blueyPlushy != null) {
                // 收集物品纹理 → 注册到 item atlas（type 1）
                VanillaModelManager.TextureManagement.collectTextures("item/bluey", true);
                VanillaModelManager.TextureManagement.collectTextures("item/bluey_inventory", true);
                // 注册自定义 ItemModel（快捷栏 2D + 手持 3D）
                VanillaModelManager.ModelRegistration.registerItemModel(blueyPlushy, new BlueyPlushyItemModel());
            }

            // 注册树叶染色
            LeavesTintProvider.register();
            // 注册树叶画质纹理切换扩展
            ModelRenderRegistry.register(new LeavesGraphicsExtension());
        }

        logger.info("Pre initialization logic complete");

    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Initialization logic
        logger.info("Initialization logic complete");
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() == 0) {
            // Register vanilla model textures before atlas is stitched
            JsonBlock.registerVanillaTextures(event.map);
            // Register _opaque leaf textures
            LeavesGraphicsExtension.registerTextures(event.map);
        } else if (event.map.getTextureType() == 1) {
            // Register item textures on the item atlas
            VanillaModelManager.TextureManagement.registerItemTextures(event.map);
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() == 0) {
            // Collect IIcon references and bake models
            JsonBlock.onTextureStitchPost(event.map);
            // Resolve _opaque leaf IIcons
            LeavesGraphicsExtension.onTextureStitchPost(event.map);
            // Process custom block render requests
            JsonBlock.event();
        } else if (event.map.getTextureType() == 1) {
            // item atlas 缝合完成后更新 item 纹理 IIcon 引用并重新烘焙
            VanillaModelManager.TextureManagement.onTextureStitchPostItem(event.map);
        }
    }
}
