package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.world.IBlockAccess;

/**
 * 单一静态模型实现。包装一个预烘焙的 {@link BlockStateModelPart}，
 * 无论 metadata 如何变化都返回同一个部件集合。
 *
 * <p>对应 1.21.5 的 SingleVariant + 静态 BlockStateModelPart。
 */
public class SingleBlockModel implements BlockStateModel {
    private final BlockStateModelPart part;

    public SingleBlockModel(BlockStateModelPart part) {
        this.part = part != null ? part : BlockStateModelPart.empty();
    }

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        return part;
    }

    public BlockStateModelPart getPart() {
        return part;
    }
}
