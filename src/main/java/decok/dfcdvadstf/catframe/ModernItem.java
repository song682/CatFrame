package decok.dfcdvadstf.catframe;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.IItemJsonModel;
import decok.dfcdvadstf.catframe.model.ItemModel;
import decok.dfcdvadstf.catframe.model.ModelJson;
import decok.dfcdvadstf.catframe.model.ModelResolver;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import java.util.List;
import java.util.Map;

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
 * Call {@link #setModels(String, String)} in the constructor to specify
 * separate 2D (inventory) and 3D (handheld) model paths. The built-in
 * {@link DualRenderItemModel} automatically dispatches:
 * <ul>
 *   <li>GUI / dropped item → 2D inventory model (builtin/generated, with side thickness)</li>
 *   <li>First/third person hand → 3D handheld model</li>
 * </ul>
 * When only one model is set (via {@link VanillaModelManager.ModelRegistration#registerItemModel}),
 * that model is used for all phases.
 *
 * <h3>Model system compatibility</h3>
 * ModernItem is a plain {@link Item} subclass — it works transparently
 * with {@link VanillaModelManager} and the existing JSON model pipeline.
 */
public class ModernItem extends Item implements IItemJsonModel {

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
     * 2D inventory model path (e.g. "item/bluey_inventory"), used for GUI and dropped item rendering.
     * Set by {@link #setModels(String, String)}.
     */
    protected String inventoryModelPath;

    /**
     * 3D handheld model path (e.g. "item/bluey"), used for first/third person hand rendering.
     * Set by {@link #setModels(String, String)}.
     */
    protected String handModelPath;

    // ==================== Constructors ====================

    public ModernItem() {
        this(1);
    }

    /**
     * @param layers number of render passes (≥ 1).  Values &lt; 1 are clamped to 1.
     */
    public ModernItem(int layers) {
        this.layerCount = Math.max(1, layers);
        this.layerIcons = new IIcon[this.layerCount];
        this.layerIconNames = new String[this.layerCount];
        this.setFull3D();               // Enable vanilla 3D-in-hand; JSON models override with display transforms
        this.setHasSubtypes(true);      // allow damage-based sub-items by default
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
        if (layers < 1) layers = 1;
        this.layerCount = layers;
        this.layerIcons = new IIcon[this.layerCount];
        this.layerIconNames = new String[this.layerCount];
    }

    /**
     * Assign texture names for each layer and update the layer count.
     * Also forwards the first name to {@link #setTextureName(String)}
     * so vanilla tooling (e.g. missing-icon fallback) keeps working.
     *
     * @param names one texture name per layer, e.g. {@code "catframe:items/sword_blade", "catframe:items/sword_handle"}
     * @return this
     */
    public ModernItem setLayerTextureNames(String... names) {
        if (names == null || names.length == 0) return this;
        this.layerCount = names.length;
        this.layerIcons = new IIcon[this.layerCount];
        this.layerIconNames = names;
        this.setTextureName(names[0]);   // compatibility with vanilla iconString
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
     * Per-layer colour tint.  Subclasses (e.g. dyed items) should override
     * this to return a different colour per pass.  Default: opaque white.
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
     * Automatically creates and registers a {@link DualRenderItemModel}.
     * <p>
     * Must be called <b>before</b> {@link VanillaModelManager.DataLoading#init()}
     * so that texture collection can pick up both models' textures.
     *
     * @param inventoryModel 2D model path for GUI and dropped items (e.g. "item/bluey_inventory")
     * @param handModel      3D model path for handheld rendering (e.g. "item/bluey")
     * @return this
     */
    public ModernItem setModels(String inventoryModel, String handModel) {
        this.inventoryModelPath = inventoryModel;
        this.handModelPath = handModel;
        return this;
    }

    /**
     * Returns true if this item has dual-model configuration.
     */
    public boolean hasDualModels() {
        return inventoryModelPath != null && handModelPath != null;
    }

    // ==================== IItemJsonModel ====================

    /**
     * IItemJsonModel: 返回 inventory（2D GUI）模型路径。
     * <p>
     * DataLoading.init() 扫描时自动收集此路径的纹理。
     * 如果是双模型（{@link #hasDualModels()}），hand 模型路径也会被额外收集。
     *
     * @return inventory 模型路径，如果未设置则返回 null
     */
    @Override
    public String getModelPath() {
        return inventoryModelPath;
    }

    /**
     * 3D handheld 模型路径的公开访问器。
     * <p>
     * 供 {@link VanillaModelManager.DataLoading#init()} 扫描时
     * 额外收集双模型物品的 hand 模型纹理。
     *
     * @return hand 模型路径，如果未设置则返回 null
     */
    public String getHandModelPath() {
        return handModelPath;
    }

    /**
     * Create the dual-render ItemModel for this item.
     * Called by registration code after DataLoading.init().
     */
    @SideOnly(Side.CLIENT)
    public ItemModel createItemModel() {
        if (hasDualModels()) {
            return new DualRenderItemModel(inventoryModelPath, handModelPath);
        }
        return null;
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

    // ==================== Creative tabs — multi-subtype support ====================

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        // Default: single sub-item.  Subclasses with multiple damage values
        // should override this.
        list.add(new ItemStack(item, 1, 0));
    }

    // ==================== Dual-render ItemModel ====================

    /**
     * 内建的双模型 ItemModel：GUI/掉落物用 2D inventory 模型，手持用 3D 模型。
     * <p>
     * 由 {@link ModernItem#setModels(String, String)} 触发创建，
     * 替代了每个物品单独实现 {@link ItemModel} 的需要。
     */
    public static class DualRenderItemModel implements ItemModel {

        private final String model2D;
        private final String model3D;

        private BlockStateModelPart part2D;
        private Map<String, ModelJson.DisplayTransform> display2D;
        private BlockStateModelPart part3D;
        private Map<String, ModelJson.DisplayTransform> display3D;

        public DualRenderItemModel(String model2D, String model3D) {
            this.model2D = model2D;
            this.model3D = model3D;
        }

        @Override
        public boolean handles(RenderPhase phase) {
            return true;
        }

        @Override
        public void render(ItemStack stack, RenderPhase phase) {
            if (phase == RenderPhase.ITEM_HAND_FIRST_PERSON
                    || phase == RenderPhase.ITEM_HAND_THIRD_PERSON) {
                render3D(stack, phase);
            } else if (phase == RenderPhase.ITEM_GUI
                    || phase == RenderPhase.DROPPED_ITEM_GROUND) {
                render2D(stack, phase);
            }
        }

        private void render3D(ItemStack stack, RenderPhase phase) {
            if (part3D == null) {
                part3D = VanillaModelManager.ModelRegistration.bakeModelPart(model3D, 0);
                ModelJson resolved = ModelResolver.resolve(model3D);
                display3D = (resolved != null) ? resolved.display : null;
            }
            if (part3D == null || part3D.isEmpty()) return;

            UniformRenderPipeline.renderItemQuads(part3D, stack, phase, display3D);
        }

        private void render2D(ItemStack stack, RenderPhase phase) {
            if (part2D == null) {
                part2D = VanillaModelManager.ModelRegistration.bakeModelPart(model2D, 0);
                ModelJson resolved = ModelResolver.resolve(model2D);
                display2D = (resolved != null) ? resolved.display : null;
            }
            if (part2D == null || part2D.isEmpty()) return;

            UniformRenderPipeline.renderItemQuads(part2D, stack, phase, display2D);
        }
    }
}
