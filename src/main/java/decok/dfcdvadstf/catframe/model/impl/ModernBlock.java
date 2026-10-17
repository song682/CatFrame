package decok.dfcdvadstf.catframe.model.impl;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.Models;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.RenderJsonBlockModel;
import decok.dfcdvadstf.catframe.model.state.*;
import decok.dfcdvadstf.catframe.model.state.BlockStateDefinition;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel;
import decok.dfcdvadstf.catframe.model.state.block.StateProviderBlockModel;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import java.util.Collections;
import java.util.Map;

/**
 * ModernBlock — CatFrame Mod Blocks Base class
 *
 * <p>Similar to {@link ModernItem}, it gives blocks one-step access to the CatFrame
 * blockstate JSON model pipeline:
 * <ul>
 *   <li>Specify the blockstate JSON path via {@link #setBlockstate(String, String)} or
 *       {@link #setBlockstate(String)}; when not set explicitly, it is derived from the
 *       block registry name</li>
 *   <li>Inheritance as access: once registered in the game registry it is discovered
 *       automatically during texture stitching — loading the blockstate, collecting
 *       textures, and registering a typed state definition along the inheritance chain
 *       (see {@link BlockStateInheritance}), with no manual call to
 *       {@link #register(ModernBlock)}</li>
 *   <li>{@link #register(ModernBlock)} is an optional explicit entry: immediate access +
 *       allocation of an ISBRH renderType on the client</li>
 *   <li>Renders the "normal" variant by default; subclasses may override
 *       {@link #getStateProperties} for dynamic property matching</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * public class MyBlock extends ModernBlock {
 *     public MyBlock() {
 *         super(Material.rock);
 *         setBlockName("my_block");
 *         setBlockTextureName("catframe:my_block");
 *         setBlockstate("catframe", "my_block");
 *     }
 * }
 *
 * // Registering to the game registry in preInit completes access (auto-discovery takes over):
 * GameRegistry.registerBlock(myBlock, "my_block");
 * }</pre>
 */
public class ModernBlock extends Block implements IBlockStateProvider {

    /** Default blockstate namespace, consistent with the vanilla resource pack directory. */
    public static final String DEFAULT_BLOCKSTATE_NAMESPACE = "minecraft";

    /** Explicitly specified blockstate namespace. An empty string means unset; registration then tries to derive it from the registry name. */
    protected String blockstateNamespace = "";

    /** Explicitly specified blockstate file name (without .json). An empty string means unset; registration then tries to derive it from the registry name. */
    protected String blockstateName = "";

    /** ISBRH renderType ID, allocated by {@link #register(ModernBlock)} on the client. */
    protected int modernRenderType = 0;

    /**
     * v0.3.0+ typed state definition obtained from automatic inheritance resolution
     * (populated during {@link #register(ModernBlock)}).
     * <p>
     * Subclasses need not assign it manually: when not configured explicitly, base-class
     * fragments are merged along the inheritance chain (see {@link BlockStateInheritance})
     * for use by the render pipeline and blockstate key validation. When register() is not
     * called this field stays {@code null}; the pipeline-side resolution result is recorded
     * in the global {@link ModelRegistry} table and read together by
     * {@link #getStateDefinition()}.
     */
    private BlockStateDefinition<?> stateDefinition;

    protected ModernBlock(Material material) {
        super(material);
    }

    /**
     * Sets the namespace and file name of the blockstate JSON.
     *
     * @param namespace resource pack namespace, e.g. "catframe"
     * @param name      blockstate file name (without .json), e.g. "my_block"
     * @return this
     */
    public ModernBlock setBlockstate(String namespace, String name) {
        this.blockstateNamespace = namespace == null ? "" : namespace;
        this.blockstateName = name == null ? "" : name;
        return this;
    }

    /**
     * Sets the blockstate file name using the default namespace {@link #DEFAULT_BLOCKSTATE_NAMESPACE}.
     *
     * @param name blockstate file name (without .json)
     * @return this
     */
    public ModernBlock setBlockstate(String name) {
        return setBlockstate(DEFAULT_BLOCKSTATE_NAMESPACE, name);
    }

    @Override
    public ModernBlock setBlockName(String name) {
        super.setBlockName(name);
        return this;
    }

    @Override
    public ModernBlock setBlockTextureName(String name) {
        super.setBlockTextureName(name);
        return this;
    }

    @Override
    public ModernBlock setCreativeTab(CreativeTabs tab) {
        super.setCreativeTab(tab);
        return this;
    }

    @Override
    public String getBlockstateNamespace() {
        return blockstateNamespace;
    }

    @Override
    public String getBlockstateName() {
        return blockstateName;
    }

    @Override
    public BlockStateDefinition<?> getStateDefinition() {
        // When register() was not called, the pipeline auto-resolves the typed
        // definition into the global registry — read it as the fallback source.
        return stateDefinition != null ? stateDefinition : ModelRegistry.getStateDefinition(this);
    }

    /**
     * Dynamic property mapping. Returns an empty map by default (matches the "normal"
     * variant in the blockstate JSON).
     * <p>
     * Subclasses may override this method to return property key-value pairs based on the
     * world state / metadata, for example:
     * <pre>{@code
     * Map<String, String> props = new HashMap<>();
     * props.put("facing", EnumFacing.getFront(metadata).name());
     * return props;
     * }</pre>
     */
    @Override
    public Map<String, String> getStateProperties(IBlockAccess world, int x, int y, int z, int metadata) {
        return Collections.emptyMap();
    }

    @Override
    public int getRenderType() {
        return modernRenderType;
    }

    /**
     * Registers a ModernBlock into the CatFrame pipeline.
     * <p>
     * This method will:
     * <ol>
     *   <li>try to derive the blockstate path from the block registry name if not set explicitly</li>
     *   <li>load the blockstate JSON via {@link ModelManagerDataLoader#registerBlock(Block)}</li>
     *   <li>register a {@link BlockStateModel} so ItemBlock can use the same model in the
     *       inventory/hand</li>
     *   <li>register the ISBRH on the client and obtain/set the renderType ID</li>
     * </ol>
     *
     * <p>Registration timing is relaxed: discovery is driven by texture stitching (executed
     * incrementally on each {@code TextureStitchEvent.Pre}), and registry blocks implementing
     * IBlockStateProvider are also discovered automatically; the blockstate loads at the next
     * stitch at the latest (calling after the first discovery pass completes loads it
     * immediately). The renderType can only be obtained on the client.
     *
     * <p>Optional: discovery covers the same data loading and typed resolution without it;
     * calling it additionally front-loads registration and assigns an ISBRH renderType.
     *
     * @param block the ModernBlock instance to register
     * @return the allocated ISBRH renderType ID; -1 on the server
     */
    public static int register(ModernBlock block) {
        resolveBlockstateFromRegistry(block);

        if (block.blockstateName.isEmpty()) {
            CatFrame.logger.warn(
                    "[ModernBlock] blockstate not configured and could not be derived from registry name for {}. " +
                    "Call setBlockstate() before register(), or register the block with GameRegistry before ModernBlock.register().",
                    block.getClass().getName());
        }

        ModelManagerDataLoader.registerBlock(block);

        // Auto inheritance: merge base fragments (e.g. PillarBlock's axis) along the
        // class hierarchy when no explicit typed def exists.
        if (block.stateDefinition == null && !ModelRegistry.hasStateDefinition(block)) {
            BlockStateInheritance.Inherited inherited = BlockStateInheritance.resolve(block);
            if (inherited != null) {
                block.stateDefinition = inherited.def;
                ModelRegistry.registerStateDefinition(block, block.stateDefinition);
                CatFrame.logger.info("[ModernBlock] inherited state definition for {} from base class chain",
                        block.getClass().getName());
            }
        }

        // Register a BlockStateModel for ItemBlock rendering fallback
        ModelRegistry.registerBlockModel(block, new LazyStateProviderBlockModel(block));

        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            int id = RenderJsonBlockModel.register(block);
            block.modernRenderType = id;
            return id;
        }
        return -1;
    }

    /**
     * Convenience registration entry for any Block: automatic BlockState inheritance +
     * the declarative Models pipeline.
     * <p>
     * Blocks that are not {@link ModernBlock} (e.g. a PillarBlock extension directly
     * inheriting vanilla {@code BlockRotatedPillar}) need not implement
     * {@link IBlockStateProvider}: this method derives and registers the blockstate
     * namespace from the registry name, then runs the {@link Models#register(Block)}
     * pipeline (including inheritance resolution); the blockstate JSON is loaded and
     * materialized at the next texture stitch at the latest.
     *
     * @param block any block instance
     * @return the client-allocated ISBRH renderType ID; -1 for non-{@link ModernBlock}
     */
    public static int register(Block block) {
        if (block instanceof ModernBlock) {
            return register((ModernBlock) block);
        }
        // Derive and register the blockstate namespace (Models.materialize depends on loaded blockstates)
        String registryName = Block.blockRegistry.getNameForObject(block);
        if (registryName != null) {
            int colon = registryName.indexOf(':');
            String ns = colon > 0 ? registryName.substring(0, colon) : DEFAULT_BLOCKSTATE_NAMESPACE;
            ModelManagerDataLoader.registerNamespace(ns);
        }
        Models.register(block).register();
        return -1;
    }

    /**
     * Derives the namespace and name from the block registry name if the blockstate path
     * is not set explicitly. The registry name format is "namespace:name"; without a
     * namespace it falls back to {@link #DEFAULT_BLOCKSTATE_NAMESPACE}.
     */
    private static void resolveBlockstateFromRegistry(ModernBlock block) {
        if (block.blockstateName.isEmpty()) {
            String registryName = Block.blockRegistry.getNameForObject(block);
            if (registryName != null && !registryName.isEmpty()) {
                int colon = registryName.indexOf(':');
                if (colon >= 0) {
                    block.blockstateNamespace = registryName.substring(0, colon);
                    block.blockstateName = registryName.substring(colon + 1);
                } else {
                    block.blockstateNamespace = DEFAULT_BLOCKSTATE_NAMESPACE;
                    block.blockstateName = registryName;
                }
            }
        }
    }

    /**
     * BlockStateModel that resolves the blockstate lazily.
     * <p>
     * Lets {@link #register(ModernBlock)} pre-register a model even when the blockstate
     * JSON is not yet loaded; lazy-loads from
     * {@link ModelManagerDataLoader#getBlockstateData(Block)} on first render.
     */
    private static class LazyStateProviderBlockModel implements BlockStateModel {
        private final IBlockStateProvider provider;
        private BlockStateModel delegate;

        LazyStateProviderBlockModel(IBlockStateProvider provider) {
            this.provider = provider;
        }

        private BlockStateModel getDelegate() {
            if (delegate == null) {
                Block block = (Block) provider;
                BlockstateJson bs = ModelManagerDataLoader.getBlockstateData(block);
                if (bs != null) {
                    // When a typed state definition exists (explicit .states(...) or obtained
                    // via inheritance resolution), use ResidentStateModel's property-driven
                    // matching; otherwise fall back to the provider's dynamic properties.
                    BlockStateDefinition<?> def = ModelRegistry.getStateDefinition(block);
                    if (def != null) {
                        delegate = ResidentStateModel.builder(block)
                                        .blockstate(bs)
                                        .stateDefinition(def)
                                        .build();
                    } else {
                        delegate = new StateProviderBlockModel(provider, bs, null);
                    }
                }
            }
            return delegate;
        }

        @Override
        public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
            BlockStateModel model = getDelegate();
            return model != null
                    ? model.collectParts(world, x, y, z, metadata)
                    : BlockStateModelPart.empty();
        }

        /**
         * Particle icon resolution: delegates to the lazily built delegate (isomorphic to
         * collectParts); when the delegate is not ready (blockstate not loaded) returns
         * null, which ParticleIconResolver maps to missingno following high-version
         * semantics.
         */
        @Override
        public IIcon particleIcon(int metadata) {
            BlockStateModel model = getDelegate();
            return model != null ? model.particleIcon(metadata) : null;
        }
    }
}
