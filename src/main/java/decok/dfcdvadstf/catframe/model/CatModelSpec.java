package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.CatBlockState;
import decok.dfcdvadstf.catframe.model.state.CatStateDefinition;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import net.minecraft.block.Block;

import javax.annotation.Nullable;
import java.util.*;

/**
 * {@link CatModels} registered block model spec (data holder).
 * <p>
 * Collected during preInit by {@link CatModels} chainable API, materialized at runtime
 * ({@code registerAllModels}, after blockstate JSON loaded) via
 * {@link #buildBlockModel(BlockstateJson)} / {@link #buildItemNode(BlockstateJson)}
 * into {@link ResidentStateModel} and item decision tree.
 * <p>
 * This solves the "blockstate not yet async-loaded during preInit" timing issue:
 * spec only records declarative intent; actual materialization needing bs is deferred
 * to bake registration phase.
 */
@SideOnly(Side.CLIENT)
public final class CatModelSpec {

    public final Block block;
    @Nullable
    public CatStateDefinition<?> def;
    @Nullable
    public IMetadataBlockstateRedirect redirect;
    @Nullable
    public String redirectNamespace;
    @Nullable
    public ResidentStateModel.DynamicPropertyResolver dynamic;
    public boolean connectionMultipart = false;
    public boolean fullModel = true;
    /** Whether to export item {@code catframe:meta} decision tree from blockstate's 16 static states. */
    public boolean itemFromBlockstate = false;

    public CatModelSpec(Block block) {
        this.block = block;
    }

    // ==================== Materialization ====================

    /**
     * Materialize into resident block model using loaded blockstate JSON.
     *
     * @param bs block's blockstate JSON (nullable in redirect mode, resolved at runtime by meta)
     * @return resident block model
     */
    public ResidentStateModel buildBlockModel(@Nullable BlockstateJson bs) {
        ResidentStateModel.Builder b = ResidentStateModel.builder(block);
        if (bs != null) b.blockstate(bs);
        if (def != null) b.stateDefinition(def);
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
     * Export item decision tree from blockstate's static states: {@code catframe:meta} → model path.
     * <p>
     * Iterates meta 0-15, uses {@link #def} (and {@link #redirect}) to resolve the
     * blockstate variant model path for each meta, builds {@link ItemStateNode.SelectNode}
     * (property = CatFrame extended property {@code "catframe:meta"}, i.e. 1.7.10 metadata).
     * Unmatched meta falls back to {@code builtin/missing}.
     *
     * @param bs block's blockstate JSON
     * @return item decision tree root; null if no models exported
     */
    @Nullable
    public ItemStateNode buildItemNode(@Nullable BlockstateJson bs) {
        List<ItemStateNode.SelectCase> cases = new ArrayList<>();
        for (int meta = 0; meta < 16; meta++) {
            String model = resolveModelPath(meta, bs);
            if (model != null) {
                cases.add(new ItemStateNode.SelectCase(
                        Collections.singleton(String.valueOf(meta)),
                        new ItemStateNode.ModelLeaf(model)));
            }
        }
        if (cases.isEmpty()) return null;
        ItemStateNode fallback = new ItemStateNode.ModelLeaf("builtin/missing");
        return new ItemStateNode.SelectNode("catframe:meta", cases, fallback);
    }

    // ==================== Internal: static props → variant model path ====================

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

    /** Static props only (item export, no world/dynamic). */
    @Nullable
    private Map<String, String> resolveProps(int meta) {
        if (def != null) {
            CatBlockState state = def.getStateFromMeta(meta);
            Map<String, String> props = new LinkedHashMap<>();
            Property<?>[] properties = def.getProperties();
            List<String> valueNames = state.getValueNames();
            for (int i = 0; i < properties.length && i < valueNames.size(); i++) {
                // Dynamic properties excluded from item matching (items use static defaults)
                props.put(properties[i].getName(), valueNames.get(i));
            }
            return props;
        }
        return null;
    }
}
