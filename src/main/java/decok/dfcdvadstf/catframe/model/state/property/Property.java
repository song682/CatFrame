package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.model.state.CatBlockState;

import java.util.*;

/**
 * 类型安全的块状态属性基类。类似于 1.21.5 的 {@code Property<T>}。
 *
 * <p>每个属性有一个唯一的名称、一个值类型，以及一个所有可能值的不可变列表。
 * {@link #getInternalIndex} 将值映射为内部索引，用于 {@link CatBlockState} 的邻居跳表。
 *
 * <p>预定义子类：
 * <ul>
 *   <li>{@link BooleanProperty} — 布尔值 (true / false)</li>
 *   <li>{@link IntegerProperty} — 整数范围 (min ~ max)</li>
 *   <li>{@link EnumProperty} — 枚举值</li>
 * </ul>
 *
 * @param <T> 值的类型，必须可比较
 */
public abstract class Property<T extends Comparable<T>> {

    private final String name;
    private final Class<T> valueClass;
    private final List<T> values;
    private final Map<T, Integer> indexMap;

    /**
     * @param name      属性名称（如 "facing", "variant"）
     * @param valueClass 值类型
     * @param values    所有可能值的列表（顺序决定内部索引）
     */
    protected Property(String name, Class<T> valueClass, List<T> values) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Property name must not be null or empty");
        }
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Property must have at least one value");
        }
        this.name = name;
        this.valueClass = valueClass;
        this.values = Collections.unmodifiableList(new ArrayList<>(values));

        // 构建值→索引映射
        this.indexMap = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            this.indexMap.put(values.get(i), i);
        }
    }

    // ==================== 抽象方法 ====================

    /**
     * 返回给定值的内部索引（用于邻居跳表查找）。
     * 默认实现基于 values 列表的位置，子类可覆盖以获得 O(1) 直接计算。
     */
    public int getInternalIndex(T value) {
        Integer idx = indexMap.get(value);
        if (idx == null) {
            throw new IllegalArgumentException("Value " + value + " is not in property " + name);
        }
        return idx;
    }

    // ==================== 实例方法 ====================

    public String getName() {
        return name;
    }

    public Class<T> getValueClass() {
        return valueClass;
    }

    /**
     * 返回所有可能值的不可变列表。列表的索引位置与 {@link #getInternalIndex} 一致。
     */
    public List<T> getValues() {
        return values;
    }

    /**
     * 返回所有值的字符串表示列表。
     */
    public List<String> getNames() {
        List<String> names = new ArrayList<>(values.size());
        for (T value : values) {
            names.add(value.toString());
        }
        return names;
    }

    /**
     * 返回此属性的值数量。
     */
    public int getValueCount() {
        return values.size();
    }

    // ==================== 静态工厂 ====================

    /**
     * 创建一个简单的自定义 Property（无专用子类时使用）。
     */
    public static <T extends Comparable<T>> Property<T> create(String name, Class<T> clazz, List<T> values) {
        return new SimpleProperty<>(name, clazz, values);
    }

    // ==================== Object ====================

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Property)) return false;
        Property<?> property = (Property<?>) o;
        return name.equals(property.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    // 内部实现：通用 Property
    private static class SimpleProperty<T extends Comparable<T>> extends Property<T> {
        SimpleProperty(String name, Class<T> clazz, List<T> values) {
            super(name, clazz, values);
        }
    }
}
