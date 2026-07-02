package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.core.async.AsyncBakePipeline;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Texture tracking and management extracted from {@link VanillaModelManager}.
 * <p>
 * Responsible for collecting texture paths from models/blockstates during loading,
 * registering them with the appropriate texture atlas, and stitching post-processing.
 */
@SideOnly(Side.CLIENT)
public class VanillaTextureTracker {

    // ==================== 纹理追踪注册表 ====================

    static final Set<String> pendingTextures = new LinkedHashSet<>();
    static final Set<String> pendingItemTextures = new LinkedHashSet<>();
    public static final Map<String, IIcon> textureIcons = new HashMap<>();

    static void collectTexturesFromBlockstate(@Nonnull BlockstateJson bs) {
        if (bs.variants != null) {
            for (BlockstateJson.VariantEntry entry : bs.variants.values()) {
                if (entry.isArray()) {
                    for (BlockstateJson.Variant v : entry.list) {
                        collectTexturesFromModel(v.model, false);
                    }
                } else if (entry.single != null) {
                    collectTexturesFromModel(entry.single.model, false);
                }
            }
        }
        if (bs.multipart != null) {
            for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                if (mpc.apply != null) {
                    collectTexturesFromModel(mpc.apply.model, false);
                }
            }
        }
    }

    /**
     * Internal: resolve a model and collect its textures into the appropriate
     * pending set based on model type.
     *
     * @param modelPath   model resource path
     * @param isItemModel true → item atlas, false → block atlas
     */
    static void collectTexturesFromModel(String modelPath, boolean isItemModel) {
        if (modelPath == null) return;
        ModelJson resolved = ModelResolver.resolve(modelPath);
        if (resolved != null) {
            Set<String> textures = ModelResolver.collectTextures(resolved);
            for (String tex : textures) {
                if (isItemModel) {
                    pendingItemTextures.add(tex);
                } else {
                    pendingTextures.add(tex);
                }
            }
        }
    }

    // ==================== Texture Registration ====================

    /**
     * Register all block textures with the block texture map (type 0).
     * Call during TextureStitchEvent.Pre when getTextureType() == 0.
     */
    public static void registerTextures(TextureMap map) {
        for (String texturePath : pendingTextures) {
            String iconName = VanillaModelManager.Utilities.resolveTextureName(texturePath);
            if (iconName != null && !iconName.isEmpty()) {
                map.registerIcon(iconName);
            }
        }
    }

    /**
     * Register all item textures with the item texture map (type 1).
     * Call during TextureStitchEvent.Pre when getTextureType() == 1.
     */
    public static void registerItemTextures(TextureMap map) {
        for (String texturePath : pendingItemTextures) {
            String iconName = VanillaModelManager.Utilities.resolveTextureName(texturePath);
            if (iconName != null && !iconName.isEmpty()) {
                map.registerIcon(iconName);
            }
        }
    }

    /**
     * Collect IIcon references after stitching and bake all models.
     * Call during TextureStitchEvent.Post when getTextureType() == 0.
     */
    public static void onTextureStitchPost(TextureMap map) {
        // 不清理 pendingTextures/pendingItemTextures —— 与 LegacyPreview 行为一致。
        // Forge 1.7.10 启动时会触发两次 type=0 Post（第二次来自 refreshResources）。
        // 保留数据让两次都能从各自的新图集重新收集 IIcon，避免 stale reference。
        if (pendingTextures.isEmpty() && pendingItemTextures.isEmpty()) {
            CatFrame.logger.info("[VTT-diag] onStitchPost: pending sets empty, skip");
            return;
        }
        textureIcons.clear();
        int blockCollected = 0, blockMissed = 0;
        // Block atlas icons
        for (String texturePath : pendingTextures) {
            String iconName = VanillaModelManager.Utilities.resolveTextureName(texturePath);
            if (iconName != null) {
                IIcon icon = map.getAtlasSprite(iconName);
                if (icon != null) {
                    textureIcons.put(texturePath, icon);
                    blockCollected++;
                } else {
                    blockMissed++;
                    CatFrame.logger.warn("[VTT-diag] block IIcon miss: texturePath='{}' → iconName='{}'", texturePath, iconName);
                }
            }
        }
        // Item atlas icons
        int itemCollected = 0, itemMissed = 0;
        net.minecraft.client.renderer.texture.TextureMap itemMap =
                (net.minecraft.client.renderer.texture.TextureMap) Minecraft.getMinecraft().getTextureManager()
                        .getTexture(TextureMap.locationItemsTexture);
        if (itemMap != null) {
            for (String texturePath : pendingItemTextures) {
                String iconName = VanillaModelManager.Utilities.resolveTextureName(texturePath);
                if (iconName != null) {
                    IIcon icon = itemMap.getAtlasSprite(iconName);
                    if (icon != null) {
                        textureIcons.put(texturePath, icon);
                        itemCollected++;
                    } else {
                        itemMissed++;
                    }
                }
            }
        }
        CatFrame.logger.info("[VTT-diag] onStitchPost: pendingBlk={} pendingItm={} | collected blk={} miss={} itm={} miss={} | textureIcons.size={}",
                pendingTextures.size(), pendingItemTextures.size(),
                blockCollected, blockMissed, itemCollected, itemMissed,
                textureIcons.size());
        // 不清理 pendingTextures —— 保留数据供 Forge refreshResources 后的第二次 stitch 重新收集
        // 对标高版本 MaterialBaker 实例化闭包模式：iconMap 作为参数传入缓存和烘焙管线
        BakedModelCache.INSTANCE.clear(textureIcons);
        ModelResolver.clearCache();

        CatFrame.logger.info("[VTT-diag] BakedModelCache.clear(iconMap) called | textureIcons.size={}",
                textureIcons.size());
        // 注册懒模型（不执行同步烘焙，烘焙由 BakedModelCache 懒烘焙 + AsyncBakePipeline 异步预烘焙承担）
        VMMModelBaking.registerAllModels();
        // 异步预烘焙管线：将常用模型预烘焙到 BakedModelCache（传入 iconMap）
        AsyncBakePipeline.triggerAsyncBake(textureIcons);
    }

    /**
     * 在 item atlas (type 1) 缝合完成后更新 item 纹理的 IIcon 引用并重新烘焙。
     * <p>
     * 1.7.10 中 block atlas (type 0) 的 {@link net.minecraftforge.client.event.TextureStitchEvent.Post} 早于
     * item atlas (type 1) 的 Post。因此 type 0 Post 时 item atlas 可能尚未完全
     * 缝合，{@link TextureMap#getAtlasSprite(String)} 可能返回缝合前的占位 sprite
     * 甚至 missingno。
     * 本方法在 type 1 Post 中被调用，此时 item atlas 已缝合完成，可以获取正确的
     * sprite UV 坐标。
     */
    public static void onTextureStitchPostItem(TextureMap itemMap) {
        // 保留 pendingItemTextures 数据 —— 与 LegacyPreview 一致，支持 refreshResources 后的多次 stitch
        if (pendingItemTextures.isEmpty()) {
            CatFrame.logger.info("[VTT-diag] onStitchPostItem: pending empty, skip");
            return;
        }
        // 更新 item 纹理的 IIcon 引用（item atlas 此时已缝合完成）
        for (String texturePath : pendingItemTextures) {
            String iconName = VanillaModelManager.Utilities.resolveTextureName(texturePath);
            if (iconName != null) {
                IIcon icon = itemMap.getAtlasSprite(iconName);
                if (icon != null) {
                    textureIcons.put(texturePath, icon);
                }
            }
        }
        // 不清理 pendingItemTextures —— 保留数据供多次 stitch 重新收集
        // item iconMap 更新到缓存（懒烘焙时使用）
        BakedModelCache.INSTANCE.clear(textureIcons);
        // [W2 修复] 仅增量更新 item 模型注册（懒模型，无需实际烘焙）
        VMMModelBaking.registerItemModels();
        // item atlas 就绪后再次触发异步预烘焙（确保 item 模型也被预烘焙）
        AsyncBakePipeline.triggerAsyncBake(textureIcons);
    }
}
