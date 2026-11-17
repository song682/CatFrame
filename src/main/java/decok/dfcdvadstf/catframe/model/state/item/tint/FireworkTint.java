package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;

/**
 * Average color tint of fireworks stars.
 * <p>Mirrors vanilla {@code ItemFireworkCharge#getColorFromItemStack(stack, 1)}: the
 * {@code Explosion/Colors} int array is averaged channel-wise; a single entry is used as-is;
 * a missing or non-int-array tag falls back to vanilla's grey {@code 9079434} ({@code 0x8A8A8A}).
 */
public class FireworkTint implements ItemTint {

    /** Vanilla fallback color used when the stack carries no explosion colors ({@code 0x8A8A8A}). */
    public static final int DEFAULT_COLOR = 9079434;

    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        int[] colors = readExplosionColors(stack);
        if (colors == null || colors.length == 0) return DEFAULT_COLOR;
        if (colors.length == 1) return colors[0] & 0xFFFFFF;

        // Channel-wise average, identical to vanilla (integer division by the entry count)
        int red = 0;
        int green = 0;
        int blue = 0;
        for (int color : colors) {
            red += (color & 0xFF0000) >> 16;
            green += (color & 0xFF00) >> 8;
            blue += color & 0xFF;
        }
        int count = colors.length;
        return ((red / count) << 16) | ((green / count) << 8) | (blue / count);
    }

    /**
     * Reads {@code Explosion.Colors} (an int array of {@code 0xRRGGBB}) from the stack NBT.
     *
     * @param stack the stack being tinted; may be null
     * @return the raw color array, or null when the tag is absent or of another NBT type
     */
    private static int[] readExplosionColors(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("Explosion", 10)) return null;
        NBTTagCompound explosion = tag.getCompoundTag("Explosion");
        if (explosion == null || !explosion.hasKey("Colors", 11)) return null;
        return explosion.getTag("Colors") instanceof NBTTagIntArray
                ? ((NBTTagIntArray) explosion.getTag("Colors")).func_150302_c()
                : null;
    }
}
