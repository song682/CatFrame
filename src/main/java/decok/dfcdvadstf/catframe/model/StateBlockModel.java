package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.state.*;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;

import java.util.*;

/**
 * 基于 CatBlockState 的 BlockStateModel 实现。使用类型安全的属性系统
 * 替代 {@link StateProviderBlockModel} 的 {@code Map<String, String>} 路径。
 *
 * <p>当 IBlockStateProvider 同时实现了 {@link CatStateDefinition} 时，
 * 优先使用此类进行调度。运行时通过 {@link CatBlockState#toVariantKey()}
 * 与 blockstate JSON 的 variant key 匹配。
 *
 * <p>对应 1.21.5 的 VariantSelector + 属性匹配机制。
 */
public class StateBlockModel implements BlockStateModel {

    private final IBlockStateProvider provider;
    private final BlockstateJson blockstate;
    private final String fallbackModelPath;

    /**
     * @param provider          实现了 IBlockStateProvider 的方块
     * @param blockstate        已加载的 blockstate JSON
     * @param fallbackModelPath 兜底 model path（如 "block/stone"）
     */
    public StateBlockModel(IBlockStateProvider provider,
                           BlockstateJson blockstate,
                           String fallbackModelPath) {
        this.provider = provider;
        this.blockstate = blockstate;
        this.fallbackModelPath = fallbackModelPath;
    }

    // ==================== CatBlockState 路径（v0.3.0） ====================

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z,
                                            CatBlockState state) {
        if (blockstate == null) return BlockStateModelPart.empty();

        if (blockstate.variants != null) {
            String variantKey = state.toVariantKey();
            BlockstateJson.VariantEntry entry = blockstate.variants.get(variantKey);
            if (entry == null) entry = blockstate.variants.get("normal");
            if (entry == null) return BlockStateModelPart.empty();

            int seed = x * 3129871 ^ z * 116129781 ^ y;
            BlockstateJson.Variant variant = entry.getVariant(seed);
            if (variant == null || variant.model == null) return BlockStateModelPart.empty();

            BlockStateModelPart part = ModelBaker.bake(variant.model);
            if (part == null || part.isEmpty()) return BlockStateModelPart.empty();
            return part;

        } else if (blockstate.multipart != null) {
            // Multipart: 使用 CatBlockState.matches() 进行条件匹配
            Map<EnumFacing, List<BakedQuad>> mergedFace
                    = new EnumMap<>(EnumFacing.class);
            List<BakedQuad> mergedGeneral = new ArrayList<>();

            // [W7 修复] buildPropertyMap 对同一 state 结果不变，提到循环外避免重复计算
            Map<String, String> propMap = buildPropertyMap(state);
            for (BlockstateJson.MultipartCase mpc : blockstate.multipart) {
                boolean applies = (mpc.when == null) || mpc.when.matches(propMap);
                if (applies && mpc.apply != null && mpc.apply.model != null) {
                    BlockStateModelPart part = ModelBaker.bake(mpc.apply.model);
                    if (part != null) {
                        for (EnumFacing dir : EnumFacing.values()) {
                            mergedFace.computeIfAbsent(dir, k -> new ArrayList<>())
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

    // ==================== 旧 metadata 路径（向后兼容） ====================

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        // 优先使用 CatBlockState 路径
        CatBlockState state = provider.getBlockState(world, x, y, z, metadata);
        if (state != null) {
            return collectParts(world, x, y, z, state);
        }
        // 回退到旧的 Map<String,String> 路径
        return collectPartsLegacy(world, x, y, z, metadata);
    }

    private BlockStateModelPart collectPartsLegacy(IBlockAccess world, int x, int y, int z, int metadata) {
        if (blockstate == null) return BlockStateModelPart.empty();

        Map<String, String> properties = provider.getStateProperties(world, x, y, z, metadata);
        if (properties == null) properties = Collections.emptyMap();

        if (blockstate.variants != null) {
            String variantKey = buildVariantKey(properties);
            BlockstateJson.VariantEntry entry = blockstate.variants.get(variantKey);
            if (entry == null) entry = blockstate.variants.get("normal");
            if (entry == null) return BlockStateModelPart.empty();

            int seed = x * 3129871 ^ z * 116129781 ^ y;
            BlockstateJson.Variant variant = entry.getVariant(seed);
            if (variant == null || variant.model == null) return BlockStateModelPart.empty();

            BlockStateModelPart part = ModelBaker.bake(variant.model);
            if (part == null || part.isEmpty()) return BlockStateModelPart.empty();
            return part;

        } else if (blockstate.multipart != null) {
            Map<EnumFacing, List<BakedQuad>> mergedFace
                    = new EnumMap<>(EnumFacing.class);
            List<BakedQuad> mergedGeneral = new ArrayList<>();

            for (BlockstateJson.MultipartCase mpc : blockstate.multipart) {
                boolean applies = (mpc.when == null) || mpc.when.matches(properties);
                if (applies && mpc.apply != null && mpc.apply.model != null) {
                    BlockStateModelPart part = ModelBaker.bake(mpc.apply.model);
                    if (part != null) {
                        for (EnumFacing dir : EnumFacing.values()) {
                            mergedFace.computeIfAbsent(dir, k -> new ArrayList<>())
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

    // ==================== 辅助方法 ====================

    private static String buildVariantKey(Map<String, String> properties) {
        if (properties.isEmpty()) return "normal";
        List<String> keys = new ArrayList<>(properties.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(keys.get(i)).append('=').append(properties.get(keys.get(i)));
        }
        return sb.toString();
    }

    /**
     * 将 CatBlockState 的值转换为用于 multipart 条件匹配的 Map。
     */
    private static Map<String, String> buildPropertyMap(CatBlockState state) {
        CatStateDefinition<?> def = state.getDefinition();
        if (def == null) return Collections.emptyMap();

        Map<String, String> result = new HashMap<>();
        for (Property<?> prop : def.getProperties()) {
            result.put(prop.getName(), state.getValue(prop).toString());
        }
        return result;
    }
}
