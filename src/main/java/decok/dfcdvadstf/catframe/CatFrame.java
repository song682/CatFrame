package decok.dfcdvadstf.catframe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.JsonBlock;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
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

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Pre initialization logic
        logger = event.getModLog();
        MinecraftForge.EVENT_BUS.register(this);

        // --- Register vanilla metadata-to-property mappings (1.7.10 compat) ---
        if (event.getSide() == Side.CLIENT) {
            registerVanillaMetadataMappings();
            VanillaModelManager.init();
        }

        logger.info("Pre initialization logic complete");
    }

    @SideOnly(Side.CLIENT)
    private static void registerVanillaMetadataMappings() {
        // log: low 2 bits = wood type, high 2 bits = rotation (0=y, 1=x, 2=z, 3=bark→y)
        final String[] woods = {"oak", "spruce", "birch", "jungle"};
        final String[] axes  = {"y", "x", "z"};
        VanillaModelManager.registerMetadataMapping(Blocks.log, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", woods[meta & 3]);
            props.put("axis", axes[(meta >> 2) % 3]);
            return props;
        });

        // log2: low 1 bit = wood type, high 2 bits = rotation (same scheme)
        final String[] woods2 = {"acacia", "dark_oak"};
        VanillaModelManager.registerMetadataMapping(Blocks.log2, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", woods2[meta & 1]);
            props.put("axis", axes[(meta >> 2) % 3]);
            return props;
        });
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
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() == 0) {
            // Collect IIcon references and bake models
            JsonBlock.onTextureStitchPost(event.map);
            // Process custom block render requests
            JsonBlock.event();
        }
    }
}
