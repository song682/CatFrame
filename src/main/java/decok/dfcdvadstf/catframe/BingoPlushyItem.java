package decok.dfcdvadstf.catframe;

import decok.dfcdvadstf.catframe.model.impl.ModernItem;
import net.minecraft.creativetab.CreativeTabs;

public class BingoPlushyItem extends ModernItem {

    public BingoPlushyItem() {
        super(1);
        this.maxStackSize = 1;
        this.setUnlocalizedName("bingo_plushy");
        this.setLayerTextureNames("catframe:bingo_pixelized_inventory");
        this.setCreativeTab(CreativeTabs.tabMisc);
    }
}
