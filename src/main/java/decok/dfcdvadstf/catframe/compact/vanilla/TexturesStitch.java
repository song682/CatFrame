package decok.dfcdvadstf.catframe.compact.vanilla;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.JsonBlock;
import decok.dfcdvadstf.catframe.model.VMMTextureTracker;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.extension.LeavesGraphicsExtension;
import net.minecraftforge.client.event.TextureStitchEvent;

public class TexturesStitch {

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() == 0) {
            // Register vanilla model textures before atlas is stitched
            VMMTextureTracker.registerTextures(event.map);
            // Register _opaque leaf textures
            LeavesGraphicsExtension.registerTextures(event.map);
        } else if (event.map.getTextureType() == 1) {
            // Register item textures on the item atlas
            VMMTextureTracker.registerItemTextures(event.map);
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() == 0) {
            // Collect IIcon references and bake models
            VMMTextureTracker.onTextureStitchPost(event.map);
            // Resolve _opaque leaf IIcons
            LeavesGraphicsExtension.onTextureStitchPost(event.map);
            // Process custom block render requests (ISBRH + VMM bridge)
            JsonBlock.event();
        } else if (event.map.getTextureType() == 1) {
            // item atlas 缝合完成后更新 item 纹理 IIcon 引用并重新烘焙
            VMMTextureTracker.onTextureStitchPostItem(event.map);
        }
    }
}
