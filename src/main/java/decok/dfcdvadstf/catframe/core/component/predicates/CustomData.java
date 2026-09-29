package decok.dfcdvadstf.catframe.core.component.predicates;

import decok.dfcdvadstf.catframe.core.component.ComponentSerializer;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

/**
 * Custom data - the fallback NBT data container.
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.world.item.component.CustomData}.
 * Stores arbitrary NBT data that has no dedicated component, keeping
 * compatibility with legacy NBT.
 * <p>
 * Scope: covers the ENTIRE vanilla item NBT (the whole stackTagCompound),
 * mirroring the legacy {@code nbt} predicate semantics — not a nested sub-tag.
 */
public final class CustomData {

    private static final CustomData EMPTY = new CustomData(new NBTTagCompound());

    private final NBTTagCompound tag;
    private final int cachedHash;

    private CustomData(NBTTagCompound tag) {
        this.tag = tag;
        this.cachedHash = tag.hashCode();
    }

    /**
     * Creates a CustomData containing the given data.
     */
    public static CustomData of(NBTTagCompound tag) {
        return tag.hasNoTags() ? EMPTY : new CustomData((NBTTagCompound) tag.copy());
    }

    /**
     * Creates a CustomData wrapping the given NBT (no copy).
     */
    public static CustomData wrap(NBTTagCompound tag) {
        return tag.hasNoTags() ? EMPTY : new CustomData(tag);
    }

    /**
     * Returns the empty data instance.
     */
    public static CustomData empty() {
        return EMPTY;
    }

    /**
     * Returns a copy of the internal NBT.
     */
    public NBTTagCompound copyTag() {
        return (NBTTagCompound) tag.copy();
    }

    /**
     * Returns the internal NBT (read-only).
     */
    public NBTTagCompound getTag() {
        return tag;
    }

    /**
     * Updates the data.
     */
    public CustomData update(NBTTagCompound newTag) {
        return newTag.equals(tag) ? this : new CustomData((NBTTagCompound) newTag.copy());
    }

    public boolean isEmpty() {
        return tag.hasNoTags();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomData)) return false;
        CustomData that = (CustomData) o;
        return tag.equals(that.tag);
    }

    @Override
    public int hashCode() {
        return cachedHash;
    }

    @Override
    public String toString() {
        return "CustomData" + tag;
    }

    // ========== Serializer ==========

    /**
     * Full-scope serializer: read wraps the whole vanilla tag; write merges
     * the held keys back into the root tag (no nested "CustomData" key).
     */
    public static final ComponentSerializer<CustomData> SERIALIZER = new ComponentSerializer<CustomData>() {
        @Override
        public void write(NBTTagCompound nbt, CustomData value) {
            // Same live tag already in place — nothing to merge.
            if (value.isEmpty() || value.tag == nbt) return;
            for (Object keyObj : value.tag.func_150296_c()) {
                String key = (String) keyObj;
                nbt.setTag(key, value.tag.getTag(key).copy());
            }
        }

        @Nullable
        @Override
        public CustomData read(NBTTagCompound nbt) {
            // Whole vanilla NBT is the component value.
            if (nbt.hasNoTags()) return null;
            return wrap(nbt);
        }

        @Override
        public boolean hasData(NBTTagCompound nbt) {
            return !nbt.hasNoTags();
        }
    };
}
