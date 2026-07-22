package decok.dfcdvadstf.catframe.model.state.block;

import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
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

    private static String buildVariantKey(Map<String, String> properties) {
        if (properties.isEmpty()) return "normal";
        java.util.List<String> keys = new java.util.ArrayList<>(properties.keySet());
        java.util.Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(keys.get(i)).append('=').append(properties.get(keys.get(i)));
        }
        return sb.toString();
    }

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        if (blockstate == null) return BlockStateModelPart.empty();

        Map<String, String> properties = provider.getStateProperties(world, x, y, z, metadata);
        if (properties == null) properties = java.util.Collections.emptyMap();

        if (blockstate.variants != null) {
            String variantKey = buildVariantKey(properties);
            BlockstateJson.VariantEntry entry = blockstate.variants.get(variantKey);
            if (entry == null) entry = blockstate.variants.get("normal");
            if (entry == null) return BlockStateModelPart.empty();

            int seed = x * 3129871 ^ z * 116129781 ^ y;
            BlockstateJson.Variant variant = entry.getVariant(seed);
            if (variant == null || variant.model == null) return BlockStateModelPart.empty();

            // 直接烘焙（VanillaModelManager 有缓存）
            BlockStateModelPart part = AtlasGuard.gate(ModelBaker.bake(variant.model), variant.model);
            if (part == null || part.isEmpty()) return BlockStateModelPart.empty();
            return part;

        } else if (blockstate.multipart != null) {
            // Multipart: 合并所有匹配的部件
            Map<Direction, java.util.List<JsonModelBake.BakedQuad>> mergedFace
                    = new java.util.EnumMap<>(Direction.class);
            java.util.List<JsonModelBake.BakedQuad> mergedGeneral = new java.util.ArrayList<>();

            for (BlockstateJson.MultipartCase mpc : blockstate.multipart) {
                boolean applies = (mpc.when == null) || mpc.when.matches(properties);
                if (applies && mpc.apply != null && mpc.apply.model != null) {
                    BlockStateModelPart part = AtlasGuard.gate(ModelBaker.bake(mpc.apply.model), mpc.apply.model);
                    if (part != null) {
                        for (Direction dir : Direction.values()) {
                            mergedFace.computeIfAbsent(dir, k -> new java.util.ArrayList<>())
                                    .addAll(part.getQuads(dir));
                        }
                        mergedGeneral.addAll(part.getGeneralQuads());
                    }
                }
            }

            if (!mergedGeneral.isEmpty() || !mergedFace.isEmpty()) {
                return BlockStateModelPart.fromFaceMap(mergedFace, mergedGeneral);
            }
        }

        return BlockStateModelPart.empty();
    }
}
