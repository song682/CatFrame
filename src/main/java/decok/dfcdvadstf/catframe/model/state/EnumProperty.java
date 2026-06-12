package decok.dfcdvadstf.catframe.model.state;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 枚举属性。值域为枚举类型的一个子集。
 *
 * <p>内部索引通过 {@code ordinalToIndex[]} 映射：{@code Enum.ordinal() → 内部索引}。
 *
 * <p>用法（全部枚举值）：
 * <pre>{@code
 * public enum SlabType { BOTTOM, TOP, DOUBLE }
 * public static final Property<SlabType> TYPE = EnumProperty.create("type", SlabType.class);
 * }</pre>
 *
 * <p>用法（过滤后的子集）：
 * <pre>{@code
 * public static final Property<Direction> FACING_HORIZONTAL =
 *     EnumProperty.create("facing", Direction.class, Direction.NORTH, Direction.SOUTH, ...);
 * }</pre>
 *
 * @param <T> 枚举类型
 */
public final class EnumProperty<T extends Enum<T>> extends Property<T> {

    private final int[] ordinalToIndex;

    private EnumProperty(String name, Class<T> clazz, List<T> values) {
        super(name, clazz, values);
        this.ordinalToIndex = new int[clazz.getEnumConstants().length];
        Arrays.fill(this.ordinalToIndex, -1);
        for (int i = 0; i < values.size(); i++) {
            this.ordinalToIndex[values.get(i).ordinal()] = i;
        }
    }

    @Override
    public int getInternalIndex(T value) {
        int idx = ordinalToIndex[value.ordinal()];
        if (idx < 0) {
            throw new IllegalArgumentException("Value " + value + " is not in this property filter");
        }
        return idx;
    }

    /**
     * 创建一个包含枚举所有值的枚举属性。
     *
     * @param name  属性名称
     * @param clazz 枚举类
     * @param <T>   枚举类型
     * @return 新的 EnumProperty 实例
     */
    public static <T extends Enum<T>> EnumProperty<T> create(String name, Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        return new EnumProperty<>(name, clazz, Collections.unmodifiableList(Arrays.asList(constants)));
    }

    /**
     * 创建一个仅包含指定枚举值子集的枚举属性。
     *
     * @param name   属性名称
     * @param clazz  枚举类
     * @param filter 要包含的枚举值
     * @param <T>    枚举类型
     * @return 新的 EnumProperty 实例
     */
    @SafeVarargs
    public static <T extends Enum<T>> EnumProperty<T> create(String name, Class<T> clazz, T... filter) {
        return new EnumProperty<>(name, clazz, Collections.unmodifiableList(Arrays.asList(filter)));
    }
}
