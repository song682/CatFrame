package decok.dfcdvadstf.catframe.resources.atlas;

import com.google.common.util.concurrent.ListenableFuture;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import decok.dfcdvadstf.catframe.model.core.async.RenderExecutors;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderTypeRegistry;
import decok.dfcdvadstf.catframe.resources.atlas.source.AtlasSource;
import decok.dfcdvadstf.catframe.resources.atlas.source.FilterSource;
import decok.dfcdvadstf.catframe.resources.atlas.source.SpriteRef;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * CatFrame 纹理图集管理器 —— 缝合编排与 sprite 发布中枢（对标 26.1.2
 * {@code SpriteLoader} + {@code TextureAtlas} 生命周期，适配 1.7.10 同步点）。
 * <p>
 * M1 生命周期（两次 stitch：startGame + refreshResources，全量重建）：
 * <ol>
 *   <li>{@link #stitch()} 在 {@code TextureStitchEvent.Pre(type 0)} 的
 *       {@code ModelManagerDataLoader.init()} 之后调用：先 {@link #release()} 删除上一轮
 *       GL 纹理对象（防泄漏），加载图集定义（{@link AtlasDefinitionLoader}），再消费
 *       收集循环产物与定义驱动源的<b>合并结果</b>（模型驱动权威 + 定义驱动补全），
 *       按图集分别完成 并行解码 → 布局 → CPU 组装 + GL 上传 → TextureManager 注册
 *       （{@code catframe:atlas/blocks|items}）→ 渲染分组换绑；</li>
 *   <li>{@link #publish(Map)} 在 {@code TextureStitchEvent.Post} 的原版 icon 收集循环
 *       之后调用，把 {@code texturePath → CatSprite} 合并进现有
 *       {@code textureIcons}（幂等覆盖，保证烘焙永远读到 CatSprite UV），
 *       烘焙屏障（BakedModelCache.clear + AsyncBakePipeline.triggerBakeBlocking）
 *       由既有 Post 流程原样触发，本类绝不自行烘焙；</li>
 *   <li>{@link #tickAnimations()} 每客户端 tick 驱动动画 sprite 帧推进与区域重传
 *       （ClientOverlayHandler 调用，游戏暂停时跳过）。</li>
 * </ol>
 * <p>
 * <b>M2 合并规则</b>（设计文档确认）：模型驱动引用（pending 集合）先入且去重，
 * 定义驱动源（directory/filter/single/unstitch/paletted_permutations）随后补全 ——
 * 同 iconName 冲突时模型胜（模型是渲染权威）；filter 仅作用于定义驱动集合；
 * 定义驱动 sprite 的 iconName 以 {@code resolveTextureName}（剥 prefix）对齐。
 * <p>
 * <b>失败降级</b>：任一图集构建失败（含 {@code CatStitchException} 超限）→ 捕获并
 * error 日志，该图集不发布、不换绑 —— 原版缝合路径整体兜底，游戏不崩溃。
 * 纹理文件缺失/解码失败 → 映射内置 missing sprite（紫黑 16×16），不崩溃。
 *
 * <p>Stitch orchestration and sprite publication hub. Consumes the existing
 * model-driven pending sets merged with definition-driven sources, uploads two
 * custom atlases (blocks/items) and merges the resulting CatSprites into
 * {@code textureIcons} before the existing bake barrier; per-atlas failure
 * degrades to the vanilla path.
 */
@SideOnly(Side.CLIENT)
public final class CatAtlasManager {

    /** blocks 图集 id（IAtlas.getAtlasName 语义；对齐 Wiki：原版定义文件
     * {@code assets/minecraft/atlases/blocks.json} → 图集 id {@code minecraft:blocks}）。 */
    public static final String BLOCK_ATLAS_ID = "minecraft:blocks";
    /** items 图集 id（对齐 Wiki：{@code atlases/items.json} → {@code minecraft:items}）。 */
    public static final String ITEM_ATLAS_ID = "minecraft:items";
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
    /** icon 名称（resolveTextureName 剥前缀结果，如 {@code minecraft:stone}）→ CatSprite，
     *  供 {@link #findSprite} 反查（烘焙侧防 vanilla sprite 泄漏）。 */
    private static final Map<String, CatSprite> spritesByIconName = new ConcurrentHashMap<>();

    /**
     * 触发全量重建缝合（TextureStitchEvent.Pre type 0 调用）。
     * <p>
     * 先删除上一轮 GL 纹理对象（startGame + refreshResources 两次 stitch 均全量重建），
     * 再分别构建 blocks / items 两个图集；单图集失败独立降级（不发布、不换绑）。
     */
    public static void stitch() {
        release();
        spritesByTexturePath.clear();
        spritesByIconName.clear();

        // 硬上限：min(GL_MAX_TEXTURE_SIZE, 16384)（主线程 GL 上下文读取）
        int maxTextureSize = Math.min(GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE), MAX_ATLAS_SIZE);
        // M2：加载全部图集定义（atlasId → sources），供合并规则消费
        Map<String, List<AtlasSource>> defs = AtlasDefinitionLoader.loadAll();
        CatFrame.logger.info("[CatAtlas] stitch start | pendingBlk={} pendingItm={} | defs={} | maxTextureSize={}",
                VanillaTextureTracker.getPendingTextures().size(),
                VanillaTextureTracker.getPendingItemTextures().size(),
                defs.keySet(),
                maxTextureSize);

        buildAtlasSafe(VanillaTextureTracker.getPendingTextures(), BLOCK_ATLAS_ID, GL_BLOCK_ATLAS, maxTextureSize, defs);
        buildAtlasSafe(VanillaTextureTracker.getPendingItemTextures(), ITEM_ATLAS_ID, GL_ITEM_ATLAS, maxTextureSize, defs);

        CatFrame.logger.info("[CatAtlas] stitch done | sprites={} | atlases={}",
                spritesByTexturePath.size(), atlases.keySet());
    }

    /**
     * 单图集构建：合并（模型权威 + 定义补全）→ 并行解码 → 布局 + 上传 →
     * TextureManager 注册 → 渲染分组换绑 → 收集发布映射。
     * 任一环节异常 → error 日志并整体跳过该图集（原版绑定与原版 sprite 路径兜底）。
     */
    private static void buildAtlasSafe(Collection<String> pending, String atlasId,
                                       ResourceLocation glName, int maxTextureSize,
                                       Map<String, List<AtlasSource>> defs) {
        try {
            List<SpriteRef> refs = mergeRefs(pending, atlasId, defs);
            List<CatSprite> sprites = decodeAll(refs, atlasId);
            int missing = 0;
            for (CatSprite sprite : sprites) {
                if (sprite.isMissing()) {
                    missing++;
                }
            }
            // 降级保护：decode 大面积失败（missing 过半）说明解码环境异常，整体跳过本图集
            // （不注册、不换绑、不发布）→ 原版缝合路径兜底，画面保持原版正常，游戏不崩溃。
            // Degrade guard: if most sprites failed to decode, skip the whole atlas
            // so the vanilla stitch path and vanilla UV space stay in effect.
            if (missing * 2 > refs.size()) {
                CatFrame.logger.error(
                        "[CatAtlas] atlas '{}' decode failed {}/{} sprites (>50%), skipping atlas (vanilla path fallback)",
                        atlasId, missing, refs.size());
                return;
            }
            CatAtlas atlas = new CatAtlas(atlasId);
            atlas.stitch(sprites, maxTextureSize);

            // 注册到 TextureManager：渲染层按 catframe:atlas/<id> 绑定
            Minecraft.getMinecraft().getTextureManager().loadTexture(glName, atlas);
            // 上传成功后换绑内建渲染分组（register 的重复 id 走 update 语义，结构零改动）
            rebindRenderGroups(atlasId);

            for (CatSprite sprite : atlas.getSprites().values()) {
                spritesByTexturePath.put(sprite.getTexturePath(), sprite);
                spritesByIconName.put(sprite.getIconName(), sprite);
            }
            atlases.put(atlasId, atlas);
        } catch (RuntimeException e) {
            CatFrame.logger.error("[CatAtlas] atlas '{}' build failed, falling back to vanilla path: {}",
                    atlasId, e.getMessage(), e);
        }
    }

    /**
     * M2 合并规则：模型驱动权威 + 定义驱动补全（设计文档确认）。
     * <p>
     * 合并键 = iconName（{@code resolveTextureName} 剥前缀结果，如 {@code minecraft:stone}）：
     * <ol>
     *   <li>模型驱动引用（pending）先入，重复 iconName 忽略后者（集合已去重，防御性）；</li>
     *   <li>定义驱动源按序产出 SpriteRef：filter（仅作用于定义驱动集合）命中 → 跳过；
     *       与已有 iconName 冲突 → warn + 跳过（模型/先入者胜）；否则追加。</li>
     * </ol>
     * 定义驱动 sprite 的图集归属即当前图集（atlas/{id}.json 的 id）；SpriteRef 的
     * atlasId 覆盖字段仅 debug 记录（CatFrame 渲染分组只绑定 blocks/items 两个图集）。
     */
    private static List<SpriteRef> mergeRefs(Collection<String> pending, String atlasId,
                                             Map<String, List<AtlasSource>> defs) {
        LinkedHashMap<String, SpriteRef> merged = new LinkedHashMap<>();
        // 1) 模型驱动先入（权威）：texturePath → SpriteRef（resource = spriteId）
        for (String texturePath : pending) {
            String icon = VanillaModelManager.Utilities.resolveTextureName(texturePath);
            if (icon == null || icon.isEmpty()) {
                continue;
            }
            merged.putIfAbsent(icon, SpriteRef.of(new ResourceLocation(texturePath)));
        }
        // 2) 定义驱动补全：filter 移除 + 重复警告 + 先入者胜
        List<AtlasSource> sources = defs.get(atlasId);
        if (sources != null) {
            for (AtlasSource source : sources) {
                List<SpriteRef> refs;
                try {
                    refs = source.list(Minecraft.getMinecraft().getResourceManager());
                } catch (RuntimeException e) {
                    CatFrame.logger.warn("[CatAtlas] source '{}' in atlas '{}' failed, skipping: {}",
                            source.type(), atlasId, e.getMessage());
                    continue;
                }
                for (SpriteRef ref : refs) {
                    String spriteId = ref.spriteId().toString();
                    if (isFiltered(sources, spriteId)) {
                        continue; // filter 仅作用于定义驱动集合（模型引用是渲染必需）
                    }
                    String icon = VanillaModelManager.Utilities.resolveTextureName(spriteId);
                    if (icon == null || icon.isEmpty()) {
                        icon = spriteId;
                    }
                    if (merged.containsKey(icon)) {
                        CatFrame.logger.warn(
                                "[CatAtlas] duplicate sprite '{}' in atlas '{}': model/earlier entry wins, definition entry skipped",
                                icon, atlasId);
                        continue;
                    }
                    if (ref.atlasId() != null && !atlasId.equals(ref.atlasId().toString())) {
                        CatFrame.logger.debug("[CatAtlas] sprite '{}' targets atlas '{}' != current '{}', "
                                        + "kept in current atlas (CatFrame binds only blocks/items)",
                                spriteId, ref.atlasId(), atlasId);
                    }
                    merged.put(icon, ref);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** 定义驱动集合中是否被任一 filter 源命中移除。 */
    private static boolean isFiltered(List<AtlasSource> sources, String spriteId) {
        for (AtlasSource source : sources) {
            if (source instanceof FilterSource && source.shouldRemove(spriteId)) {
                CatFrame.logger.debug("[CatAtlas] filter removed '{}'", spriteId);
                return true;
            }
        }
        return false;
    }

    /**
     * 并行解码（RenderExecutors 共享池）：每个 SpriteRef 提交 CatSpriteLoader.load，
     * 按提交顺序收集结果；失败（返回 null 或异常）→ 内置 missing sprite（warn，不崩溃）。
     */
    private static List<CatSprite> decodeAll(List<SpriteRef> refs, String atlasId) {
        List<CatSprite> sprites = new ArrayList<>(refs.size());
        if (refs.isEmpty()) {
            return sprites;
        }
        List<ListenableFuture<CatSprite>> futures = new ArrayList<>(refs.size());
        for (SpriteRef ref : refs) {
            futures.add(RenderExecutors.get().submit(() -> CatSpriteLoader.load(
                    ref.resource(), ref.spriteId().toString(), atlasId, ref.transform())));
        }
        for (int i = 0; i < futures.size(); i++) {
            String spriteId = refs.get(i).spriteId().toString();
            try {
                CatSprite sprite = futures.get(i).get();
                if (sprite == null) {
                    CatFrame.logger.warn("[CatAtlas] sprite '{}' decode returned null, using missing", spriteId);
                    sprites.add(CatSprite.missing(atlasId, spriteId));
                } else {
                    sprites.add(sprite);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CatFrame.logger.warn("[CatAtlas] sprite '{}' decode interrupted, using missing", spriteId);
                sprites.add(CatSprite.missing(atlasId, spriteId));
            } catch (ExecutionException e) {
                CatFrame.logger.warn("[CatAtlas] sprite '{}' decode failed ({}), using missing",
                        spriteId, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                sprites.add(CatSprite.missing(atlasId, spriteId));
            }
        }
        return sprites;
    }

    /**
     * M3 动画 tick：遍历全部图集的动画 sprite，帧切换后重传对应图集区域。
     * 由 ClientOverlayHandler 在客户端 tick（非暂停）调用，主线程执行。
     */
    public static void tickAnimations() {
        for (CatAtlas atlas : atlases.values()) {
            for (CatSprite sprite : atlas.getSprites().values()) {
                if (sprite.isAnimated() && sprite.updateAnimationTick()) {
                    atlas.updateAnimationRegion(sprite);
                }
            }
        }
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
        // 别名键：iconName（resolveTextureName 结果，如 "minecraft:stone"）也指向同一
        // CatSprite，配合 TextureSlots.findIcon 的别名查询，防止纹理引用格式差异时
        // 第二级 fallback 到 vanilla sprite（UV 原版图集空间）与换绑后的 CatAtlas 错配。
        // Alias keys: iconName also maps to the same CatSprite so findIcon's alias
        // lookup never falls back to a vanilla sprite in the vanilla UV space.
        for (CatSprite sprite : spritesByTexturePath.values()) {
            String iconName = sprite.getIconName();
            if (iconName != null && !iconName.isEmpty()) {
                textureIcons.put(iconName, sprite);
            }
        }
        CatFrame.logger.info("[CatAtlas] published {} CatSprites into textureIcons (size={})",
                spritesByTexturePath.size(), textureIcons.size());
    }

    /**
     * 按 icon 名称（resolveTextureName 剥前缀结果，如 {@code minecraft:stone}）查找
     * 当前 stitch 周期的 CatSprite；未缝合或不存在时返回 null。
     * <p>
     * 供烘焙/渲染侧在 {@code globalIconMap} 未命中时优先取 CatSprite，防止降级到
     * vanilla sprite（原版图集 UV 空间）与物品渲染的 CatAtlas 绑定错配。
     *
     * <p>Lookup by stripped icon name so baking never falls back to a vanilla
     * sprite (vanilla atlas UV space) while item rendering binds the CatAtlas.
     */
    @Nullable
    public static CatSprite findSprite(String iconName) {
        if (iconName == null) return null;
        return spritesByIconName.get(iconName);
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
