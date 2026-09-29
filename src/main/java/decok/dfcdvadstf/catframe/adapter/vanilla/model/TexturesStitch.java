package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import decok.dfcdvadstf.catframe.model.render.extension.LeavesGraphicsExtension;
import decok.dfcdvadstf.catframe.resources.atlas.CatAtlasManager;
import net.minecraftforge.client.event.TextureStitchEvent;

public class TexturesStitch {

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() == 0) {
            // Incremental model discovery at the platform sync point: the first block-atlas
            // stitch fires after ALL mods' preInit, the second (refreshResources) after the
            // whole FML lifecycle — late registrations get picked up there.
            ModelManagerDataLoader.init();
            // [Render three-domain architecture] Vanilla backend (the only path): feed the
            // definition-driven collection into the vanilla blocks atlas (registerIcon), and the
            // vanilla stitcher performs layout + upload. UI-domain assets (the catframe:gui atlas
            // definition) are not part of this — they go through the independent
            // GuiTextureStitchEvent chain.
            CatAtlasManager.registerDefinedSprites(event.map);
            // Register vanilla model textures before atlas is stitched
            VanillaTextureTracker.registerTextures(event.map);
            // Register _opaque leaf textures
            LeavesGraphicsExtension.registerTextures(event.map);
        } else if (event.map.getTextureType() == 1) {
            // [Render three-domain architecture] Vanilla backend: feed the definition-driven
            // collection into the vanilla items atlas.
            CatAtlasManager.registerDefinedSprites(event.map);
            // Register item textures on the item atlas
            VanillaTextureTracker.registerItemTextures(event.map);
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() == 0) {
            // Collect IIcon references and bake models
            VanillaTextureTracker.onTextureStitchPost(event.map);
            // Resolve _opaque leaf IIcons
            LeavesGraphicsExtension.onTextureStitchPost(event.map);
        } else if (event.map.getTextureType() == 1) {
            // After the item atlas is stitched, refresh item texture IIcon references and re-bake
            VanillaTextureTracker.onTextureStitchPostItem(event.map);
        }
    }
}
