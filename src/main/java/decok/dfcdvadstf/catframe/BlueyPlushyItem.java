package decok.dfcdvadstf.catframe;

import decok.dfcdvadstf.catframe.model.impl.ModernItem;
import net.minecraft.creativetab.CreativeTabs;

/**
 * Bluey Plushy Item <br>
 * 2D inventory model (GUI + drop) and 3D held model example.
 */
public class BlueyPlushyItem extends ModernItem {

    public BlueyPlushyItem() {
        super(1);
        this.maxStackSize = 1;
        this.setUnlocalizedName("bluey_plushy");
        // 注意：TextureMap 的 basePath 为 "items"，会自动拼接路径前缀。
        // 所以注册名为 "catframe:bluey_pixelized_inventory"，
        // 实际纹理文件位于 assets/.../textures/items/bluey_pixelized_inventory.png
        this.setLayerTextureNames("catframe:bluey_pixelized_inventory");
        this.setCreativeTab(CreativeTabs.tabMisc);
        // 双模型配置：GUI/掉落物走 2D inventory，手持走 3D 模型
        this.setModels("item/bluey_inventory", "item/bluey");
    }
}
