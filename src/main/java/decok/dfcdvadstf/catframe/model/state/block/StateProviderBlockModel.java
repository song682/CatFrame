package decok.dfcdvadstf.catframe.model.state.block;

import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.RenderDispatcher;
import decok.dfcdvadstf.catframe.model.core.baking.AtlasGuard;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake;
import decok.dfcdvadstf.catframe.model.core.baking.ModelBaker;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import net.minecraft.world.IBlockAccess;

import java.util.Map;


/**
 * 委托 IBlockStateProvider 的动态模型。每次渲染时调用
 * {@link IBlockStateProvider#getStateProperties} 获取属性，
 * 再匹配 blockstate JSON 中的 variant 键。
 *
 * <p>对应 1.21.5 的 VariantSelector + IBlockStateProvider 组合。
 */
public class StateProviderBlockModel implements BlockStateModel {
    private final IBlockStateProvider provider;
    private final BlockstateJson blockstate;
    private final String fallbackModelPath;

    /**
     * @param provider          实现了 IBlockStateProvider 的方块
     * @param blockstate        已加载的 blockstate JSON
     * @param fallbackModelPath 兜底 model path（如 "block/stone"）
     */
    public StateProviderBlockModel(IBlockStateProvider provider,
                                   BlockstateJson blockstate,
                                   String fallbackModelPath) {
        this.provider = provider;
        this.blockstate = blockstate;
        this.fallbackModelPath = fallbackModelPath;
    }

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        return collectPartsWithProps(world, x, y, z, metadata).part;
    }

    @Override
    public BlockStateModel.CollectedPart collectPartsWithProps(IBlockAccess world, int x, int y, int z, int metadata) {
        if (blockstate == null) return new BlockStateModel.CollectedPart(BlockStateModelPart.empty(), null);

        Map<String, String> properties = provider.getStateProperties(world, x, y, z, metadata);
        if (properties == null) properties = java.util.Collections.emptyMap();

        BlockStateModelPart part;
        if (blockstate.variants != null) {
            String variantKey = RenderDispatcher.buildVariantKey(properties);
            BlockstateJson.VariantEntry entry = blockstate.variants.get(variantKey);
            if (entry == null) entry = blockstate.variants.get("normal");
            if (entry == null) return new BlockStateModel.CollectedPart(BlockStateModelPart.empty(), null);

            int seed = x * 3129871 ^ z * 116129781 ^ y;
            BlockstateJson.Variant variant = entry.getVariant(seed);
            if (variant == null || variant.model == null) return new BlockStateModel.CollectedPart(BlockStateModelPart.empty(), null);

            // 直接烘焙（VanillaModelManager 有缓存）
            part = AtlasGuard.gate(ModelBaker.bake(variant.model), variant.model);
            if (part == null || part.isEmpty()) return new BlockStateModel.CollectedPart(BlockStateModelPart.empty(), null);

        } else if (blockstate.multipart != null) {
            // Multipart: 合并所有匹配的部件
            Map<Direction, java.util.List<JsonModelBake.BakedQuad>> mergedFace
                    = new java.util.EnumMap<>(Direction.class);
            java.util.List<JsonModelBake.BakedQuad> mergedGeneral = new java.util.ArrayList<>();

            for (BlockstateJson.MultipartCase mpc : blockstate.multipart) {
                boolean applies = (mpc.when == null) || mpc.when.matches(properties);
                if (applies && mpc.apply != null) {
                    BlockstateJson.Variant v = mpc.apply.getVariant(0);
                    if (v != null && v.model != null) {
                        BlockStateModelPart subPart = AtlasGuard.gate(ModelBaker.bake(v.model), v.model);
                        if (subPart != null) {
                            for (Direction dir : Direction.values()) {
                                mergedFace.computeIfAbsent(dir, k -> new java.util.ArrayList<>())
                                        .addAll(subPart.getQuads(dir));
                            }
                            mergedGeneral.addAll(subPart.getGeneralQuads());
                        }
                    }
                }
            }

            if (!mergedGeneral.isEmpty() || !mergedFace.isEmpty()) {
                part = BlockStateModelPart.fromFaceMap(mergedFace, mergedGeneral);
            } else {
                return new BlockStateModel.CollectedPart(BlockStateModelPart.empty(), null);
            }
        } else {
            return new BlockStateModel.CollectedPart(BlockStateModelPart.empty(), null);
        }

        // 匹配完成后包裹只读视图随提交携带（防扩展篡改）；属性为空时保持 null，零额外成本。
        Map<String, String> exposed = properties.isEmpty()
                ? null : java.util.Collections.unmodifiableMap(properties);
        return new BlockStateModel.CollectedPart(part, exposed);
    }
}
