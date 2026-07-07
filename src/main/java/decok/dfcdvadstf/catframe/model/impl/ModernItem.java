package decok.dfcdvadstf.catframe.model.impl;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import decok.dfcdvadstf.catframe.model.state.property.ItemProperties;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import java.util.HashMap;
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
 * separate 2D (inventory) and 3D (handheld) model paths.
 * Internally an {@link ItemStateNode} decision tree is built which automatically dispatches:
 * <ul>
 *   <li>GUI / dropped item → 2D inventory model (builtin/generated, with side thickness)</li>
 *   <li>First/third person hand → 3D handheld model</li>
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
     * 2D inventory model path (e.g. "item/bluey_inventory"), used for GUI and dropped item rendering.
     * Set by {@link #setModels(String, String)}.
     */
    protected String inventoryModelPath;

    /**
     * 3D handheld model path (e.g. "item/bluey"), used for first/third person hand rendering.
     * Set by {@link #setModels(String, String)}.
     */
    protected String handModelPath;

    /**
     * ItemState 决策树根节点。
     * 由 {@link #setModels(String, String)} 根据单模型或双模型配置构建。
     */
    protected ItemStateNode itemStateRoot;

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
     * <p>
     * 内部会构建一棵 {@link ItemStateNode} 决策树：
     * <ul>
     *   <li>GUI / 掉落物阶段（display_context = ITEM_GUI / DROPPED_ITEM_GROUND）→ 2D inventory 模型</li>
     *   <li>手持阶段（display_context = ITEM_HAND_FIRST_PERSON / ITEM_HAND_THIRD_PERSON）→ 3D 手持模型</li>
     * </ul>
     * 若只传入一个模型路径，则退化为单 {@link ItemStateNode.ModelLeaf}。
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
        rebuildItemStateRoot();
        return this;
    }

    /**
     * 根据当前 {@link #inventoryModelPath} / {@link #handModelPath} 重建 ItemState 决策树。
     */
    private void rebuildItemStateRoot() {
        if (inventoryModelPath == null && handModelPath == null) {
            this.itemStateRoot = null;
            return;
        }

        if (hasDualModels()) {
            ItemStateNode inventoryLeaf = new ItemStateNode.ModelLeaf(inventoryModelPath);
            ItemStateNode handLeaf = new ItemStateNode.ModelLeaf(handModelPath);

            Map<String, ItemStateNode> cases = new HashMap<>();
            cases.put(RenderPhase.ITEM_HAND_FIRST_PERSON.name(), handLeaf);
            cases.put(RenderPhase.ITEM_HAND_THIRD_PERSON.name(), handLeaf);
            cases.put(RenderPhase.ITEM_GUI.name(), inventoryLeaf);
            cases.put(RenderPhase.DROPPED_ITEM_GROUND.name(), inventoryLeaf);
            // 方块物品掉落物也使用 2D inventory 模型（ModernItem 本身不是 ItemBlock，但为兼容保留）
            cases.put(RenderPhase.DROPPED_BLOCK_GROUND.name(), inventoryLeaf);

            this.itemStateRoot = new ItemStateNode.ExactMatchNode(
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
        if (itemStateRoot == null) return;

        Map<String, Comparable<?>> props = ItemProperties.buildProperties(stack, phase);
        String resolvedPath = itemStateRoot.evaluate(props);
        if (resolvedPath == null) return;

        String cacheKey = BakedModelCache.buildKey(resolvedPath, 0, 0);
        BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
        if (part == null || part.isEmpty()) return;

        UniformRenderPipeline.renderItemQuads(part, stack, phase);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void render(ItemStack stack, RenderPhase phase,
                       @javax.annotation.Nullable javax.vecmath.Matrix4d preTransform) {
        if (itemStateRoot == null) return;

        Map<String, Comparable<?>> props = ItemProperties.buildProperties(stack, phase);
        String resolvedPath = itemStateRoot.evaluate(props);
        if (resolvedPath == null) return;

        String cacheKey = BakedModelCache.buildKey(resolvedPath, 0, 0);
        BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
        if (part == null || part.isEmpty()) return;

        UniformRenderPipeline.renderItemQuads(part, stack, phase,
                null, 0, 0, 0, null, preTransform);
    }

    /**
     * IItemState: 返回 inventory（2D GUI）模型路径。
     * <p>
     * DataLoading.init() 扫描时自动收集此路径的纹理。
     * 如果是双模型（{@link #hasDualModels()}），hand 模型路径也会被额外收集。
     *
     * @return inventory 模型路径，如果未设置则返回 null
     */
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
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        // Default: single sub-item.  Subclasses with multiple damage values
        // should override this.
        list.add(new ItemStack(item, 1, 0));
    }
}
