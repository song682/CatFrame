package decok.dfcdvadstf.catframe.model.impl;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.item.EvalResult;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import decok.dfcdvadstf.catframe.model.state.property.ItemProperties;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import java.util.*;

/**
 * ModernItem — extended Item with two key features over vanilla Item:
 *
 * <h3>1. Multi-layer texture rendering</h3>
 * Subclasses can specify any number (3+) of rendering passes via
 * {@link #setLayerCount(int)} / {@link #setLayerTextureNames(String...)}.
 * Vanilla only supports 1 or 2 passes; ModernItem supports N passes.
 * Each pass renders a separate full-brightness flat quad in the GUI.
 *
 * <h3>2. Dual-model rendering (2D inventory + 3D handheld)</h3>
 * Use {@link #setModels(String, String)} to specify
 * separate 2D (inventory) and 3D (handheld) model paths.
 * Internally an {@link ItemStateNode} decision tree is built which
 * automatically dispatches:
 * <ul>
 * <li>GUI / dropped item → 2D inventory model (builtin/generated, with side
 * thickness)</li>
 * <li>First/third person hand → 3D handheld model</li>
 * </ul>
 * When only one model is set, that model is used for all phases.
 *
 * <h3>Model system compatibility</h3>
 * ModernItem is a plain {@link Item} subclass — it works transparently
 * with {@link VanillaModelManager} and the existing JSON model pipeline.
 */
public class ModernItem extends Item implements IItemStateProvider {

    /**
     * Icons for each render layer, populated during {@link #registerIcons}.
     */
    @SideOnly(Side.CLIENT)
    protected IIcon[] layerIcons;

    /**
     * Texture name strings for each layer, set by subclasses before registration.
     */
    protected String[] layerIconNames;

    /**
     * Number of render passes (default 1).
     */
    protected int layerCount;

    // ==================== Dual-model configuration ====================

    /**
     * 2D inventory model path (e.g. "item/bluey_inventory"), used for
     * GUI and dropped item rendering.
     * Set by {@link #setModels(String, String)}.
     */
    protected String inventoryModelPath;

    /**
     * 3D handheld model path (e.g. "item/bluey"), used for first/third
     * person hand rendering.
     * Set by {@link #setModels(String, String)}.
     */
    protected String handModelPath;

    /**
     * ItemState decision tree root node.
     * Built by {@link #setModels(String, String)} from single or dual model config.
     */
    protected ItemStateNode itemStateRoot;

    // ==================== Constructors ====================

    public ModernItem() {
        this(1);
    }

    /**
     * @param layers number of render passes (≥ 1). Values &lt; 1 are clamped to 1.
     */
    public ModernItem(int layers) {
        this.layerCount = Math.max(1, layers);
        this.layerIcons = new IIcon[this.layerCount];
        this.layerIconNames = new String[this.layerCount];
        // Keep isFull3D = true. RenderJsonItemModel.computePreTransform() consumes it
        // in the
        // RenderBiped branch (EQUIPPED, non-player entity, non-ItemBlock): isFull3D
        // picks the
        // full3D counter-transform vs the 2D fallback. Player-held / item-frame /
        // dropped-item
        // phases never read it, so removing this looks safe but silently mis-positions
        // mob-held items.
        // Keep isFull3D = true: RenderJsonItemModel.computePreTransform() consumes it in
        // (third person, non-player entity, non-ItemBlock) depends on it to choose
        // Player-held / item-frame / dropped-item phases never read it — removing looks safe but silently mis-positions mob-held items.
        this.setFull3D();
    }

    // ==================== Layer configuration ====================

    /**
     * @return number of render passes (layers) this item uses.
     */
    public int getLayerCount() {
        return layerCount;
    }

    /**
     * Change the number of render passes at runtime.
     * Must be called <b>before</b> {@link #registerIcons} to have effect.
     */
    protected void setLayerCount(int layers) {
        if (layers < 1)
            layers = 1;
        this.layerCount = layers;
        this.layerIcons = new IIcon[this.layerCount];
        this.layerIconNames = new String[this.layerCount];
    }

    /**
     * Assign texture names for each layer and update the layer count.
     * Also forwards the first name to {@link #setTextureName(String)}
     * so vanilla tooling (e.g. missing-icon fallback) keeps working.
     *
     * @param names one texture name per layer, e.g.
     *              {@code "catframe:items/sword_blade", "catframe:items/sword_handle"}
     * @return this
     */
    public ModernItem setLayerTextureNames(String... names) {
        if (names == null || names.length == 0)
            return this;
        this.layerCount = names.length;
        this.layerIcons = new IIcon[this.layerCount];
        this.layerIconNames = names;
        this.setTextureName(names[0]); // compatibility with vanilla iconString
        return this;
    }

    // ==================== Vanilla Item overrides ====================

    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return layerCount > 1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderPasses(int metadata) {
        return layerCount;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamageForRenderPass(int damage, int pass) {
        if (pass >= 0 && pass < layerIcons.length && layerIcons[pass] != null) {
            return layerIcons[pass];
        }
        return this.itemIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        if (pass >= 0 && pass < layerIcons.length && layerIcons[pass] != null) {
            return layerIcons[pass];
        }
        return this.itemIcon;
    }

    /**
     * Per-layer colour tint. Subclasses (e.g. dyed items) should override
     * this to return a different colour per pass. Default: opaque white.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public int getColorFromItemStack(ItemStack stack, int pass) {
        return 0xFFFFFF;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        for (int i = 0; i < layerCount; i++) {
            if (layerIconNames[i] != null && !layerIconNames[i].isEmpty()) {
                layerIcons[i] = register.registerIcon(layerIconNames[i]);
            }
        }
        // Keep vanilla itemIcon in sync with layer 0 for compatibility
        if (layerIcons[0] != null) {
            this.itemIcon = layerIcons[0];
        }
    }

    // ==================== Dual-model API ====================

    /**
     * Set separate 2D and 3D model paths for this item.
     *             <p>
     *             Set an internal {@link ItemStateNode} node：
     *             <ul>
     *             <li>GUI / looting stage（display_context = ITEM_GUI /
     *             DROPPED_ITEM_GROUND）→ 2D inventory model</li>
     *             <li>Handheld phase (display_context = ITEM_HAND_FIRST_PERSON /
     *             ITEM_HAND_THIRD_PERSON）→ 3D handheld model</li>
     *             </ul>
     *             If only a model is input, regressed to the single
     *             {@link ItemStateNode.ModelLeaf}.
     *             <p>
     *             Must be called <b>before</b>
     *             {@link ModelManagerDataLoader#init()}
     *             so that texture collection can pick up both models' textures.
     *             </p>
     *
     * @param inventoryModel 2D model path for GUI and dropped items (e.g.
     *                       "item/bluey_inventory")
     * @param handModel      3D model path for handheld rendering (e.g.
     *                       "item/bluey")
     * @return this
     */
    public ModernItem setModels(String inventoryModel, String handModel) {
        this.inventoryModelPath = inventoryModel;
        this.handModelPath = handModel;
        rebuildItemStateRoot();
        return this;
    }

    /**
     * Rebuild ItemState decision tree from current {@link #inventoryModelPath} / {@link #handModelPath}.
     */
    private void rebuildItemStateRoot() {
        if (inventoryModelPath == null && handModelPath == null) {
            this.itemStateRoot = null;
            return;
        }

        if (hasDualModels()) {
            ItemStateNode inventoryLeaf = new ItemStateNode.ModelLeaf(inventoryModelPath);
            ItemStateNode handLeaf = new ItemStateNode.ModelLeaf(handModelPath);

            List<ItemStateNode.SelectCase> cases = new ArrayList<>();
            // Handheld / item-frame 3D phases -> hand model
            // Handheld / item-frame 3D phases -> hand model
            cases.add(new ItemStateNode.SelectCase(new LinkedHashSet<>(Arrays.asList(
                    RenderPhase.ITEM_HAND_FIRST_PERSON.name(),
                    RenderPhase.ITEM_HAND_THIRD_PERSON.name(),
                    RenderPhase.ITEM_FIXED.name())), handLeaf));
            // GUI / dropped-item 2D phases -> inventory model
            // Dropped block items also use 2D inventory model (ModernItem itself is not an ItemBlock, but kept for compat)
            // GUI / dropped-item 2D phases -> inventory model
            cases.add(new ItemStateNode.SelectCase(new LinkedHashSet<>(Arrays.asList(
                    RenderPhase.ITEM_GUI.name(),
                    RenderPhase.DROPPED_ITEM_GROUND.name(),
                    RenderPhase.DROPPED_BLOCK_GROUND.name())), inventoryLeaf));

            this.itemStateRoot = new ItemStateNode.SelectNode(
                    ItemProperties.DISPLAY_CONTEXT.getName(), cases, inventoryLeaf);
        } else {
            String singlePath = inventoryModelPath != null ? inventoryModelPath : handModelPath;
            this.itemStateRoot = new ItemStateNode.ModelLeaf(singlePath);
        }
    }

    /**
     * Returns true if this item has dual-model configuration.
     */
    public boolean hasDualModels() {
        return inventoryModelPath != null && handModelPath != null;
    }

    // ==================== IItemState ====================

    @Override
    public boolean handles(RenderPhase phase) {
        return itemStateRoot != null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void render(ItemStack stack, RenderPhase phase) {
        if (itemStateRoot == null)
            return;

        Map<String, Comparable<?>> props = ItemProperties.buildProperties(stack, phase);
        EvalResult result = itemStateRoot.evaluate(props);
        if (result.isEmpty())
            return;

        for (String path : result.getModels()) {
            String cacheKey = BakedModelCache.buildKey(path, 0, 0);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            if (part == null || part.isEmpty())
                continue;
            UniformRenderPipeline.renderItemQuads(part, stack, phase,
                    null, 0, 0, 0, null, null, null, props);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void render(ItemStack stack, RenderPhase phase,
            @Nullable Matrix4d preTransform) {
        if (itemStateRoot == null)
            return;

        Map<String, Comparable<?>> props = ItemProperties.buildProperties(stack, phase);
        EvalResult result = itemStateRoot.evaluate(props);
        if (result.isEmpty())
            return;

        for (String path : result.getModels()) {
            String cacheKey = BakedModelCache.buildKey(path, 0, 0);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            if (part == null || part.isEmpty())
                continue;
            UniformRenderPipeline.renderItemQuads(part, stack, phase,
                    null, 0, 0, 0, null, preTransform, null, props);
        }
    }

    /**
     * IItemState: returns inventory (2D GUI) model path.
     * <p>
     * DataLoading.init() automatically collects this path's textures during scanning.
     * If dual-model ({@link #hasDualModels()}), hand model path is also collected.
     *
     * @return inventory model path, or null if not set
     */
    public String getModelPath() {
        return inventoryModelPath;
    }

    /**
     * Public accessor for 3D handheld model path.
     * <p>
     * Used by {@link ModelManagerDataLoader#init()} during scanning to
     * additionally collect hand model textures for dual-model items.
     *
     * @return hand model path, or null if not set
     */
    public String getHandModelPath() {
        return handModelPath;
    }

    /**
     * IItemState: interface-driven declaration of model paths this item needs for texture
     * collection ({@link IItemStateProvider#getDeclaredModelPaths()}).
     * <p>
     * Implementation-as-declaration: discovery phase no longer instanceof-checks ModernItem,
     * unified collection via this method; every texture stitch reruns this method
     * (sets are idempotent), late {@link #setModels(String, String)} declarations
     * auto-pickup on next pass.
     * <p>
     * Semantics identical to legacy special-case: if inventory unset, no paths collected;
     * dual-model returns {@code [inventory, hand]}; single-model returns {@code [inventory]}.
     *
     * @return declared model paths (may be empty)
     */
    @Override
    public List<String> getDeclaredModelPaths() {
        if (inventoryModelPath == null) {
            return Collections.emptyList();
        }
        if (handModelPath != null) {
            return Arrays.asList(inventoryModelPath, handModelPath);
        }
        return Collections.singletonList(inventoryModelPath);
    }

    // ==================== Convenience ====================

    /**
     * Direct read access for a specific layer icon (client side only).
     */
    @SideOnly(Side.CLIENT)
    public IIcon getLayerIcon(int layer) {
        if (layer >= 0 && layer < layerIcons.length) {
            IIcon icon = layerIcons[layer];
            return icon != null ? icon : this.itemIcon;
        }
        return this.itemIcon;
    }

    // ==================== Creative tabs — multi-subtype support
    // ====================

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        // Default: single sub-item. Subclasses with multiple damage values
        // should override this.
        list.add(new ItemStack(item, 1, 0));
    }
}
