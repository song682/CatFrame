package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.AtlasPixelCache;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.core.async.AsyncBakePipeline;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.resources.atlas.CatAtlasManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Texture tracking and management extracted from {@link VanillaModelManager}.
 * <p>
 * Responsible for collecting texture paths from models/blockstates during loading,
 * registering them with the appropriate texture atlas, and stitching post-processing.
 */
@SideOnly(Side.CLIENT)
public class VanillaTextureTracker {

    // ==================== 纹理追踪注册表 ====================

    /**
     * 原版图集注册/查询键：直接做 1.7.10 basePath 键变换（剥离 namespace 与
     * {@code blocks/} / {@code items/} / 单数前缀，见 {@code toVanillaBaseKey}）。
     * <p>
     * 仅服务于原版图集（vanilla TextureMap）路径 —— 1.7.10 原版键语义即 basePath
     * （如 {@code minecraft:blocks/ladder} → {@code ladder}）；CatFrame 数据驱动
     * 图集键是完整纹理路径，由 {@code CatAtlasManager} 独立管理，与本方法无关。
     *
     * <p>Vanilla-atlas registry/query key: the plain 1.7.10 base-path key.
     *
     * @param itemAtlas true = items 图集（textures/items/），false = blocks 图集
     */
    public static String toVanillaKey(String texturePath, boolean itemAtlas) {
        if (texturePath == null) return null;
        return VanillaModelManager.Utilities.toVanillaBaseKey(texturePath);
    }

    static final Set<String> pendingTextures = new LinkedHashSet<>();
    static final Set<String> pendingItemTextures = new LinkedHashSet<>();
    public static final Map<String, IIcon> textureIcons = new ConcurrentHashMap<>();
    // vanilla sprite 快照表：texturePath → 原版收集循环的 vanilla IIcon（publish 前快照）。
    // 世界渲染（BLOCK_WORLD/BLOCK_DESTROY）绑定 vanilla blocks 图集（chunk 混批约束下
    // 无法换绑 CatAtlas），QuadWriter 用本表把 CatSprite（CatAtlas 空间 UV）换回原版
    // sprite（原版空间 UV），避免 quad 在原版图集上采样错位 → 模糊/空白。
    // Vanilla sprite snapshot: CatSprite UVs (CatAtlas space) are remapped back to
    // the vanilla sprite space because world batches bind the vanilla blocks atlas.
    static final Map<String, IIcon> vanillaIcons = new ConcurrentHashMap<>();

    /**
     * 原版 sprite 快照表（世界渲染 UV 回退用，跨包访问入口）。
     */
    public static Map<String, IIcon> getVanillaIcons() {
        return vanillaIcons;
    }

    /**
     * 模型驱动的 block 纹理集合（跨包访问入口，供 CatAtlasManager 消费）。
     */
    public static Set<String> getPendingTextures() {
        return pendingTextures;
    }

    /**
     * 模型驱动的 item 纹理集合（跨包访问入口，供 CatAtlasManager 消费）。
     */
    public static Set<String> getPendingItemTextures() {
        return pendingItemTextures;
    }

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
                // Extract path after namespace for prefix checking
                // e.g., "minecraft:block/sapling_oak" → pathPart = "block/sapling_oak"
                String pathPart = tex;
                int colon = tex.indexOf(':');
                if (colon >= 0) {
                    pathPart = tex.substring(colon + 1);
                }
                // Block textures belong to block atlas regardless of calling model type
                // Item models (like items/oak_sapling.json) may reference block textures (layer0: "minecraft:block/sapling_oak")
                if (pathPart.startsWith("block/") || pathPart.startsWith("blocks/")) {
                    pendingTextures.add(tex);
                } else if (pathPart.startsWith("item/") || pathPart.startsWith("items/")) {
                    pendingItemTextures.add(tex);
                } else if (isItemModel) {
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
            String iconName = toVanillaKey(texturePath, false);
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
            String iconName = toVanillaKey(texturePath, true);
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
        vanillaIcons.clear(); // 世界渲染 UV 回退表与收集循环同步重建
        AtlasPixelCache.clear(); // 资源重载时清空上一轮回读缓存

        int blockCollected = 0, blockMissed = 0;
        java.util.List<IIcon> blockIcons = new java.util.ArrayList<>();
        // Block atlas icons
        for (String texturePath : pendingTextures) {
            String iconName = toVanillaKey(texturePath, false);
            if (iconName != null) {
                IIcon icon = map.getAtlasSprite(iconName);
                if (icon != null) {
                    textureIcons.put(texturePath, icon);
                    vanillaIcons.put(texturePath, icon);
                    blockIcons.add(icon);
                    blockCollected++;
                } else {
                    blockMissed++;
                    CatFrame.logger.warn("[VTT-diag] block IIcon miss: texturePath='{}' → iconName='{}'", texturePath, iconName);
                }
            }
        }
        // Item atlas icons
        int itemCollected = 0, itemMissed = 0;
        java.util.List<IIcon> itemIcons = new java.util.ArrayList<>();
        net.minecraft.client.renderer.texture.TextureMap itemMap =
                (net.minecraft.client.renderer.texture.TextureMap) Minecraft.getMinecraft().getTextureManager()
                        .getTexture(TextureMap.locationItemsTexture);
        if (itemMap != null) {
            for (String texturePath : pendingItemTextures) {
                String iconName = toVanillaKey(texturePath, true);
                if (iconName != null) {
                    IIcon icon = itemMap.getAtlasSprite(iconName);
                    if (icon != null) {
                        textureIcons.put(texturePath, icon);
                        vanillaIcons.put(texturePath, icon);
                        itemIcons.add(icon);
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

        // 发布自定义图集 sprite：必须置于原版 icon 收集循环之后（循环会用 vanilla sprite
        // 覆盖 Pre 阶段产物）、烘焙屏障之前，保证烘焙永远读到 CatSprite UV。
        // Publish CatAtlas sprites AFTER the vanilla collection loops (they would
        // overwrite Pre-stage results) and BEFORE the bake barrier below.
        CatAtlasManager.publish(textureIcons);

        // GPU 回读：在主线程一次性读取图集像素，供异步烘焙线程纯 CPU 读取
        AtlasPixelCache.readAtlas(map, blockIcons);
        if (itemMap != null && !itemIcons.isEmpty()) {
            AtlasPixelCache.readAtlas(itemMap, itemIcons);
        }

        // 不清理 pendingTextures —— 保留数据供 Forge refreshResources 后的第二次 stitch 重新收集
        // 对标高版本 MaterialBaker 实例化闭包模式：iconMap 作为参数传入缓存和烘焙管线
        BakedModelCache.INSTANCE.clear(textureIcons);
        ModelResolver.clearCache();

        CatFrame.logger.info("[VTT-diag] BakedModelCache.clear(iconMap) called | textureIcons.size={}",
                textureIcons.size());
        // 注册懒模型（不执行同步烘焙，烘焙由 AsyncBakePipeline 屏障式预烘焙承担，懒烘焙仅作安全网）
        VanillaModelManager.Baking.registerAllModels();
        // 异步准备，同步切换：并行预烤所有常用模型并阻塞至完成，返回后缓存即就绪（对标 vanilla reload 屏障）
        AsyncBakePipeline.triggerBakeBlocking(textureIcons);
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
        java.util.List<IIcon> itemIcons = new java.util.ArrayList<>();
        for (String texturePath : pendingItemTextures) {
            String iconName = toVanillaKey(texturePath, true);
            if (iconName != null) {
                IIcon icon = itemMap.getAtlasSprite(iconName);
                if (icon != null) {
                    textureIcons.put(texturePath, icon);
                    vanillaIcons.put(texturePath, icon);
                    itemIcons.add(icon);
                }
            }
        }
        // 重新发布自定义图集 sprite：原版 item 收集循环会用 vanilla sprite 覆盖
        // type-0 Post 发布的 CatSprite，此处幂等覆盖回去（烘焙屏障前）。
        // Re-publish CatSprites: the vanilla item loop above overwrote them with
        // vanilla sprites; this idempotent merge restores CatSprite UVs pre-barrier.
        CatAtlasManager.publish(textureIcons);
        // GPU 回读 item atlas（此时 UV 是最终态，覆盖 onTextureStitchPost 时的早期数据）
        AtlasPixelCache.readAtlas(itemMap, itemIcons);
        // 不清理 pendingItemTextures —— 保留数据供多次 stitch 重新收集
        // item iconMap 更新到缓存（懒烘焙时使用）
        BakedModelCache.INSTANCE.clear(textureIcons);
        // [W2 修复] 仅增量更新 item 模型注册（懒模型，无需实际烘焙）
        VanillaModelManager.Baking.registerItemModels();
        // item atlas 就绪后再次并行预烤并阻塞至完成（确保 item 模型也在返回前就绪，零现场烘焙）
        AsyncBakePipeline.triggerBakeBlocking(textureIcons);
    }
}
