package decok.dfcdvadstf.catframe.model.state;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel;
import decok.dfcdvadstf.catframe.model.state.property.MetaBlockProperty;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import net.minecraft.block.Block;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Automatic BlockState inheritance — maps a block class's inheritance chain to a
 * {@link BlockStateDefinition} fragment, so subclasses need not manually redeclare
 * their superclass's properties and meta decoding.
 * <p>
 * A mod developer (or CatFrame's built-in table) first uses {@link #registerBase(Class)}
 * to register a "state definition fragment" (properties + MetaCodec + optional dynamic
 * resolver / redirect) for some block base class; afterwards any block extending that
 * base class will, at registration time ({@code Models.Spec.register()} /
 * {@code ModernBlock.register()}), automatically merge into a complete
 * {@link BlockStateDefinition} via {@link #resolve(Block)}.
 *
 * <h3>Merge semantics</h3>
 * <ul>
 *   <li>Collect fragments upward along {@code block.getClass()} (up to {@link Object}),
 *       concatenating properties with "superclass first, subclass last"; a property with
 *       the same name keeps its first-appearance position, while its definition is taken
 *       from the subclass.</li>
 *   <li>MetaCodec concatenates the output value arrays by fragment, then reorders by the
 *       final static property order (remapping on same-name overrides).</li>
 *   <li>Distributed byte mapping (preferred): when every static (non-dynamic) merged property
 *       is a {@link MetaBlockProperty}, each property decodes only its own metadata bit field, so
 *       inheritance is a plain property union and multiple fragments combine without any
 *       positional codec splicing. The positional {@code MetaCodec} merge below is then skipped.</li>
 *   <li>Dynamic property flags are unioned; additional configuration such as dynamic
 *       resolver / redirect / multipart is overridden from superclass fragment by
 *       subclass fragment.</li>
 *   <li>When merging multiple fragments each fragment must carry a MetaCodec (otherwise
 *       their respective meta bit fields cannot be expressed); if one is missing,
 *       {@link #resolve} returns {@code null} and warns, and the caller falls back to an
 *       explicit {@code .states(...)}.</li>
 * </ul>
 *
 * <p>Reference scenario: a mod block {@code MyLogBlock extends BlockRotatedPillar}
 * (the vanilla pillar base class, with axis encoded in meta bits 2-3 and the low 2 bits
 * left to the subclass). After CatFrame's built-in table registers a {@code [axis]}
 * fragment for {@code BlockRotatedPillar}, MyLogBlock automatically gains the
 * {@code axis} property and its meta decoding, with no redeclaration.
 */
@SideOnly(Side.CLIENT)
public final class BlockStateInheritance {

    private BlockStateInheritance() {
    }

    /** Base-class fragment registry: block class → state definition fragment (order-preserving, for inheritance-chain traversal). */
    private static final Map<Class<? extends Block>, Fragment> BASE_FRAGMENTS = new LinkedHashMap<>();

    // ==================== Registration API ====================

    /**
     * Begins registering a state definition fragment for a block base class.
     *
     * @param clazz the base class type (e.g. {@code BlockRotatedPillar.class})
     * @return a chainable configurator
     */
    public static BaseSpec registerBase(Class<? extends Block> clazz) {
        return new BaseSpec(clazz);
    }

    // ==================== Chainable Configurators ====================

    public static final class BaseSpec {
        private final Class<? extends Block> clazz;
        private BlockStateDefinition<?> def;
        @Nullable
        private IMetadataBlockstateRedirect redirect;
        @Nullable
        private String redirectNamespace;
        @Nullable
        private ResidentStateModel.DynamicPropertyResolver dynamic;
        private boolean connectionMultipart = false;
        private boolean fullModel = true;

        private BaseSpec(Class<? extends Block> clazz) {
            this.clazz = clazz;
        }

        /** Sets the typed state definition fragment for this base class (properties + optional MetaCodec + optional dynamic properties). */
        public BaseSpec states(BlockStateDefinition<?> def) {
            this.def = def;
            return this;
        }

        /** Sets this base class's own per-meta blockstate redirect (e.g. multi-file blocks split by color). */
        public BaseSpec redirect(IMetadataBlockstateRedirect redirect, String namespace) {
            this.redirect = redirect;
            this.redirectNamespace = namespace;
            return this;
        }

        /** Sets this base class's runtime dynamic property resolver (stairs shape / pane connections etc.). */
        public BaseSpec dynamic(ResidentStateModel.DynamicPropertyResolver dynamic) {
            this.dynamic = dynamic;
            return this;
        }

        /** Enables pane connection multipart mode (per-face merged baking, isFullModel=false). */
        public BaseSpec connectionMultipart() {
            this.connectionMultipart = true;
            this.fullModel = false;
            return this;
        }

        /** Overrides isFullModel (defaults to true; multipart decorations may set it to false). */
        public BaseSpec fullModel(boolean fullModel) {
            this.fullModel = fullModel;
            return this;
        }

        /** Completes registration: stores into {@link #BASE_FRAGMENTS} for inheritance-chain resolution. */
        public void register() {
            if (def == null) {
                throw new IllegalStateException(
                        "Base fragment for " + clazz.getName() + " requires .states(...) before register()");
            }
            BASE_FRAGMENTS.put(clazz, new Fragment(def, redirect, redirectNamespace,
                    dynamic, connectionMultipart, fullModel));
        }
    }

    // ==================== Inheritance Resolution ====================

    /**
     * Resolves the merged state definition and additional configuration along a block's
     * inheritance chain.
     * <p>
     * Traverses upward from {@code block.getClass()} (excluding {@link Object}), collects
     * all registered base-class fragments, and merges properties / MetaCodec / dynamic
     * flags / additional configuration with "superclass first, subclass last", building a
     * {@link BlockStateDefinition} whose owner is the block.
     *
     * @param block the target block instance
     * @return the merge result; {@code null} when there is no base-class fragment on the
     *         inheritance chain (or a multi-fragment combination lacks a MetaCodec)
     */
    @Nullable
    public static Inherited resolve(Block block) {
        // 1. Collect fragments along the inheritance chain (child→parent), reverse to parent→child
        List<Fragment> fragments = new ArrayList<>();
        for (Class<?> c = block.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Fragment f = BASE_FRAGMENTS.get(c);
            if (f != null) fragments.add(f);
        }
        if (fragments.isEmpty()) return null;
        Collections.reverse(fragments);

        // 2. Merge properties: LinkedHashMap dedupes by name — keeps the first-appearance position, definition taken from the subclass (put later)
        LinkedHashMap<String, Property<?>> propMap = new LinkedHashMap<>();
        LinkedHashSet<String> dynamicNames = new LinkedHashSet<>();
        for (Fragment f : fragments) {
            for (Property<?> p : f.properties) {
                propMap.put(p.getName(), p);
            }
            for (Property<?> p : f.dynamic) {
                dynamicNames.add(p.getName());
            }
        }
        List<Property<?>> mergedProps = new ArrayList<>(propMap.values());

        // 3. Merge meta decoding.
        //    Distributed byte mapping (preferred): if every static (non-dynamic) merged property
        //    carries its own decode (MetaBlockProperty), inheritance needs no whole-definition codec
        //    at all — each property reads only its own bit field from meta, so combining fragments is a
        //    plain union and multi-fragment merging works without positional splicing.
        boolean perPropertyMeta = false;
        for (Property<?> p : mergedProps) {
            if (!dynamicNames.contains(p.getName())) {
                perPropertyMeta = p instanceof MetaBlockProperty;
                break;
            }
        }
        if (perPropertyMeta) {
            for (Property<?> p : mergedProps) {
                if (!dynamicNames.contains(p.getName()) && !(p instanceof MetaBlockProperty)) {
                    perPropertyMeta = false;
                    break;
                }
            }
        }

        BlockStateDefinition.MetaCodec mergedCodec = null;
        if (!perPropertyMeta) {
            // Legacy positional path: concatenate each fragment's output + reorder by final static order.
            // With multiple fragments and any fragment missing a codec, the bit fields cannot be
            // expressed -> return null and let the caller fall back.
            mergedCodec = mergeCodecs(fragments, mergedProps, dynamicNames);
            if (mergedCodec == null && fragments.size() > 1) {
                CatFrame.logger.warn(
                        "[CatStateInheritance] {} inherits from {} base fragment(s), but a fragment lacks a "
                                + "MetaCodec so meta decoding cannot be combined. Register the block with an explicit "
                                + ".states(...) instead.",
                        block.getClass().getName(), fragments.size());
                return null;
            }
        }

        // 4. Build the merged BlockStateDefinition (owner = block instance)
        BlockStateDefinition.Builder<Block> builder = new BlockStateDefinition.Builder<>(block);
        for (Property<?> p : mergedProps) {
            builder.add(p);
        }
        for (Property<?> p : mergedProps) {
            if (dynamicNames.contains(p.getName())) {
                builder.dynamic(p);
            }
        }
        if (mergedCodec != null) {
            builder.metaCodec(mergedCodec);
        }

        // 5. Additional configuration: subclass fragments override superclass fragments (fragments are already in parent→child order)
        IMetadataBlockstateRedirect redirect = null;
        String redirectNamespace = null;
        ResidentStateModel.DynamicPropertyResolver dynamicResolver = null;
        boolean connectionMultipart = false;
        boolean fullModel = true;
        for (Fragment f : fragments) {
            if (f.redirect != null) {
                redirect = f.redirect;
                redirectNamespace = f.redirectNamespace;
            }
            if (f.dynamicResolver != null) {
                dynamicResolver = f.dynamicResolver;
            }
            if (f.connectionMultipart) {
                connectionMultipart = true;
                fullModel = false;
            }
            fullModel = f.fullModel;
        }

        return new Inherited(builder.create(), redirect, redirectNamespace,
                dynamicResolver, connectionMultipart, fullModel);
    }

    /**
     * Materializes inheritance as declaration: returns the existing global-table entry
     * when present, honors an explicit {@link IBlockStateProvider#getStateDefinition()}
     * override, otherwise merges base-class fragments along the class hierarchy.
     * Idempotent; returns {@code null} when no source applies.
     *
     * @param block the target block instance
     * @return the registered (or just-registered) typed state definition; {@code null} when no source applies
     */
    @Nullable
    public static BlockStateDefinition<?> resolveAndRegister(Block block) {
        BlockStateDefinition<?> existing = ModelRegistry.getStateDefinition(block);
        if (existing != null) return existing;

        // Prefer an explicit provider override of getStateDefinition() (the interface-declared typed hook)
        if (block instanceof IBlockStateProvider) {
            BlockStateDefinition<?> declared = ((IBlockStateProvider) block).getStateDefinition();
            if (declared != null) {
                ModelRegistry.registerStateDefinition(block, declared);
                return declared;
            }
        }

        Inherited inherited = resolve(block);
        if (inherited == null) return null;
        ModelRegistry.registerStateDefinition(block, inherited.def);
        CatFrame.logger.info("[CatStateInheritance] inherited state definition for {} from base class chain",
                block.getClass().getName());
        return inherited.def;
    }

    /**
     * Merges the MetaCodec of each fragment: outputs the static property values
     * concatenated in fragment order, then reorders by the final static property order.
     * Returns {@code null} when any fragment lacks a codec (a single fragment lacking a
     * codec means that fragment as a whole uses the default cartesian-product decoding).
     */
    @Nullable
    private static BlockStateDefinition.MetaCodec mergeCodecs(List<Fragment> fragments,
                                                              List<Property<?>> mergedProps,
                                                              Set<String> dynamicNames) {
        // Compute concatenation offsets and collect all codecs
        int spliceLen = 0;
        int[] offsets = new int[fragments.size()];
        BlockStateDefinition.MetaCodec[] codecs = new BlockStateDefinition.MetaCodec[fragments.size()];
        for (int i = 0; i < fragments.size(); i++) {
            BlockStateDefinition.MetaCodec codec = fragments.get(i).codec;
            if (codec == null) return null;
            offsets[i] = spliceLen;
            codecs[i] = codec;
            spliceLen += fragments.get(i).staticProps.length;
        }

        // Final static property order (excluding dynamic properties)
        List<Property<?>> mergedStatic = new ArrayList<>();
        for (Property<?> p : mergedProps) {
            if (!dynamicNames.contains(p.getName())) {
                mergedStatic.add(p);
            }
        }

        // Remap: final static index → concatenated output index.
        // When a same-name property is declared by multiple fragments, the later-traversed fragment (subclass) overrides the earlier mapping.
        int[] remap = new int[mergedStatic.size()];
        for (int fi = 0; fi < fragments.size(); fi++) {
            Fragment f = fragments.get(fi);
            Map<String, Integer> staticIdx = new HashMap<>();
            for (int si = 0; si < f.staticProps.length; si++) {
                staticIdx.put(f.staticProps[si].getName(), si);
            }
            for (int msi = 0; msi < mergedStatic.size(); msi++) {
                Integer si = staticIdx.get(mergedStatic.get(msi).getName());
                if (si != null) {
                    remap[msi] = offsets[fi] + si;
                }
            }
        }

        final int finalSpliceLen = spliceLen;
        return meta -> {
            Comparable<?>[] splice = new Comparable<?>[finalSpliceLen];
            for (int i = 0; i < codecs.length; i++) {
                Comparable<?>[] part = codecs[i].decode(meta);
                System.arraycopy(part, 0, splice, offsets[i], part.length);
            }
            Comparable<?>[] out = new Comparable<?>[remap.length];
            for (int i = 0; i < remap.length; i++) {
                out[i] = splice[remap[i]];
            }
            return out;
        };
    }

    // ==================== Fragments / Results ====================

    /**
     * Immutable base-class fragment: extracts properties / MetaCodec / dynamic flags from a
     * {@link BlockStateDefinition}, plus additional model-behavior configuration
     * (redirect / dynamic resolver / multipart).
     */
    private static final class Fragment {
        final Property<?>[] properties;
        final Property<?>[] staticProps;
        final Property<?>[] dynamic;
        @Nullable
        final BlockStateDefinition.MetaCodec codec;
        @Nullable
        final IMetadataBlockstateRedirect redirect;
        @Nullable
        final String redirectNamespace;
        @Nullable
        final ResidentStateModel.DynamicPropertyResolver dynamicResolver;
        final boolean connectionMultipart;
        final boolean fullModel;

        Fragment(BlockStateDefinition<?> def,
                 @Nullable IMetadataBlockstateRedirect redirect,
                 @Nullable String redirectNamespace,
                 @Nullable ResidentStateModel.DynamicPropertyResolver dynamicResolver,
                 boolean connectionMultipart,
                 boolean fullModel) {
            this.properties = def.getProperties();
            this.codec = def.getMetaCodec();
            List<Property<?>> dyn = new ArrayList<>();
            List<Property<?>> stat = new ArrayList<>();
            for (Property<?> p : properties) {
                if (def.isDynamic(p)) {
                    dyn.add(p);
                } else {
                    stat.add(p);
                }
            }
            this.dynamic = dyn.toArray(new Property<?>[0]);
            this.staticProps = stat.toArray(new Property<?>[0]);
            this.redirect = redirect;
            this.redirectNamespace = redirectNamespace;
            this.dynamicResolver = dynamicResolver;
            this.connectionMultipart = connectionMultipart;
            this.fullModel = fullModel;
        }
    }

    /**
     * Inheritance resolution result: the merged {@link BlockStateDefinition} plus additional
     * model-behavior configuration.
     * <p>
     * Consumed by {@link CatModels.Spec} / {@code ModernBlock.register}: fields not
     * explicitly configured are filled from the inheritance result, while explicitly
     * configured fields keep priority.
     */
    public static final class Inherited {
        /** The merged typed state definition (owner = block instance, non-null). */
        public final BlockStateDefinition<?> def;
        @Nullable
        public final IMetadataBlockstateRedirect redirect;
        @Nullable
        public final String redirectNamespace;
        @Nullable
        public final ResidentStateModel.DynamicPropertyResolver dynamic;
        public final boolean connectionMultipart;
        public final boolean fullModel;

        Inherited(BlockStateDefinition<?> def,
                  @Nullable IMetadataBlockstateRedirect redirect,
                  @Nullable String redirectNamespace,
                  @Nullable ResidentStateModel.DynamicPropertyResolver dynamic,
                  boolean connectionMultipart,
                  boolean fullModel) {
            this.def = def;
            this.redirect = redirect;
            this.redirectNamespace = redirectNamespace;
            this.dynamic = dynamic;
            this.connectionMultipart = connectionMultipart;
            this.fullModel = fullModel;
        }
    }
}
