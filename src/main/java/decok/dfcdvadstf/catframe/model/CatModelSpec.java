package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.CatBlockState;
import decok.dfcdvadstf.catframe.model.state.CatStateDefinition;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import decok.dfcdvadstf.catframe.model.state.property.ItemProperties;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import net.minecraft.block.Block;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link CatModels} 登记的方块模型规格（数据 holder）。
 * <p>
 * 在 preInit 阶段由 {@link CatModels} 链式 API 收集，运行时（{@code registerAllModels}，
 * blockstate JSON 已加载后）由 {@link #buildBlockModel(BlockstateJson)} /
 * {@link #buildItemNode(BlockstateJson)} 物化为 {@link ResidentStateModel} 与物品决策树。
 * <p>
 * 这样解决了「preInit 时 blockstate 尚未异步加载」的时序问题：spec 只记录声明式意图，
 * 真正需要 bs 的物化推迟到烘焙注册阶段。
 */
@SideOnly(Side.CLIENT)
public final class CatModelSpec {

    public final Block block;
    @Nullable
    public CatStateDefinition<?> def;
    @Nullable
    public IMetadataMapper mapper;
    @Nullable
    public IMetadataBlockstateRedirect redirect;
    @Nullable
    public String redirectNamespace;
    @Nullable
    public ResidentStateModel.DynamicPropertyResolver dynamic;
    public boolean connectionMultipart = false;
    public boolean fullModel = true;
    /** 是否从 blockstate 的 16 个静态状态导出物品 {@code damage} 决策树。 */
    public boolean itemFromBlockstate = false;

    public CatModelSpec(Block block) {
        this.block = block;
    }

    // ==================== 物化 ====================

    /**
     * 用已加载的 blockstate JSON 物化为常驻方块模型。
     *
     * @param bs 该方块的 blockstate JSON（redirect 模式下可为 null，运行时按 meta 解析）
     * @return 常驻方块模型
     */
    public ResidentStateModel buildBlockModel(@Nullable BlockstateJson bs) {
        ResidentStateModel.Builder b = ResidentStateModel.builder(block);
        if (bs != null) b.blockstate(bs);
        if (def != null) b.stateDefinition(def);
        if (mapper != null) b.mapper(mapper);
        if (redirect != null) b.redirect(redirect, redirectNamespace);
        if (dynamic != null) b.dynamic(dynamic);
        if (connectionMultipart) {
            b.connectionMultipart();
        } else {
            b.fullModel(fullModel);
        }
        return b.build();
    }

    /**
     * 从 blockstate 的静态状态导出物品决策树：{@code damage} → 模型路径。
     * <p>
     * 遍历 meta 0-15，用 {@link #def} / {@link #mapper}（及 {@link #redirect}）解析出该
     * meta 对应的 blockstate variant 模型路径，构建 {@link ItemStateNode.ExactMatchNode}
     * （key = {@link ItemProperties#DAMAGE} 的名称 {@code "damage"}）。未命中的 meta 走
     * fallback（{@code builtin/missing}）。
     *
     * @param bs 该方块的 blockstate JSON
     * @return 物品决策树根节点；无法导出任何模型时返回 {@code null}
     */
    @Nullable
    public ItemStateNode buildItemNode(@Nullable BlockstateJson bs) {
        Map<String, ItemStateNode> cases = new LinkedHashMap<>();
        for (int meta = 0; meta < 16; meta++) {
            String model = resolveModelPath(meta, bs);
            if (model != null) {
                cases.put(String.valueOf(meta), new ItemStateNode.ModelLeaf(model));
            }
        }
        if (cases.isEmpty()) return null;
        ItemStateNode fallback = new ItemStateNode.ModelLeaf("builtin/missing");
        return new ItemStateNode.ExactMatchNode(ItemProperties.DAMAGE.getName(), cases, fallback);
    }

    // ==================== 内部：静态属性 → variant 模型路径 ====================

    @Nullable
    private String resolveModelPath(int meta, @Nullable BlockstateJson baseBs) {
        BlockstateJson target = resolveTargetBs(meta, baseBs);
        if (target == null || target.variants == null) return null;

        Map<String, String> props = resolveProps(meta);
        String variantKey;
        if (props != null) {
            variantKey = RenderDispatcher.buildVariantKey(props);
        } else {
            variantKey = String.valueOf(meta);
        }

        BlockstateJson.VariantEntry entry = target.variants.get(variantKey);
        if (entry == null) entry = target.variants.get("meta=" + meta);
        if (entry == null) entry = target.variants.get("normal");
        if (entry == null) return null;

        BlockstateJson.Variant variant = entry.getVariant(0);
        return (variant != null) ? variant.model : null;
    }

    @Nullable
    private BlockstateJson resolveTargetBs(int meta, @Nullable BlockstateJson baseBs) {
        if (redirect == null) return baseBs;
        String targetName = redirect.redirect(meta);
        if (targetName == null) return null;
        BlockstateJson t = null;
        Map<String, BlockstateJson> nsMap = ModelManagerDataLoader.loadedBlockstates.get(redirectNamespace);
        if (nsMap != null) t = nsMap.get(targetName);
        if (t == null) {
            t = ModelManagerDataLoader.cachedRedirectBlockstates.get(redirectNamespace + ":" + targetName);
        }
        if (t == null) {
            t = ModelManagerDataLoader.loadSingleBlockstate(redirectNamespace, targetName);
        }
        return t;
    }

    /** 仅用静态属性（物品导出，无世界/dynamic）。 */
    @Nullable
    private Map<String, String> resolveProps(int meta) {
        if (def != null) {
            CatBlockState state = def.getStateFromMeta(meta);
            Map<String, String> props = new LinkedHashMap<>();
            Property<?>[] properties = def.getProperties();
            List<String> valueNames = state.getValueNames();
            for (int i = 0; i < properties.length && i < valueNames.size(); i++) {
                // 动态属性不参与物品匹配（物品用静态默认值即可）
                props.put(properties[i].getName(), valueNames.get(i));
            }
            return props;
        }
        if (mapper != null) {
            return mapper.map(meta);
        }
        return null;
    }
}
