package decok.dfcdvadstf.catframe;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import java.util.List;

/**
 * ItemModern — extended Item with two key features over vanilla Item:
 *
 * <h3>1. Multi-layer texture rendering</h3>
 * Subclasses can specify any number (3+) of rendering passes via
 * {@link #setLayerCount(int)} / {@link #setLayerTextureNames(String...)}.
 * Vanilla only supports 1 or 2 passes; ItemModern supports N passes.
 * Each pass renders a separate full-brightness flat quad in the GUI.
 *
 * <h3>2. Inconsistent rendering (2D GUI / 3D hand)</h3>
 * Vanilla already provides this behaviour: {@code isFull3D() == true}
 * makes items render as 3D models when held in the player's hand, while
 * inventory / GUI rendering always uses flat 2D quads.
 * ItemModern calls {@link #setFull3D()} in its constructor.
 * Subclasses may call {@link #setRender3DInHand(boolean)} to toggle.
 *
 * <h3>Model system compatibility</h3>
 * ItemModern is a plain {@link Item} subclass — it works transparently
 * with {@link VanillaModelManager} and the existing JSON model pipeline.
 * When an ItemModern is registered via {@code model_mappings.json},
 * {@link VanillaModelManager#hasItemModel(Item)} returns {@code true}
 * and the model-baked quads override the vanilla icon path.
 */
public class ModernItem extends Item {

    /** Icons for each render layer, populated during {@link #registerIcons}. */
    @SideOnly(Side.CLIENT)
    protected IIcon[] layerIcons;

    /** Texture name strings for each layer, set by subclasses before registration. */
    protected String[] layerIconNames;

    /** Number of render passes (default 1). */
    protected int layerCount;

    /** Whether this item should render as a 3D model when held in hand. */
    protected boolean render3DInHand = true;

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
        this.setFull3D();               // 3D when held in hand
        this.setHasSubtypes(true);      // allow damage-based sub-items by default
    }

    // ==================== Layer configuration ====================

    /** @return number of render passes (layers) this item uses. */
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

    // ==================== 3D-in-hand toggle ====================

    /**
     * When {@code true} (default), the item is rendered as a 3D model
     * while held in the player's hand.  GUI / inventory rendering is
     * always flat 2D regardless of this flag — that is vanilla behaviour.
     */
    public ModernItem setRender3DInHand(boolean value) {
        this.render3DInHand = value;
        if (value) {
            this.setFull3D();
        } else {
            // There is no vanilla un-setFull3D; we can only override the
            // behaviour in mixins.  For now we keep the field as-is and
            // let subclasses / mixins inspect render3DInHand.
        }
        return this;
    }

    public boolean shouldRender3DInHand() {
        return render3DInHand;
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

    // ==================== Convenience ====================

    /** Direct read access for a specific layer icon (client side only). */
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
}
