package decok.dfcdvadstf.catframe.resources.atlas;

import com.google.common.util.concurrent.ListenableFuture;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <b>数据驱动唯一权威</b>：图集内容完全由 atlas 定义 JSON（{@code atlases/<id>.json} 的
 * {@code sources}）决定 —— 模型引用必须按字面精确命中定义产物（如 {@code minecraft:blocks/ladder}
 * 命中 blocks 图集 directory 扫描），<b>不做任何兼容处理</b>：无单复数目录回退、无别名键、
 * 无模型驱动强制缝合。模型引用未命中定义 → 日志明确报告纹理错误，渲染回退 missingno。
 * <p>
 * <b>缺失语义（与原版一致）</b>：纹理找不到 → missingno（紫黑格），不透明、不自动纠正；
 * 每个图集 stitch 后恒含一个 built-in missing sprite 供查找兜底。
 * <p>
 * <b>失败降级</b>：任一图集构建失败（含 {@code CatStitchException} 超限）→ 捕获并
 * error 日志，该图集不发布、不换绑 —— 原版缝合路径整体兜底，游戏不崩溃。
 * 纹理文件缺失/解码失败 → 映射内置 missing sprite（紫黑 16×16），不崩溃。
 *
 * <p>Stitch orchestration and sprite publication hub. Atlas definitions are the
 * single source of truth: sprite ids come from the {@code atlases/<id>.json}
 * sources verbatim, model references must match exactly or they resolve to
 * missingno with a texture-error report (vanilla missing semantics).
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
    /** 发布映射：数据驱动键（定义产物 sprite id，如 {@code minecraft:blocks/ladder}；含缺失
     *  的原始键与内置 "missingno"）→ CatSprite。publish 时原样合并进 textureIcons，
     *  无任何别名/兼容键 —— 模型引用按字面命中。 */
    private static final Map<String, CatSprite> sprites = new ConcurrentHashMap<>();

    /**
     * 触发全量重建缝合（TextureStitchEvent.Pre type 0 调用）。
     * <p>
     * 先删除上一轮 GL 纹理对象（startGame + refreshResources 两次 stitch 均全量重建），
     * 再分别构建 blocks / items 两个图集；单图集失败独立降级（不发布、不换绑）。
     */
    public static void stitch() {
        release();
        sprites.clear();

        // 硬上限：min(GL_MAX_TEXTURE_SIZE, 16384)（主线程 GL 上下文读取）
        int maxTextureSize = Math.min(GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE), MAX_ATLAS_SIZE);
        // 加载全部图集定义（atlasId → sources），图集内容唯一由此决定
        Map<String, List<AtlasSource>> defs = AtlasDefinitionLoader.loadAll();
        CatFrame.logger.info("[CatAtlas] stitch start | pendingBlk={} pendingItm={} | defs={} | maxTextureSize={}",
                VanillaTextureTracker.getPendingTextures().size(),
                VanillaTextureTracker.getPendingItemTextures().size(),
                defs.keySet(),
                maxTextureSize);

        buildAtlasSafe(BLOCK_ATLAS_ID, GL_BLOCK_ATLAS, maxTextureSize, defs,
                VanillaTextureTracker.getPendingTextures());
        buildAtlasSafe(ITEM_ATLAS_ID, GL_ITEM_ATLAS, maxTextureSize, defs,
                VanillaTextureTracker.getPendingItemTextures());

        CatFrame.logger.info("[CatAtlas] stitch done | sprites={} | atlases={}",
                sprites.size(), atlases.keySet());
    }

    /**
     * 单图集构建：定义驱动收集 → 缺失报告 → 并行解码 → 布局 + 上传 →
     * TextureManager 注册 → 渲染分组换绑 → 收集发布映射。
     * 任一环节异常 → error 日志并整体跳过该图集（原版绑定与原版 sprite 路径兜底）。
     */
    private static void buildAtlasSafe(String atlasId, ResourceLocation glName, int maxTextureSize,
                                       Map<String, List<AtlasSource>> defs,
                                       Collection<String> pendingRefs) {
        try {
            // 1) 定义驱动收集：atlas JSON 是唯一权威，sprite id 原样采用
            List<SpriteRef> refs = collectRefs(atlasId, defs);
            // 2) 模型引用缺失报告：未命中定义产物的引用 → 纹理错误（渲染回退 missingno）
            reportMissingRefs(pendingRefs, refs, atlasId);

            List<CatSprite> decoded = decodeAll(refs, atlasId);
            int missing = 0;
            for (CatSprite sprite : decoded) {
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
            // 3) 图集恒含 built-in missing sprite：缺失查找最终兜底（键 "missingno"），
            //    与原版 TextureMap 的 missingImage 语义对齐。
            //    Every atlas keeps a built-in missing sprite so any unresolved
            //    lookup lands on missingno (vanilla semantics).
            boolean hasMissing = false;
            for (CatSprite sprite : decoded) {
                if (sprite.isMissing()) {
                    hasMissing = true;
                    break;
                }
            }
            if (!hasMissing) {
                decoded.add(CatSprite.missing(atlasId, CatSprite.MISSING_NAME));
            }

            CatAtlas atlas = new CatAtlas(atlasId);
            atlas.stitch(decoded, maxTextureSize);

            // 注册到 TextureManager：渲染层按 catframe:atlas/<id> 绑定
            Minecraft.getMinecraft().getTextureManager().loadTexture(glName, atlas);
            // 上传成功后换绑内建渲染分组（register 的重复 id 走 update 语义，结构零改动）
            rebindRenderGroups(atlasId);

            // 发布表键 = 数据驱动键（sprite 的 texturePath，含缺失的原始键与 "missingno"），
            // 无别名键 —— 模型引用必须按字面命中，未命中走 findIcon 兜底 missingno。
            for (CatSprite sprite : atlas.getSprites().values()) {
                sprites.put(sprite.getTexturePath(), sprite);
            }
            atlases.put(atlasId, atlas);
        } catch (RuntimeException e) {
            CatFrame.logger.error("[CatAtlas] atlas '{}' build failed, falling back to vanilla path: {}",
                    atlasId, e.getMessage(), e);
        }
    }

    /**
     * 定义驱动收集：atlas JSON 的 sources 按序产出 sprite ref，id 原样采用（数据驱动键）。
     * <ol>
     *   <li>filter 源命中 → 移除（仅作用于定义驱动集合）；</li>
     *   <li>重复 sprite id → warn + 跳过（先入者胜，同 pack 覆盖语义）；</li>
     *   <li>SpriteRef 的 atlasId 覆盖字段仅 debug 记录（CatFrame 渲染分组只绑定
     *       blocks/items 两个图集）。</li>
     * </ol>
     * 模型驱动 pending 引用<b>不参与</b>图集内容（数据驱动唯一权威），只用于缺失报告。
     */
    private static List<SpriteRef> collectRefs(String atlasId, Map<String, List<AtlasSource>> defs) {
        LinkedHashMap<String, SpriteRef> merged = new LinkedHashMap<>();
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
                        continue;
                    }
                    if (merged.containsKey(spriteId)) {
                        CatFrame.logger.warn(
                                "[CatAtlas] duplicate sprite '{}' in atlas '{}': earlier entry wins, later skipped",
                                spriteId, atlasId);
                        continue;
                    }
                    if (ref.atlasId() != null && !atlasId.equals(ref.atlasId().toString())) {
                        CatFrame.logger.debug("[CatAtlas] sprite '{}' targets atlas '{}' != current '{}', "
                                        + "kept in current atlas (CatFrame binds only blocks/items)",
                                spriteId, ref.atlasId(), atlasId);
                    }
                    merged.put(spriteId, ref);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 模型引用缺失报告：pending 中的引用未命中当前图集定义产物 → 纹理错误。
     * 引用必须按字面匹配数据驱动键（不做单复数/别名兼容）；未命中渲染回退 missingno。
     */
    private static void reportMissingRefs(Collection<String> pendingRefs, List<SpriteRef> refs,
                                          String atlasId) {
        if (pendingRefs.isEmpty()) {
            return;
        }
        Set<String> defined = new HashSet<>();
        for (SpriteRef ref : refs) {
            defined.add(ref.spriteId().toString());
        }
        for (String texturePath : pendingRefs) {
            if (!defined.contains(texturePath)) {
                CatFrame.logger.warn(
                        "[CatAtlas] texture error: '{}' referenced by models but not defined in atlas '{}' "
                                + "sources; will render missingno",
                        texturePath, atlasId);
            }
        }
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
     * 发布键即数据驱动键，无别名 —— 模型引用按字面命中；未命中走 findIcon 兜底 missingno。
     */
    public static void publish(Map<String, IIcon> textureIcons) {
        if (sprites.isEmpty()) {
            return;
        }
        textureIcons.putAll(sprites);
        CatFrame.logger.info("[CatAtlas] published {} CatSprites into textureIcons (size={})",
                sprites.size(), textureIcons.size());
    }

    /**
     * 按数据驱动键（定义产物 sprite id，如 {@code minecraft:blocks/ladder}）查找当前
     * stitch 周期的 CatSprite；未缝合或键不存在时返回 null。
     * <p>
     * 供烘焙/渲染侧在 {@code globalIconMap} 未命中时优先取 CatSprite，防止降级到
     * vanilla sprite（原版图集 UV 空间）与物品渲染的 CatAtlas 绑定错配。
     *
     * <p>Lookup by the data-driven key; baking never falls back to a vanilla
     * sprite (vanilla atlas UV space) while item rendering binds the CatAtlas.
     */
    @Nullable
    public static CatSprite findSprite(String texturePath) {
        if (texturePath == null) return null;
        return sprites.get(texturePath);
    }

    /**
     * 缺失查找最终兜底：返回纹理所属图集的 built-in missing sprite（紫黑格，missingno）。
     * <p>
     * 图集归属按路径前缀判定（{@code items/} / {@code item/} → items 图集，其余 → blocks）；
     * 图集未构建（降级）时返回原版 missingImage —— 与原版 {@code TextureMap.getAtlasSprite}
     * 的缺失语义一致：<b>纹理找不着 → missingno</b>。
     *
     * <p>Final fallback for unresolved textures: the owning atlas's built-in
     * missing sprite; when the atlas was not built, the vanilla missing image.
     *
     * @return missingno icon（CatSprite 或原版 missingImage）；极端情况下可为 null
     */
    @Nullable
    public static IIcon getMissingIcon(String texturePath) {
        String atlasId = atlasIdFor(texturePath);
        CatAtlas atlas = atlases.get(atlasId);
        if (atlas != null && atlas.getMissingSprite() != null) {
            return atlas.getMissingSprite();
        }
        // 图集未构建/无缺失 sprite（降级路径）→ 原版 missingImage（1.7.10 恒注册 "missingno"）
        try {
            return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(CatSprite.MISSING_NAME);
        } catch (Exception e) {
            return null;
        }
    }

    /** 纹理引用 → 图集 id：items/ item/ 前缀 → items 图集，其余 → blocks 图集。 */
    private static String atlasIdFor(@Nullable String texturePath) {
        if (texturePath == null) {
            return BLOCK_ATLAS_ID;
        }
        int colon = texturePath.indexOf(':');
        String path = colon >= 0 ? texturePath.substring(colon + 1) : texturePath;
        return (path.startsWith("items/") || path.startsWith("item/"))
                ? ITEM_ATLAS_ID : BLOCK_ATLAS_ID;
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
        return sprites.size();
    }
}
