package decok.dfcdvadstf.catframe.model.render.extension.tint;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Decides the colour when an item (GUI / held) is rendered, based on the
 * {@code "tintindex"} on the JSON face. Because the argument is an
 * {@link ItemStack}, implementations can read NBT, damage, enchantments, etc. for
 * fine-grained tinting (e.g. dyed armour, different potions).
 *
 * <p>Implementations should override {@link #register()} to register themselves with
 * {@link TintRegistry}, replacing the old static {@code XxxProvider.register()}
 * pattern.</p>
 */
public interface IItemTintProvider {
    /**
     * @param stack     the item stack being rendered
     * @param tintIndex the {@code "tintindex"} value in the JSON face (≥ 0)
     * @return 0xRRGGBB colour multiplier; {@code 0xFFFFFF} means no tint
     */
    int getTint(ItemStack stack, int tintIndex);

    /**
     * Registers the current instance with {@link TintRegistry}. Implementations should
     * call {@link TintRegistry#registerItemTint(Item, IItemTintProvider)} etc. in this
     * method to perform their own registration.
     */
    default void register() {
        // Subclasses override this to implement their registration logic
    }
}
