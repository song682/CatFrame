package decok.dfcdvadstf.catframe.model.lazy;

import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.RenderDispatcher;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import net.minecraft.world.IBlockAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 懒烘焙 blockstate 模型。持有 {@link BlockstateJson} 和 {@link IMetadataMapper}，
 * 运行时根据 metadata 解析 model path 并从 {@link BakedModelCache} 获取烘焙结果。
 * <p>
 * 统一处理 variants 和 multipart 两种 blockstate 格式：
 * <ul>
 *   <li>variants: metadata → mapper → property map → variant key → model path</li>
 *   <li>multipart: metadata → mapper → property map → evaluate conditions → combine parts</li>
 * </ul>
 * <p>
 * 替代旧系统中 {@code bakeBlockstateForBlock()} 的同步烘焙路径。
 */
public class LazyBlockstateModel implements BlockStateModel {
    private final BlockstateJson bs;
    private final IMetadataMapper mapper;
    /** Variant 路径缓存: metadata → (modelPath, rotX, rotY)。仅 variants 模式使用。 */
    private final String[] variantModelPaths = new String[16];
    private final int[] variantRotX = new int[16];
    private final int[] variantRotY = new int[16];
    private final boolean variantsResolved;
    /** multipart 模式标记 */
    private final boolean isMultipart;

    public LazyBlockstateModel(BlockstateJson bs, IMetadataMapper mapper) {
        this.bs = bs;
        this.mapper = mapper;
        this.isMultipart = (bs.variants == null || bs.variants.isEmpty()) && bs.multipart != null;
        this.variantsResolved = !isMultipart && bs.variants != null;
        if (variantsResolved) {
            resolveAllVariants();
        }
    }

    private void resolveAllVariants() {
        for (int meta = 0; meta < 16; meta++) {
            Map<String, String> props = (mapper != null) ? mapper.map(meta) : null;
            String variantKey;
            if (props != null) {
                variantKey = RenderDispatcher.buildVariantKey(props);
            } else if (hasMetaVariantKeys()) {
                variantKey = "meta=" + meta;
            } else {
                variantKey = findNumberKeyForMeta(meta);
            }

            BlockstateJson.VariantEntry entry = bs.variants.get(variantKey);
            if (entry == null) entry = bs.variants.get("normal");
            if (entry == null) continue;

            int seed = meta * 31;
            BlockstateJson.Variant variant = entry.getVariant(seed);
            if (variant != null && variant.model != null) {
                variantModelPaths[meta] = variant.model;
                variantRotX[meta] = variant.x;
                variantRotY[meta] = variant.y;
            }
        }
    }

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        if (isMultipart) {
            return collectMultipart(metadata);
        }
        // Variants path
        String modelPath = variantModelPaths[metadata];
        if (modelPath == null) {
            modelPath = variantModelPaths[0]; // fallback to meta 0
        }
        if (modelPath == null) {
            // blockstate 中未找到任何匹配的 variant → fallback 到 builtin/missing
            modelPath = "builtin/missing";
        }

        String cacheKey = BakedModelCache.buildKey(modelPath, variantRotX[metadata], variantRotY[metadata]);
        BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
        return part != null ? part : BlockStateModelPart.empty();
    }

    private BlockStateModelPart collectMultipart(int metadata) {
        if (bs.multipart == null) return BlockStateModelPart.empty();

        Map<String, String> props = (mapper != null) ? mapper.map(metadata) : null;
        List<BakedQuad> allQuads = new ArrayList<>();

        for (BlockstateJson.MultipartCase mpc : bs.multipart) {
            boolean applies = (mpc.when == null) || (props != null && mpc.when.matches(props));
            if (applies && mpc.apply != null) {
                String partKey = BakedModelCache.buildKey(mpc.apply.model, mpc.apply.x, mpc.apply.y);
                BlockStateModelPart bakedPart = BakedModelCache.INSTANCE.get(partKey);
                if (bakedPart != null && !bakedPart.isEmpty()) {
                    allQuads.addAll(bakedPart.getAllQuads());
                }
            }
        }

        if (allQuads.isEmpty()) return BlockStateModelPart.empty();
        return BlockStateModelPart.fromQuads(allQuads);
    }

    // ==================== 辅助方法 ====================

    private boolean hasMetaVariantKeys() {
        if (bs.variants == null) return false;
        for (String key : bs.variants.keySet()) {
            if (key != null && key.startsWith("meta=")) {
                try {
                    Integer.parseInt(key.substring(5));
                    return true;
                } catch (NumberFormatException ignored) { }
            }
        }
        return false;
    }

    private String findNumberKeyForMeta(int meta) {
        if (bs.variants == null) return "normal";
        String metaStr = String.valueOf(meta);
        if (bs.variants.containsKey(metaStr)) return metaStr;
        if (bs.variants.containsKey("normal")) return "normal";
        // Return first key as fallback
        return bs.variants.keySet().stream().findFirst().orElse("normal");
    }
}
