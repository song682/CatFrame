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
            // 在平台同步点做增量模型发现：第一次方块图集缝合在全体 preInit 之后，
            // 第二次（refreshResources）在整个 FML 生命周期之后 —— 迟到的注册在此补票。
            ModelManagerDataLoader.init();
            // 自研图集缝合：消费模型驱动纹理集合（pending*），完成布局 + 上传 + 注册，
            // 产物在 Post 阶段由 VanillaTextureTracker 发布进 textureIcons。
            // Custom atlas stitch: consumes the model-driven pending sets, runs layout
            // + upload + registration; publication into textureIcons happens in Post.
            CatAtlasManager.stitch();
            // Register vanilla model textures before atlas is stitched
            VanillaTextureTracker.registerTextures(event.map);
            // Register _opaque leaf textures
            LeavesGraphicsExtension.registerTextures(event.map);
        } else if (event.map.getTextureType() == 1) {
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
            // item atlas 缝合完成后更新 item 纹理 IIcon 引用并重新烘焙
            VanillaTextureTracker.onTextureStitchPostItem(event.map);
        }
    }
}
