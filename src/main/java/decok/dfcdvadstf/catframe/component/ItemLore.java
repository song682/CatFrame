package decok.dfcdvadstf.catframe.component;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;

import net.minecraft.nbt.NBTTagString;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 物品描述文本（Lore）。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.world.item.component.ItemLore}。
 * 映射到原版 ItemStack 的 "display.Lore" NBT 标签。
 */
public final class ItemLore {

    private static final ItemLore EMPTY = new ItemLore(Collections.emptyList());

    private final List<String> lines;

    private ItemLore(List<String> lines) {
        this.lines = lines;
    }

    // ========== 工厂方法 ==========

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

    // ========== 查询 ==========

    public List<String> getLines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int size() {
        return lines.size();
    }

    // ========== NBT 转换 ==========

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
     * 创建带默认样式格式化的 lore。
     */
    public List<String> getStyledLines() {
        return lines.stream()
                .map(line -> EnumChatFormatting.RESET + EnumChatFormatting.DARK_PURPLE.toString() + line)
                .collect(Collectors.toList());
    }

    // ========== 对象约定 ==========

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

    // ========== 序列化器 ==========

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
