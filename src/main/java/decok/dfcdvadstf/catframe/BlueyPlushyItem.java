package decok.dfcdvadstf.catframe;

import net.minecraft.creativetab.CreativeTabs;

/**
 * Bluey 毛绒玩偶物品。
 * <p>
 * 使用 {@link ModernItem} 的双模型系统：
 * 通过 {@link #setModels(String, String)} 配置
 * 2D inventory 模型（GUI + 掉落物）和 3D 手持模型。
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
