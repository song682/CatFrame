package decok.dfcdvadstf.catframe.model.render.extension.tint;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.ColorizerFoliage;

import static decok.dfcdvadstf.catframe.model.render.extension.tint.LeavesTintProvider.getLeaves2Block;

public class LeavesInHandTintProvider implements IItemTintProvider{
    @Override
    public int getTint(ItemStack stack, int tintIndex) {
        Item item = stack.getItem();

        // The leaves2 item always returns the basic foliage colour
        Block leaves2 = getLeaves2Block();
        if (leaves2 != null && item == Item.getItemFromBlock(leaves2)) {
            return ColorizerFoliage.getFoliageColorBasic();
        }

        // The leaves1 item is differentiated by metadata
        int meta = stack.getItemDamage() & 3;
        if (meta == 1) return ColorizerFoliage.getFoliageColorPine();
        if (meta == 2) return ColorizerFoliage.getFoliageColorBirch();
        return ColorizerFoliage.getFoliageColorBasic();
    }
    @Override
    public void register() {
        registerLeaves1();
        registerLeaves2();
    }

    private void registerLeaves1(){
        Item item = Item.getItemFromBlock(Blocks.leaves);
        if (item != null) {
            TintRegistry.registerItemTint(item, this);
        }
    }

    private void registerLeaves2(){
        Item item = Item.getItemFromBlock(Blocks.leaves2);
        if (item != null) {
            TintRegistry.registerItemTint(item, this);
        }
    }
}
