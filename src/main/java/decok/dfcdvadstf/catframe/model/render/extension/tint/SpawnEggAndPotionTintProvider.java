package decok.dfcdvadstf.catframe.model.render.extension.tint;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import net.minecraft.entity.EntityList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionHelper;

/**
 * 刷怪蛋 & 药水的染色注册。
 *
 * <h3>刷怪蛋</h3>
 * <ul>
 *   <li>tintIndex 0 (layer0 — 蛋底纹)：primaryColor（实体主色）</li>
 *   <li>tintIndex 1 (layer1 — 斑点覆盖)：secondaryColor（实体副色）</li>
 * </ul>
 * 对齐原版 {@link ItemMonsterPlacer#getColorFromItemStack}：
 * pass 0 = primaryColor, pass > 0 = secondaryColor。
 *
 * <h3>药水</h3>
 * <ul>
 *   <li>tintIndex 0 (layer0 — 瓶身)：0xFFFFFF（不染色）</li>
 *   <li>tintIndex 1 (layer1 — 药水液体)：PotionHelper 计算出的药水颜色</li>
 * </ul>
 * 对齐原版 {@link ItemPotion#getColorFromItemStack}：
 * pass 0 = potionColor, pass > 0 = white。但 JSON 模型中 layer0 是瓶身（不染色），
 * layer1 是液体（染色），因此 tintIndex 语义与原版 pass 恰好相反。
 */
@SideOnly(Side.CLIENT)
public final class SpawnEggAndPotionTintProvider implements IItemTintProvider {

    private Item spawnEggItem;
    private Item potionItem;

    @Override
    public int getTint(ItemStack stack, int tintIndex) {
        Item item = stack.getItem();

        // ==================== 刷怪蛋 ====================
        if (item == spawnEggItem) {
            EntityList.EntityEggInfo eggInfo =
                    (EntityList.EntityEggInfo) EntityList.entityEggs.get(stack.getItemDamage());
            if (eggInfo == null) return 0xFFFFFF;

            // tintIndex 0 = layer0 (底纹) → primaryColor
            // tintIndex 1 = layer1 (斑点) → secondaryColor
            return tintIndex == 0 ? eggInfo.primaryColor : eggInfo.secondaryColor;
        }

        // ==================== 药水 ====================
        if (item == potionItem) {
            // tintIndex 0 = layer0 (瓶身) → 不染色
            // tintIndex 1 = layer1 (药水液体) → PotionHelper 计算颜色
            if (tintIndex == 0) return 0xFFFFFF;
            return PotionHelper.func_77915_a(stack.getItemDamage(), false) & 0xFFFFFF;
        }

        return 0xFFFFFF;
    }

    @Override
    public void register() {
        spawnEggItem = (Item) Item.itemRegistry.getObject("spawn_egg");
        potionItem = (Item) Item.itemRegistry.getObject("potion");

        if (spawnEggItem != null) {
            TintRegistry.registerItemTint(spawnEggItem, this);
        } else {
            CatFrame.logger.warn("[TintProvider] spawn_egg item not found, skipping tint registration");
        }

        if (potionItem != null) {
            TintRegistry.registerItemTint(potionItem, this);
        } else {
            CatFrame.logger.warn("[TintProvider] potion item not found, skipping tint registration");
        }

        CatFrame.logger.info("[TintProvider] registered spawn_egg & potion tints");
    }
}
