package decok.dfcdvadstf.catframe;

import decok.dfcdvadstf.catframe.model.impl.ModernItem;
import net.minecraft.creativetab.CreativeTabs;

/**
 * Bluey Plushy Item <br>
 * 2D inventory model (GUI + drop) and 3D held model example.
 */
public class BlueyPlushyItem extends ModernItem {

    /**
     * Note: TextureMap's basePath is "items", and the path prefix is automatically appended.
     * Therefore, the registered name is “catframe:bluey_pixelized_inventory,”
     * and the actual texture file is located at assets/.../textures/items/bluey_pixelized_inventory.png
     */
    public BlueyPlushyItem() {
        super(1);
        this.maxStackSize = 1;
        this.setUnlocalizedName("bluey_plushy");
        this.setLayerTextureNames("catframe:bluey_pixelized_inventory");
        this.setCreativeTab(CreativeTabs.tabMisc);
    }
}
