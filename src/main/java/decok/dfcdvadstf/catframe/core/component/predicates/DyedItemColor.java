package decok.dfcdvadstf.catframe.core.component.predicates;

import decok.dfcdvadstf.catframe.core.component.ComponentSerializer;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

/**
 * Dyed item color value.
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.world.item.component.DyedItemColor}.
 * Maps to the vanilla ItemStack "display.color" NBT tag.
 */
public final class DyedItemColor {

    private final int rgb;

    public DyedItemColor(int rgb) {
        this.rgb = rgb;
    }

    /**
     * Returns the packed RGB value.
     */
    public int getRgb() {
        return rgb;
    }

    /**
     * Returns the red channel.
     */
    public int getRed() {
        return (rgb >> 16) & 0xFF;
    }

    /**
     * Returns the green channel.
     */
    public int getGreen() {
        return (rgb >> 8) & 0xFF;
    }

    /**
     * Returns the blue channel.
     */
    public int getBlue() {
        return rgb & 0xFF;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DyedItemColor)) return false;
        return rgb == ((DyedItemColor) o).rgb;
    }

    @Override
    public int hashCode() {
        return rgb;
    }

    @Override
    public String toString() {
        return String.format("#%06X", rgb);
    }

    // ========== Serializer ==========

    public static final ComponentSerializer<DyedItemColor> SERIALIZER = new ComponentSerializer<DyedItemColor>() {
        @Override
        public void write(NBTTagCompound nbt, DyedItemColor value) {
            NBTTagCompound display = nbt.getCompoundTag("display");
            display.setInteger("color", value.rgb);
            nbt.setTag("display", display);
        }

        @Nullable
        @Override
        public DyedItemColor read(NBTTagCompound nbt) {
            if (!nbt.hasKey("display", 10)) return null;
            NBTTagCompound display = nbt.getCompoundTag("display");
            if (!display.hasKey("color")) return null;
            return new DyedItemColor(display.getInteger("color"));
        }
    };
}
