package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.world.IBlockAccess;

import java.util.Map;

/**
 * metadata → variant 调度模型。预先烘焙好所有 metadata 对应的
 * {@link BlockStateModelPart}，运行时根据 metadata 直接返回。
 *
 * <p>对应 1.21.5 的 VariantSelector（基于属性选择）+ WeightedVariants。
 */
public class MetadataBlockModel implements BlockStateModel {
    private final Map<Integer, BlockStateModelPart> parts;
    private final BlockStateModelPart fallback;

    /**
     * @param parts    metadata → BlockStateModelPart 映射
     * @param fallback 兜底模型（当 metadata 不存在于 parts 时使用）
     */
    public MetadataBlockModel(Map<Integer, BlockStateModelPart> parts, BlockStateModelPart fallback) {
        this.parts = parts;
        this.fallback = fallback != null ? fallback : BlockStateModelPart.empty();
    }

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        BlockStateModelPart part = parts.get(metadata);
        if (part != null) return part;
        // 也试试 meta 0 作为兜底
        part = parts.get(0);
        return part != null ? part : fallback;
    }
}
