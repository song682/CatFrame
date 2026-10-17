package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemStack;

/**
 * Spawn egg color tint (CatFrame extension).
 * <p>
 * Looks up {@link EntityList.EntityEggInfo} by the spawn egg's damage value (entity ID) and
 * returns the primary/secondary color according to {@code index}:
 * <ul>
 *   <li>{@code index == 0} (layer0 — egg base pattern): primaryColor</li>
 *   <li>{@code index != 0} (layer1 — spot overlay): secondaryColor</li>
 * </ul>
 * Aligned with 1.7.10 vanilla {@code ItemMonsterPlacer#getColorFromItemStack}:
 * pass 0 = primaryColor, pass &gt; 0 = secondaryColor.
 * <p>
 * <b>Note</b>: not a vanilla wiki tint type; high-version vanilla renders spawn eggs via a
 * {@code minecraft:special} model. This type is CatFrame's declarative extension for 1.7.10
 * per-pass tinting, replacing the old hard-coded {@code SpawnEggTintProvider}.
 *
 * <p>JSON: {@code {"type": "minecraft:spawn_egg", "index": 0}}
 */
public class SpawnEggTint implements ItemTint {

    /** Color component index: 0 = primary color (primaryColor), non-zero = secondary color (secondaryColor). */
    private final int index;

    public SpawnEggTint(int index) {
        this.index = index;
    }

    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        if (stack == null) return 0xFFFFFF;

        EntityList.EntityEggInfo eggInfo =
                (EntityList.EntityEggInfo) EntityList.entityEggs.get(stack.getItemDamage());
        if (eggInfo == null) return 0xFFFFFF;

        // index 0 = layer0 (base pattern) → primaryColor; otherwise layer1 (spots) → secondaryColor
        return (index == 0 ? eggInfo.primaryColor : eggInfo.secondaryColor) & 0xFFFFFF;
    }
}
