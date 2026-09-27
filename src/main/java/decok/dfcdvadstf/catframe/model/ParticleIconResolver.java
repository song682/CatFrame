package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.model.render.RenderJsonBlockModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.block.StateProviderBlockModel;
import decok.dfcdvadstf.catframe.resources.atlas.CatAtlasManager;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方块破坏 / hit / blockdust 粒子的模型驱动纹理解析入口。
 * <p>
 * 对标 26.1.2 的 {@code BlockStateModelSet#getParticleMaterial(state)}：按
 * {@code (block, metadata)} 解析 CatFrame 接管方块的粒子纹理，语义与高版本一致 ——
 * 显式 textures.particle 槽优先；槽缺失/无法解析时直接映射 missingno（紫黑格，
 * 对标 {@code MaterialBaker#reportMissingReference} → {@code blockMissing}），
 * 不猜测替代纹理。
 * <p>
 * 不取世界/坐标上下文（粒子生成时方块可能已被移除；26.1.2 亦为 per-state）。
 * 解析结果按 {@code (blockId, meta)} 缓存：一次破坏会生成 64 个粒子，
 * 逐粒子跑全链过热。仅缓存非 null 结果 —— 加载期模型未就绪不得污染缓存
 * （与 BakedModelCache 烘焙失败不缓存的语义一致）；资源重载时随
 * {@link BakedModelCache#clear} 一同 {@link #clear()}。
 * <p>
 * Model-driven particle icon resolution for destroy / hit / block-dust particles,
 * mirroring 26.1.2 {@code BlockStateModelSet#getParticleMaterial}. Resolution is
 * per {@code (block, metadata)} with no world context; positive results are cached.
 */
@SideOnly(Side.CLIENT)
public final class ParticleIconResolver {

    private ParticleIconResolver() {
    }

    /** (blockId << 16 | meta & 0xFFFF) → 已解析的粒子 IIcon。仅非 null 结果入缓存。 */
    private static final Map<Long, IIcon> CACHE = new ConcurrentHashMap<>();

    /**
     * 解析方块的粒子纹理（模型驱动）。
     * <p>
     * 接管方块的槽缺失/无法解析按高版本语义映射 missingno；仅未接管方块
     * （或 missingno 极端不可得）返回 null —— 调用方放行原版
     * {@code block.getIcon(side, meta)}。
     *
     * @param block    方块实例
     * @param metadata 方块 metadata
     * @return 粒子用的 IIcon，可为 null
     */
    @Nullable
    public static IIcon getParticleIcon(Block block, int metadata) {
        if (block == null) return null;

        long key = ((long) Block.getIdFromBlock(block) << 16) | (metadata & 0xFFFF);
        IIcon cached = CACHE.get(key);
        if (cached != null) return cached;

        IIcon icon = resolve(block, metadata);
        // 仅缓存非 null：加载期模型尚未烘焙就绪时不得记忆"无粒子"（与 BakedModelCache 失败不缓存同语义）。
        // Positive results only — a transient miss (models not baked yet) must not be memoized.
        if (icon != null) {
            CACHE.put(key, icon);
        }
        return icon;
    }

    /**
     * 提取部件的显式 particle 槽图标（null = 槽缺失或部件不可解析）。
     * <p>
     * 高版本语义下不存在替代猜想 —— 返回 null 由 {@link #getParticleIcon} 统一映射
     * missingno。{@link BlockStateModelPart#particleIcon()}（首 quad）为既有保留项，
     * 已不再接入本语义链。
     * Extracts the explicit particle-slot icon only; null is mapped to missingno
     * by {@link #getParticleIcon} (26.1.2 semantics, no substitute guessing).
     */
    @Nullable
    public static IIcon fromPart(@Nullable BlockStateModelPart part) {
        return part == null ? null : part.particleSlotIcon();
    }

    /**
     * 资源重载（纹理缝合）时清空：避免残留上一周期的 IIcon。
     */
    public static void clear() {
        CACHE.clear();
    }

    // ==================== 内部解析 ====================

    @Nullable
    private static IIcon resolve(Block block, int metadata) {
        // 门禁：与 MixinRenderBlocks 同族的「CatFrame 接管」判定。
        if (!ModelRegistry.hasModel(block) && !RenderJsonBlockModel.isRegistered(block)) return null;

        BlockStateModel model = ModelRegistry.registeredBlockModels.get(block);
        if (model != null) {
            return orMissingno(model.particleIcon(metadata), block, metadata);
        }

        // 与 RenderDispatcher 路径 3 同构：无注册实例但 stateBlockData 含该方块 ——
        // 临时 StateProviderBlockModel 解析（provider 契约允许 null world；结果入缓存后至多一次）。
        if (block instanceof IBlockStateProvider && ModelManagerDataLoader.stateBlockData.containsKey(block)) {
            BlockstateJson bs = ModelManagerDataLoader.stateBlockData.get(block);
            if (bs != null) {
                IIcon slot = new StateProviderBlockModel((IBlockStateProvider) block, bs, null).particleIcon(metadata);
                return orMissingno(slot, block, metadata);
            }
        }
        // 已接管判定成立但无模型实例（如仅 ISBRH 注册的模组方块）→ 不介入，放行原版 getIcon。
        return null;
    }

    /**
     * 高版本缺省映射：接管方块的显式槽缺失/无法解析 → missingno（对标
     * {@code MaterialBaker#reportMissingReference} → {@code blockMissing}），
     * 不猜测替代纹理。极端情况下 missingno 不可得时回 null（调用方放行原版 getIcon）。
     */
    @Nullable
    private static IIcon orMissingno(@Nullable IIcon slot, Block block, int metadata) {
        if (slot != null) return slot;
        if (CatFrameConfig.shouldLogDebug()) {
            CatFrame.logger.info("[ParticleIconResolver] missing particle slot: block={} meta={} -> missingno",
                    Block.blockRegistry.getNameForObject(block), metadata);
        }
        return CatAtlasManager.getMissingIcon("particle");
    }
}
