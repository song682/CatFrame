package decok.dfcdvadstf.catframe.model.state.property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 整数范围属性。值域为从 min 到 max 的所有整数（含两端）。
 *
 * <p>内部索引：{@code value - min}，即按升序排列。
 *
 * <p>用法：
 * <pre>{@code
 * public static final Property<Integer> BITES = IntegerProperty.create("bites", 0, 6);
 * }</pre>
 */
public final class IntegerProperty extends Property<Integer> {

    private final int min;
    private final int max;

    private IntegerProperty(String name, int min, int max) {
        super(name, Integer.class, createValues(min, max));
        this.min = min;
        this.max = max;
    }

    @Override
    public int getInternalIndex(Integer value) {
        int idx = value - min;
        if (idx < 0 || idx > max - min) {
            throw new IllegalArgumentException("Value " + value + " is out of range [" + min + ", " + max + "]");
        }
        return idx;
    }

    /**
     * 返回最小值（含）。
     */
    public int getMin() {
        return min;
    }

    /**
     * 返回最大值（含）。
     */
    public int getMax() {
        return max;
    }

    /**
     * 创建一个整数范围属性。
     *
     * @param name 属性名称（如 "level", "bites"）
     * @param min  最小值（含）
     * @param max  最大值（含）
     * @return 新的 IntegerProperty 实例
     * @throws IllegalArgumentException 如果 min > max
     */
    public static IntegerProperty create(String name, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("Min must be <= max for IntegerProperty '" + name + "'");
        }
        return new IntegerProperty(name, min, max);
    }

    private static List<Integer> createValues(int min, int max) {
        List<Integer> values = new ArrayList<>();
        for (int i = min; i <= max; i++) {
            values.add(i);
        }
        return Collections.unmodifiableList(values);
    }
}
