package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.render.RenderJsonBlockModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.block.StateProviderBlockModel;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方块破坏 / hit / blockdust 粒子的模型驱动纹理解析入口。
 * <p>
 * 对标 26.1.2 的 {@code BlockStateModelSet#getParticleMaterial(state)}：按
 * {@code (block, metadata)} 解析 CatFrame 接管方块的粒子纹理，语义链为
 * 「模型 textures.particle 槽 → 首 quad 回退 → null（调用方回退原版 getIcon）」。
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
     * 未接管方块（无模型）或解析失败时返回 null —— 调用方应回退原版
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
     * 回退链居中点：显式 particle 槽优先，缺失回退既有首 quad
     * （{@link BlockStateModelPart#particleIcon()}，项目规则保留的既有回退）。
     * Fallback-chain midpoint: explicit particle slot first, preserved first-quad
     * fallback otherwise.
     */
    @Nullable
    public static IIcon fromPart(@Nullable BlockStateModelPart part) {
        if (part == null) return null;
        IIcon slot = part.particleSlotIcon();
        return slot != null ? slot : part.particleIcon();
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
            return model.particleIcon(metadata);
        }

        // 与 RenderDispatcher 路径 3 同构：无注册实例但 stateBlockData 含该方块 ——
        // 临时 StateProviderBlockModel 解析（provider 契约允许 null world；结果入缓存后至多一次）。
        if (block instanceof IBlockStateProvider && ModelManagerDataLoader.stateBlockData.containsKey(block)) {
            BlockstateJson bs = ModelManagerDataLoader.stateBlockData.get(block);
            if (bs != null) {
                return new StateProviderBlockModel((IBlockStateProvider) block, bs, null).particleIcon(metadata);
            }
        }
        return null;
    }
}
