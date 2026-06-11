package decok.dfcdvadstf.catframe;

import net.minecraft.creativetab.CreativeTabs;

/**
 * Bluey 毛绒玩偶物品。
 * <p>
 * 使用 {@link ModernItem} 的多层纹理系统提供快捷栏 2D 图标，
 * 并搭配 {@link BlueyPlushyItemModel} 实现手持 3D 渲染。
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
    }
}
