package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.CatStateDefinition;
import decok.dfcdvadstf.catframe.model.state.CatStateInheritance;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified block/item model registration facade (chainable API).
 * <p>
 * Centralizes the registration of "typed state definitions → resident block models + item decision trees",
 * replacing scattered {@code registerBlockstateRedirect} calls and manual model class instantiation.
 *
 * <h3>Usage</h3>
 * 
 * <pre>{@code
 * CatModels.register(Blocks.wool)
 *     .states(StateDefinitions colorDef)   // typed resident table
 *     .itemFromBlockstate()                // export item damage decision tree from 16 static states
 *     .register();
 *
 * CatModels.register(stairsBlock)
 *     .states(stairsDef)                   // facing/half + dynamic shape
 *     .dynamic(stairsShapeResolver)        // runtime corner shape
 *     .register();
 * }</pre>
 *
 * <h3>Timing</h3>
 * {@link #register()} only registers declarative {@link CatModelSpec}s; actual blockstate JSON
 * materialization is deferred to {@link #materialize()} (invoked by
 * {@code VanillaModelManager.Baking.registerAllModels}
 * after blockstate loading completes).
 */
@SideOnly(Side.CLIENT)
public final class CatModels {

    private CatModels() {
    }

    /** preInit-registered specs (ordered for materialization traversal). */
    public static final Map<Block, CatModelSpec> SPECS = new LinkedHashMap<>();

    /**
     * Start registering a block's model.
     *
     * @param block target block
     * @return chainable configurator
     */
    public static Spec register(Block block) {
        return new Spec(block);
    }

    /**
     * Start registering a block base class state definition fragment (automatic BlockState inheritance).
     * <p>
     * After registration, any block inheriting this base class automatically merges base properties /
     * MetaCodec / dynamic attributes at {@link Spec#register()}, no manual duplication needed.
     * Built-in vanilla base class table see {@code VanillaStateDefinitions.registerVanillaBaseClasses()}.
     *
     * @param clazz base class type (e.g. {@code BlockRotatedPillar.class})
     * @return chainable configurator
     */
    public static CatStateInheritance.BaseSpec registerBase(Class<? extends Block> clazz) {
        return CatStateInheritance.registerBase(clazz);
    }

    // ==================== Chainable configurator ====================

    public static final class Spec {
        private final CatModelSpec spec;

        private Spec(Block block) {
            this.spec = new CatModelSpec(block);
        }

        /** Set the typed resident state table (property-driven variant matching). */
        public Spec states(CatStateDefinition<?> def) {
            spec.def = def;
            return this;
        }

        /** Set per-meta blockstate redirect (e.g. multi-file blocks split by color). */
        public Spec redirect(IMetadataBlockstateRedirect redirect, String namespace) {
            spec.redirect = redirect;
            spec.redirectNamespace = namespace;
            return this;
        }

        /** Set runtime dynamic property resolver (stairs shape / pane connections etc.). */
        public Spec dynamic(ResidentStateModel.DynamicPropertyResolver dynamic) {
            spec.dynamic = dynamic;
            return this;
        }

        /** Enable pane connection multipart mode (per-face merged baking, isFullModel=false). */
        public Spec connectionMultipart() {
            spec.connectionMultipart = true;
            spec.fullModel = false;
            return this;
        }

        /** Override isFullModel (default true; multipart decorative pieces can set to false). */
        public Spec fullModel(boolean fullModel) {
            spec.fullModel = fullModel;
            return this;
        }

        /** Register a fixed Y rotation (degrees) for a given meta. */
        public Spec rotation(int meta, int degrees) {
            ModelRegistry.registerBlockRotation(spec.block, meta, degrees);
            return this;
        }

        /** Mark this block as using position-based random Y rotation. */
        public Spec randomRotation() {
            ModelRegistry.markRandomRotation(spec.block);
            return this;
        }

        /** Export item {@code damage} decision tree from 16 static blockstate states. */
        public Spec itemFromBlockstate() {
            spec.itemFromBlockstate = true;
            return this;
        }

        /** Complete registration: store in {@link #SPECS} and register typed state definition. */
        public void register() {
            // Auto inheritance: when no explicit typed def is set, merge base fragments
            // from the class hierarchy; materialize() retries for late-registered bases.
            if (spec.def == null) {
                CatStateInheritance.Inherited inherited = CatStateInheritance.resolve(spec.block);
                if (inherited != null) {
                    applyInherited(spec, inherited);
                }
            }
            SPECS.put(spec.block, spec);
            if (spec.def != null) {
                ModelRegistry.registerStateDefinition(spec.block, spec.def);
            }
            // Register the redirect here as well: init()'s preload loop only walks
            // blockstateRedirects — without this, redirect targets (e.g. the 16
            // per-color stained glass panes) are never preloaded before the atlas
            // stitch, and lazy loads at render time are already too late.
            if (spec.redirect != null) {
                ModelManagerDataLoader.registerBlockstateRedirect(spec.block, spec.redirect);
            }
        }
    }

    // ==================== Materialization ====================

/**
     * Apply inheritance resolution to spec (only fill unset fields; explicit config takes priority).
     * <p>
     * Inherited connectionMultipart and fullModel cannot distinguish "explicitly set" from "default",
     * so use conservative merge: base class enabling multipart / disabling fullModel overrides defaults,
     * reverse direction does not override.
     */
    private static void applyInherited(CatModelSpec spec, CatStateInheritance.Inherited inherited) {
        spec.def = inherited.def;
        if (spec.dynamic == null) {
            spec.dynamic = inherited.dynamic;
        }
        if (spec.redirect == null && inherited.redirect != null) {
            spec.redirect = inherited.redirect;
            spec.redirectNamespace = inherited.redirectNamespace;
        }
        if (!spec.connectionMultipart && inherited.connectionMultipart) {
            spec.connectionMultipart = true;
            spec.fullModel = false;
        } else if (spec.fullModel && !inherited.fullModel) {
            spec.fullModel = false;
        }
    }

    /**
     * Materialize all registered specs into resident block models and item decision trees.
     * <p>
     * Called after blockstate JSON loading completes ({@code registerAllModels}).
     * Registered model blocks are overridden with resident models; blocks with
     * {@code itemFromBlockstate} export item decision trees and are marked persistent,
     * taking priority over {@code items/{name}.json} convention matching.
     *
     * @return number of successfully materialized block models
     */
    public static int materialize() {
        int count = 0;
        for (CatModelSpec spec : SPECS.values()) {
            // Fallback inheritance resolution: base fragments may be registered after Spec.register()
            // (e.g. mod preInit earlier than CatFrame's internal base class table), catch up here.
            if (spec.def == null) {
                CatStateInheritance.Inherited inherited = CatStateInheritance.resolve(spec.block);
                if (inherited != null) {
                    applyInherited(spec, inherited);
                    ModelRegistry.registerStateDefinition(spec.block, spec.def);
                    if (spec.redirect != null) {
                        ModelManagerDataLoader.registerBlockstateRedirect(spec.block, spec.redirect);
                    }
                    CatFrame.logger.info("CatModels: inherited state definition for {} from base class chain",
                            Block.blockRegistry.getNameForObject(spec.block));
                }
            }
            Block block = spec.block;
            String registryName = Block.blockRegistry.getNameForObject(block);
            String namespace = "minecraft";
            String name = registryName;
            if (registryName != null && registryName.contains(":")) {
                int c = registryName.indexOf(':');
                namespace = registryName.substring(0, c);
                name = registryName.substring(c + 1);
            }

            BlockstateJson bs = null;
            Map<String, BlockstateJson> nsMap = ModelManagerDataLoader.loadedBlockstates.get(namespace);
            if (nsMap != null && name != null)
                bs = nsMap.get(name);

            // Resident block model (redirect mode allows bs to be null)
            ResidentStateModel model = spec.buildBlockModel(bs);
            ModelRegistry.registerBlockModel(block, model);
            count++;

            // Item decision tree (optional)
            if (spec.itemFromBlockstate) {
                ItemStateNode node = spec.buildItemNode(bs);
                Item item = Item.getItemFromBlock(block);
                if (node != null && item != null) {
                    ModelRegistry.registeredItemModels.put(item, new ItemStateModel(node));
                    ModelRegistry.persistentItemModels.add(item);
                }
            }
        }
        if (count > 0) {
            CatFrame.logger.info("CatModels: materialized {} resident block models", count);
        }
        return count;
    }
}
