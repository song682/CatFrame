package decok.dfcdvadstf.catframe.model.state.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.RenderDispatcher;
import decok.dfcdvadstf.catframe.model.core.baking.AtlasGuard;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.core.baking.ModelBaker;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.CatBlockState;
import decok.dfcdvadstf.catframe.model.state.CatStateDefinition;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 常驻状态方块模型——单实现收敛。
 * <p>
 * 本类以 {@link Builder} 配置的方式统一了原先四套并行的数据驱动世界方块模型：
 * <ul>
 *   <li>{@code LazyBlockstateModel} — 常规 variants / multipart（{@link IMetadataMapper} 或 typed
 *       {@link CatStateDefinition} 驱动属性）</li>
 *   <li>{@code LazyRedirectModel} — per-meta 重定向到另一个 blockstate（{@link IMetadataBlockstateRedirect}）</li>
 *   <li>{@code StairsBlockModel} — 运行时转角形状（dynamic 属性 + resolver）</li>
 *   <li>{@code PaneMultipartRedirectModel} — 运行时连接状态（dynamic 属性 + resolver，face-map 合并）</li>
 * </ul>
 *
 * <h3>属性来源（优先级）</h3>
 * <ol>
 *   <li>typed {@link CatStateDefinition}：{@code def.getStateFromMeta(meta)} 解出常驻状态，
 *       再由 {@link DynamicPropertyResolver} 覆盖动态属性</li>
 *   <li>{@link IMetadataMapper}：{@code mapper.map(meta)} 得到字符串属性表</li>
 *   <li>都没有：走 blockstate 的 {@code meta=} / 数字键 / {@code normal} 回退</li>
 * </ol>
 *
 * <h3>变体键统一</h3>
 * variant 匹配统一走 {@link RenderDispatcher#buildVariantKey(Map)}（字母序），
 * 与数据驱动路径完全一致。
 */
@SideOnly(Side.CLIENT)
public final class ResidentStateModel implements BlockStateModel {

    /**
     * 运行时动态属性解析器：把从世界计算得到的属性（如 stairs 的 {@code shape}、
     * pane 的 {@code north/east/south/west}）写入属性表。
     */
    @FunctionalInterface
    public interface DynamicPropertyResolver {
        void resolve(IBlockAccess world, int x, int y, int z, int meta, Map<String, String> props);
    }

    private final Block block;
    @Nullable
    private final BlockstateJson bs;
    @Nullable
    private final CatStateDefinition<?> def;
    @Nullable
    private final IMetadataMapper mapper;
    @Nullable
    private final IMetadataBlockstateRedirect redirect;
    @Nullable
    private final String namespace;
    @Nullable
    private final DynamicPropertyResolver dynamic;
    private final boolean fullModel;
    /** pane 模式：per-face 合并 + AtlasGuard 烘焙（对齐原 PaneMultipartRedirectModel）。 */
    private final boolean connectionMultipart;

    /** redirect 模式下每个 meta 解析出的目标 blockstate 缓存。 */
    private final Map<Integer, BlockstateJson> redirectCache = new HashMap<>();

    private ResidentStateModel(Builder b) {
        this.block = b.block;
        this.bs = b.bs;
        this.def = b.def;
        this.mapper = b.mapper;
        this.redirect = b.redirect;
        this.namespace = b.namespace;
        this.dynamic = b.dynamic;
        this.fullModel = b.fullModel;
        this.connectionMultipart = b.connectionMultipart;
    }

    // ==================== 渲染入口 ====================

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        BlockstateJson target = resolveTarget(metadata);
        if (target == null) return BlockStateModelPart.empty();

        Map<String, String> props = resolveProps(world, x, y, z, metadata);
        if (dynamic != null) {
            if (props == null) props = new HashMap<>();
            dynamic.resolve(world, x, y, z, metadata, props);
        }

        if (connectionMultipart) {
            return collectConnectionMultipart(target, props);
        }
        if (target.variants != null) {
            return collectVariants(world, x, y, z, metadata, target, props);
        }
        if (target.multipart != null) {
            return collectMultipart(target, props);
        }
        return BlockStateModelPart.empty();
    }

    @Override
    public boolean isFullModel() {
        return fullModel;
    }

    // ==================== 目标 blockstate 解析 ====================

    @Nullable
    private BlockstateJson resolveTarget(int metadata) {
        if (redirect == null) return bs;

        BlockstateJson cached = redirectCache.get(metadata);
        if (cached != null) return cached;

        String targetName = redirect.redirect(metadata);
        if (targetName == null) return null;

        BlockstateJson target = null;
        Map<String, BlockstateJson> nsMap = ModelManagerDataLoader.loadedBlockstates.get(namespace);
        if (nsMap != null) target = nsMap.get(targetName);
        if (target == null) {
            target = ModelManagerDataLoader.cachedRedirectBlockstates.get(namespace + ":" + targetName);
        }
        if (target == null) {
            target = ModelManagerDataLoader.loadSingleBlockstate(namespace, targetName);
            if (target != null) {
                if (nsMap == null) {
                    nsMap = new HashMap<>();
                    ModelManagerDataLoader.loadedBlockstates.put(namespace, nsMap);
                }
                nsMap.put(targetName, target);
            }
        }
        if (target != null) redirectCache.put(metadata, target);
        return target;
    }

    // ==================== 属性来源 ====================

    @Nullable
    private Map<String, String> resolveProps(IBlockAccess world, int x, int y, int z, int metadata) {
        if (def != null) {
            CatBlockState state = def.getStateFromMeta(metadata);
            return propsFromState(state);
        }
        if (mapper != null) {
            return mapper.map(metadata);
        }
        return null;
    }

    private static Map<String, String> propsFromState(CatBlockState state) {
        Map<String, String> props = new HashMap<>();
        CatStateDefinition<?> d = state.getDefinition();
        if (d != null) {
            Property<?>[] properties = d.getProperties();
            List<String> valueNames = state.getValueNames();
            for (int i = 0; i < properties.length && i < valueNames.size(); i++) {
                props.put(properties[i].getName(), valueNames.get(i));
            }
        }
        return props;
    }

    // ==================== variants 匹配（BakedModelCache 展平） ====================

    private BlockStateModelPart collectVariants(IBlockAccess world, int x, int y, int z,
                                                int metadata, BlockstateJson target,
                                                @Nullable Map<String, String> props) {
        // dynamic（如 stairs）随位置产生权重随机；纯 mapper/def 用 meta 种子（保留原有行为）
        int seed = (dynamic != null) ? (x * 3129871 ^ z * 116129781 ^ y) : (metadata * 31);
        BlockstateJson.Variant variant = resolveVariant(metadata, target, props, seed);
        if (variant == null || variant.model == null) {
            // 回退到 meta 0 的变体
            variant = resolveVariant(0, target, propsForMeta(0), seed);
        }

        String modelPath;
        int rotX, rotY;
        if (variant != null && variant.model != null) {
            modelPath = variant.model;
            rotX = variant.x;
            rotY = variant.y;
        } else {
            modelPath = "builtin/missing";
            rotX = 0;
            rotY = 0;
        }

        String cacheKey = BakedModelCache.buildKey(modelPath, rotX, rotY);
        BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
        return part != null ? part : BlockStateModelPart.empty();
    }

    @Nullable
    private Map<String, String> propsForMeta(int metadata) {
        if (def != null) return propsFromState(def.getStateFromMeta(metadata));
        if (mapper != null) return mapper.map(metadata);
        return null;
    }

    @Nullable
    private BlockstateJson.Variant resolveVariant(int metadata, BlockstateJson target,
                                                  @Nullable Map<String, String> props, int seed) {
        String variantKey;
        if (props != null) {
            variantKey = RenderDispatcher.buildVariantKey(props);
        } else if (hasMetaVariantKeys(target)) {
            variantKey = "meta=" + metadata;
        } else {
            variantKey = findNumberKeyForMeta(target, metadata);
        }

        BlockstateJson.VariantEntry entry = target.variants.get(variantKey);
        if (entry == null) entry = target.variants.get("normal");
        if (entry == null) return null;

        return entry.getVariant(seed);
    }

    // ==================== 常规 multipart 匹配（展平） ====================

    private BlockStateModelPart collectMultipart(BlockstateJson target,
                                                 @Nullable Map<String, String> props) {
        Map<String, String> matchProps = (props != null) ? props : Collections.emptyMap();
        List<BakedQuad> allQuads = new ArrayList<>();

        for (BlockstateJson.MultipartCase mpc : target.multipart) {
            boolean applies = (mpc.when == null) || mpc.when.matches(matchProps);
            if (applies && mpc.apply != null && mpc.apply.model != null) {
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

    // ==================== 连接 multipart（pane，face-map 合并 + AtlasGuard） ====================

    private BlockStateModelPart collectConnectionMultipart(BlockstateJson target,
                                                           @Nullable Map<String, String> props) {
        if (target.multipart == null) return BlockStateModelPart.empty();
        Map<String, String> matchProps = (props != null) ? props : Collections.emptyMap();

        if (CatFrameConfig.shouldLogDebug()) {
            CatFrame.logger.info("[ResidentPane] block={} props={} cases={}",
                    Block.blockRegistry.getNameForObject(block), matchProps,
                    target.multipart.size());
        }

        Map<Direction, List<BakedQuad>> mergedFace = new EnumMap<>(Direction.class);
        List<BakedQuad> mergedGeneral = new ArrayList<>();

        for (BlockstateJson.MultipartCase mpc : target.multipart) {
            boolean applies = (mpc.when == null) || mpc.when.matches(matchProps);
            if (applies && mpc.apply != null && mpc.apply.model != null) {
                BlockStateModelPart part = AtlasGuard.gate(
                        ModelBaker.bake(mpc.apply.model, mpc.apply.x, mpc.apply.y), mpc.apply.model);
                if (part != null) {
                    for (Direction dir : Direction.values()) {
                        mergedFace.computeIfAbsent(dir, k -> new ArrayList<>())
                                .addAll(part.getQuads(dir));
                    }
                    mergedGeneral.addAll(part.getGeneralQuads());
                }
            }
        }

        if (mergedGeneral.isEmpty() && mergedFace.isEmpty()) {
            return BlockStateModelPart.empty();
        }
        return BlockStateModelPart.fromFaceMap(mergedFace, mergedGeneral);
    }

    // ==================== 辅助 ====================

    private static boolean hasMetaVariantKeys(BlockstateJson target) {
        if (target.variants == null) return false;
        for (String key : target.variants.keySet()) {
            if (key != null && key.startsWith("meta=")) {
                try {
                    Integer.parseInt(key.substring(5));
                    return true;
                } catch (NumberFormatException ignored) { }
            }
        }
        return false;
    }

    private static String findNumberKeyForMeta(BlockstateJson target, int meta) {
        if (target.variants == null) return "normal";
        String metaStr = String.valueOf(meta);
        if (target.variants.containsKey(metaStr)) return metaStr;
        if (target.variants.containsKey("normal")) return "normal";
        return target.variants.keySet().stream().findFirst().orElse("normal");
    }

    // ==================== Builder ====================

    public static Builder builder(Block block) {
        return new Builder(block);
    }

    public static final class Builder {
        private final Block block;
        private BlockstateJson bs;
        private CatStateDefinition<?> def;
        private IMetadataMapper mapper;
        private IMetadataBlockstateRedirect redirect;
        private String namespace;
        private DynamicPropertyResolver dynamic;
        private boolean fullModel = true;
        private boolean connectionMultipart = false;

        private Builder(Block block) {
            this.block = block;
        }

        public Builder blockstate(BlockstateJson bs) {
            this.bs = bs;
            return this;
        }

        public Builder stateDefinition(CatStateDefinition<?> def) {
            this.def = def;
            return this;
        }

        public Builder mapper(IMetadataMapper mapper) {
            this.mapper = mapper;
            return this;
        }

        public Builder redirect(IMetadataBlockstateRedirect redirect, String namespace) {
            this.redirect = redirect;
            this.namespace = namespace;
            return this;
        }

        public Builder dynamic(DynamicPropertyResolver dynamic) {
            this.dynamic = dynamic;
            return this;
        }

        public Builder fullModel(boolean fullModel) {
            this.fullModel = fullModel;
            return this;
        }

        /** 启用 pane 连接 multipart 模式（face-map 合并 + AtlasGuard，isFullModel=false）。 */
        public Builder connectionMultipart() {
            this.connectionMultipart = true;
            this.fullModel = false;
            return this;
        }

        public ResidentStateModel build() {
            return new ResidentStateModel(this);
        }
    }
}
