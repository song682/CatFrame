package decok.dfcdvadstf.catframe.model.core.baking;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import javax.annotation.Nullable;

/**
 * 方块世界路径的图集门禁（对标 26.1.2 {@code SimpleModelWrapper.bake}
 * 的 forbiddenSprites 拒收）。
 * <p>
 * blockstate 包装层的模型若含非 blocks 图集的 sprite，整个模型拒收、
 * 记录警告并替换为 {@code builtin/missing}（missingno）——世界渲染路径
 * 恒绑 blocks 图集，跨图集 quad 的 UV 会采样到错误区域。
 * 物品路径（ItemStateModel 直连烘焙）不受限，允许任意图集。
 */
public final class AtlasGuard {

    private AtlasGuard() {
    }

    /** 懒烘焙缓存的 builtin/missing 替换部件。 */
    @Nullable
    private static BlockStateModelPart missingPart;

    /**
     * 门禁检查：部件全部 quad 均来自 blocks 图集时原样放行，
     * 否则拒收并返回 missing 部件。
     *
     * @param part      待检部件（可为 null，原样返回）
     * @param modelPath 模型路径（仅用于警告日志）
     * @return 放行的原部件，或替换用的 missing 部件
     */
    @Nullable
    public static BlockStateModelPart gate(@Nullable BlockStateModelPart part, String modelPath) {
        if (part == null || part.allQuadsInBlockAtlas()) return part;
        CatFrame.logger.warn(
                "[AtlasGuard] Rejecting block model '{}', since it contains sprites from outside blocks atlas",
                modelPath);
        return missingPart();
    }

    private static synchronized BlockStateModelPart missingPart() {
        if (missingPart == null) {
            missingPart = ModelBaker.bake(
                    ModelResolver.resolve("builtin/missing"), 0, 0, "builtin/missing");
            if (missingPart == null) missingPart = BlockStateModelPart.empty();
        }
        return missingPart;
    }
}
