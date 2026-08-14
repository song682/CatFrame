package decok.dfcdvadstf.catframe.resources.atlas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CatFrame 纹理图集管理器 —— 缝合编排与 sprite 发布中枢（对标 26.1.2
 * {@code SpriteLoader} + {@code TextureAtlas} 生命周期，适配 1.7.10 同步点）。
 * <p>
 * M1 生命周期（两次 stitch：startGame + refreshResources，全量重建）：
 * <ol>
 *   <li>{@link #stitch()} 在 {@code TextureStitchEvent.Pre(type 0)} 的
 *       {@code ModelManagerDataLoader.init()} 之后调用：先 {@link #release()} 删除上一轮
 *       GL 纹理对象（防泄漏），再消费现有收集循环产物
 *       {@code pendingTextures} / {@code pendingItemTextures}（模型驱动的纹理名称），
 *       按图集分别完成 解码 → 布局 → CPU 组装 + GL 上传 → TextureManager 注册
 *       （{@code catframe:atlas/blocks|items}）→ 渲染分组换绑；</li>
 *   <li>{@link #publish(Map)} 在 {@code TextureStitchEvent.Post} 的原版 icon 收集循环
 *       之后调用，把 {@code texturePath → CatSprite} 合并进现有
 *       {@code textureIcons}（幂等覆盖，保证烘焙永远读到 CatSprite UV），
 *       烘焙屏障（BakedModelCache.clear + AsyncBakePipeline.triggerBakeBlocking）
 *       由既有 Post 流程原样触发，本类绝不自行烘焙；</li>
 * </ol>
 * <p>
 * <b>失败降级</b>：任一图集构建失败（含 {@code CatStitchException} 超限）→ 捕获并
 * error 日志，该图集不发布、不换绑 —— 原版缝合路径整体兜底，游戏不崩溃。
 * 纹理文件缺失/解码失败 → 映射内置 missing sprite（紫黑 16×16），不崩溃。
 * <p>
 * M1 输入为模型驱动集合；M2 起并入 {@code AtlasDecoder} 定义驱动源
 * （directory/filter/single/unstitch），解码逻辑届时迁入独立
 * {@code CatSpriteLoader}（{@link RenderExecutors} 并行池）。
 *
 * <p>Stitch orchestration and sprite publication hub. Consumes the existing
 * model-driven pending sets, uploads two custom atlases (blocks/items) and
 * merges the resulting CatSprites into {@code textureIcons} before the existing
 * bake barrier; per-atlas failure degrades to the vanilla path.
 */
@SideOnly(Side.CLIENT)
public final class CatAtlasManager {

    /** blocks 图集 id（IAtlas.getAtlasName 语义）。 */
    public static final String BLOCK_ATLAS_ID = "minecraft:block";
    /** items 图集 id。 */
    public static final String ITEM_ATLAS_ID = "minecraft:item";
    /** blocks 图集在 TextureManager 中的注册名（渲染层绑定用）。 */
    public static final ResourceLocation GL_BLOCK_ATLAS = new ResourceLocation("catframe", "atlas/blocks");
    /** items 图集在 TextureManager 中的注册名。 */
    public static final ResourceLocation GL_ITEM_ATLAS = new ResourceLocation("catframe", "atlas/items");
    /** 图集尺寸硬上限（与设计文档一致；实际上限 = min(GL_MAX_TEXTURE_SIZE, 16384)）。 */
    private static final int MAX_ATLAS_SIZE = 16384;

    private CatAtlasManager() {
    }

    /** 当前 stitch 周期的图集：atlasId → CatAtlas。 */
    private static final Map<String, CatAtlas> atlases = new LinkedHashMap<>();
    /** 发布映射：完整纹理路径 → CatSprite（publish 时合并进 textureIcons）。 */
    private static final Map<String, CatSprite> spritesByTexturePath = new ConcurrentHashMap<>();

    /**
     * 触发全量重建缝合（TextureStitchEvent.Pre type 0 调用）。
     * <p>
     * 先删除上一轮 GL 纹理对象（startGame + refreshResources 两次 stitch 均全量重建），
     * 再分别构建 blocks / items 两个图集；单图集失败独立降级（不发布、不换绑）。
     */
    public static void stitch() {
        release();
        spritesByTexturePath.clear();

        // 硬上限：min(GL_MAX_TEXTURE_SIZE, 16384)（主线程 GL 上下文读取）
        int maxTextureSize = Math.min(GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE), MAX_ATLAS_SIZE);
        CatFrame.logger.info("[CatAtlas] stitch start | pendingBlk={} pendingItm={} | maxTextureSize={}",
                VanillaTextureTracker.getPendingTextures().size(),
                VanillaTextureTracker.getPendingItemTextures().size(),
                maxTextureSize);

        buildAtlasSafe(VanillaTextureTracker.getPendingTextures(), BLOCK_ATLAS_ID, GL_BLOCK_ATLAS, maxTextureSize);
        buildAtlasSafe(VanillaTextureTracker.getPendingItemTextures(), ITEM_ATLAS_ID, GL_ITEM_ATLAS, maxTextureSize);

        CatFrame.logger.info("[CatAtlas] stitch done | sprites={} | atlases={}",
                spritesByTexturePath.size(), atlases.keySet());
    }

    /**
     * 单图集构建：解码 → 布局 + 上传 → TextureManager 注册 → 渲染分组换绑 → 收集发布映射。
     * 任一环节异常 → error 日志并整体跳过该图集（原版绑定与原版 sprite 路径兜底）。
     */
    private static void buildAtlasSafe(Collection<String> pending, String atlasId,
                                       ResourceLocation glName, int maxTextureSize) {
        try {
            List<CatSprite> sprites = new ArrayList<>(pending.size());
            for (String texturePath : pending) {
                sprites.add(decodeSprite(texturePath, atlasId));
            }
            CatAtlas atlas = new CatAtlas(atlasId);
            atlas.stitch(sprites, maxTextureSize);

            // 注册到 TextureManager：渲染层按 catframe:atlas/<id> 绑定
            Minecraft.getMinecraft().getTextureManager().loadTexture(glName, atlas);
            // 上传成功后换绑内建渲染分组（register 的重复 id 走 update 语义，结构零改动）
            rebindRenderGroups(atlasId);

            for (CatSprite sprite : atlas.getSprites().values()) {
                spritesByTexturePath.put(sprite.getTexturePath(), sprite);
            }
            atlases.put(atlasId, atlas);
        } catch (RuntimeException e) {
            CatFrame.logger.error("[CatAtlas] atlas '{}' build failed, falling back to vanilla path: {}",
                    atlasId, e.getMessage(), e);
        }
    }

    /**
     * 解码单个纹理路径为 CatSprite。
     * <p>
     * 1.7.10 纹理文件夹为复数（{@code textures/blocks/}、{@code textures/items/}），
     * 单数前缀（{@code block/}、{@code item/}）先试复数目录，失败回退单数目录；
     * 全部失败 → 内置 missing sprite（warn，不崩溃）。
     * <p>
     * 解码用 {@code ImageIO.read}（1.7.10 {@code TextureUtil} 无 readBufferedImage，
     * 原版 TextureMap 亦用 ImageIO），M2 迁 CatSpriteLoader + RenderExecutors 并行池时
     * 本方法签名保持不变。
     */
    private static CatSprite decodeSprite(String texturePath, String atlasId) {
        String namespace = "minecraft";
        String path = texturePath;
        int colon = texturePath.indexOf(':');
        if (colon >= 0) {
            namespace = texturePath.substring(0, colon);
            path = texturePath.substring(colon + 1);
        }

        String iconName = VanillaModelManager.Utilities.resolveTextureName(texturePath);
        if (iconName == null || iconName.isEmpty()) {
            return CatSprite.missing(atlasId, texturePath);
        }

        boolean item = path.startsWith("item/") || path.startsWith("items/");
        String name = path.substring(path.indexOf('/') + 1);
        // 1.7.10 复数目录优先（textures/blocks|items），单数目录回退
        String[] folders = item ? new String[]{"items", "item"} : new String[]{"blocks", "block"};

        for (String folder : folders) {
            ResourceLocation rl = new ResourceLocation(namespace, "textures/" + folder + "/" + name + ".png");
            try {
                IResource res = Minecraft.getMinecraft().getResourceManager().getResource(rl);
                BufferedImage image = ImageIO.read(res.getInputStream());
                if (image == null) {
                    continue; // 资源存在但非可解码图像
                }
                int w = image.getWidth();
                int h = image.getHeight();
                int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
                return new CatSprite(texturePath, iconName, pixels, w, h, atlasId);
            } catch (IOException | RuntimeException ex) {
                // 尝试下一个目录
            }
        }
        CatFrame.logger.warn("[CatAtlas] texture '{}' not found for atlas '{}', using missing sprite",
                texturePath, atlasId);
        return CatSprite.missing(atlasId, texturePath);
    }

    /**
     * 上传成功后把内建渲染分组换绑到本图集（register 重复 id 走 update 语义）。
     * blocks 系 3 组 + items 系 2 组；sortKey 与 blend 保持不变。
     */
    private static void rebindRenderGroups(String atlasId) {
        if (BLOCK_ATLAS_ID.equals(atlasId)) {
            RenderTypeRegistry.register("block_atlas_solid", GL_BLOCK_ATLAS,
                    false, RenderTypeRegistry.SORT_BLOCK_SOLID);
            RenderTypeRegistry.register("block_atlas_translucent", GL_BLOCK_ATLAS,
                    true, RenderTypeRegistry.SORT_BLOCK_TRANSLUCENT);
            RenderTypeRegistry.register("block_atlas_destroy", GL_BLOCK_ATLAS,
                    false, RenderTypeRegistry.SORT_BLOCK_DESTROY);
            CatFrame.logger.info("[CatAtlas] render groups rebound to {}", GL_BLOCK_ATLAS);
        } else if (ITEM_ATLAS_ID.equals(atlasId)) {
            RenderTypeRegistry.register("item_atlas_solid", GL_ITEM_ATLAS,
                    false, RenderTypeRegistry.SORT_ITEM_SOLID);
            RenderTypeRegistry.register("item_atlas_translucent", GL_ITEM_ATLAS,
                    true, RenderTypeRegistry.SORT_ITEM_TRANSLUCENT);
            CatFrame.logger.info("[CatAtlas] render groups rebound to {}", GL_ITEM_ATLAS);
        }
    }

    /**
     * 把本轮 stitch 产物合并进 textureIcons（幂等覆盖，可重复调用）。
     * <p>
     * 调用时机：type 0 Post 与 type 1 Post 的<b>原版 icon 收集循环之后</b> ——
     * 原版循环会用 vanilla sprite 覆盖 Pre 阶段产物，故 publish 必须置于其后、
     * 烘焙屏障（BakedModelCache.clear）之前，保证烘焙永远读到 CatSprite UV。
     */
    public static void publish(Map<String, IIcon> textureIcons) {
        if (spritesByTexturePath.isEmpty()) {
            return;
        }
        textureIcons.putAll(spritesByTexturePath);
        CatFrame.logger.info("[CatAtlas] published {} CatSprites into textureIcons (size={})",
                spritesByTexturePath.size(), textureIcons.size());
    }

    /**
     * 删除全部图集的 GL 纹理对象（两次 stitch 之间的重建入口，防 GL 泄漏）。
     */
    private static void release() {
        for (CatAtlas atlas : atlases.values()) {
            atlas.deleteGlTexture();
        }
        atlases.clear();
    }

    /**
     * 供诊断/测试使用：当前已构建的图集数量。
     */
    public static int atlasCount() {
        return atlases.size();
    }

    /**
     * 供诊断/测试使用：当前待发布 sprite 数量。
     */
    public static int spriteCount() {
        return spritesByTexturePath.size();
    }
}
