package decok.dfcdvadstf.catframe.core.component.predicates;

import decok.dfcdvadstf.catframe.core.component.ComponentSerializer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumChatFormatting;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Item lore (description text).
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.world.item.component.ItemLore}.
 * Maps to the vanilla ItemStack "display.Lore" NBT tag.
 */
public final class ItemLore {

    private static final ItemLore EMPTY = new ItemLore(Collections.emptyList());

    private final List<String> lines;

    private ItemLore(List<String> lines) {
        this.lines = lines;
    }

    // ========== Factory methods ==========

    public static ItemLore empty() {
        return EMPTY;
    }

    public static ItemLore of(String... lines) {
        if (lines.length == 0) return EMPTY;
        return new ItemLore(Collections.unmodifiableList(Arrays.asList(lines)));
    }

    public static ItemLore of(List<String> lines) {
        if (lines.isEmpty()) return EMPTY;
        return new ItemLore(Collections.unmodifiableList(new ArrayList<>(lines)));
    }

    // ========== Queries ==========

    public List<String> getLines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int size() {
        return lines.size();
    }

    // ========== NBT conversion ==========

    public NBTTagList toNBT() {
        NBTTagList list = new NBTTagList();
        for (String line : lines) {
            list.appendTag(new NBTTagString(line));
        }
        return list;
    }

    public static ItemLore fromNBT(NBTTagList list) {
        if (list == null || list.tagCount() == 0) return EMPTY;
        List<String> lines = new ArrayList<>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            lines.add(list.getStringTagAt(i));
        }
        return of(lines);
    }

    /**
     * Returns the lines with the default style formatting applied.
     */
    public List<String> getStyledLines() {
        return lines.stream()
                .map(line -> EnumChatFormatting.RESET + EnumChatFormatting.DARK_PURPLE.toString() + line)
                .collect(Collectors.toList());
    }

    // ========== Object contract ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemLore)) return false;
        ItemLore itemLore = (ItemLore) o;
        return lines.equals(itemLore.lines);
    }

    @Override
    public int hashCode() {
        return lines.hashCode();
    }

    @Override
    public String toString() {
        return "Lore" + lines;
    }

    // ========== Serializer ==========

    public static final ComponentSerializer<ItemLore> SERIALIZER = new ComponentSerializer<ItemLore>() {
        @Override
        public void write(NBTTagCompound nbt, ItemLore value) {
            NBTTagCompound display = nbt.getCompoundTag("display");
            if (!value.isEmpty()) {
                display.setTag("Lore", value.toNBT());
                nbt.setTag("display", display);
            } else if (display.hasKey("Lore")) {
                display.removeTag("Lore");
                if (display.hasNoTags()) {
                    nbt.removeTag("display");
                } else {
                    nbt.setTag("display", display);
                }
            }
        }

        @Nullable
        @Override
        public ItemLore read(NBTTagCompound nbt) {
            if (!nbt.hasKey("display", 10)) return null;
            NBTTagCompound display = nbt.getCompoundTag("display");
            if (!display.hasKey("Lore", 9)) return null;
            return fromNBT(display.getTagList("Lore", 8));
        }
    };
}
