package decok.dfcdvadstf.catframe.core.component;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

/**
 * NBT serializer for component values.
 * <p>
 * Plays the role of the 26.1.2 Codec, converting component values to and from NBT.
 *
 * @param <T> the Java type of the component value
 */
public interface ComponentSerializer<T> {

    /**
     * Writes the component value into the NBT tag.
     */
    void write(NBTTagCompound nbt, T value);

    /**
     * Reads the component value from the NBT tag.
     *
     * @return the parsed value, or null if the tag is absent or malformed
     */
    @Nullable
    T read(NBTTagCompound nbt);

    /**
     * Checks whether the NBT contains data for this component.
     */
    default boolean hasData(NBTTagCompound nbt) {
        return true;
    }
}
