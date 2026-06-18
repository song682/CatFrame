package decok.dfcdvadstf.catframe.model.state;

import decok.dfcdvadstf.catframe.model.state.property.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 块状态持有者。类似于 1.21.5 的 {@code BlockState}（内部继承自 {@code StateHolder}）。
 *
 * <p>每个 CatBlockState 实例对应一组确定的属性值组合，通过 {@link CatStateDefinition.Builder}
 * 在构建时预创建为单例。运行时的所有状态转换都通过邻居跳表 O(1) 完成，无需分配新对象。
 *
 * <p><b>邻居跳表机制</b>：
 * <pre>
 *              neighbor[0] (TYPE 维度)
 *              ┌────────┬────────┬────────┐
 *              │ BOTTOM │  TOP   │ DOUBLE │
 * neighbor[1]  ┼────────┼────────┼────────┤
 * (WATERLOGGED)│ state0 │ state1 │ state2 │  ← true
 *              │ state3 │ state4 │ state5 │  ← false
 *              └────────┴────────┴────────┘
 *
 * state0.setValue(TYPE, TOP)    → state1  (neighbor[0][1])
 * state0.setValue(WATERLOGGED, true) → state3  (neighbor[1][0])
 * </pre>
 */
public final class CatBlockState {

    // [C2 修复] 去掉 final，由 CatStateDefinition.Builder.create() 回填
    CatStateDefinition<?> definition;

    /**
     * 当前状态的属性值数组。索引与 definition 的 properties 数组位置对应。
     */
    final Comparable<?>[] values; // package-private

    /**
     * 邻居跳表。neighbors[p][v] 将属性 p 设置为值 v（内部索引）后得到的目标状态。
     */
    CatBlockState[][] neighbors; // package-private

    // 仅由 CatStateDefinition.Builder.create() 调用
    CatBlockState(CatStateDefinition<?> definition, Comparable<?>[] values) {
        this.definition = definition;
        this.values = values;
    }

    // ==================== 查询 ====================

    /**
     * 获取指定属性的当前值。
     *
     * @param prop 要查询的属性
     * @param <T>  值的类型
     * @return 当前属性值
     */
    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> T getValue(Property<T> prop) {
        int idx = definition.getPropertyIndex(prop);
        return (T) values[idx];
    }

    /**
     * O(1) 状态转换：将指定属性设置为新值，并通过邻居跳表返回目标状态。
     * 不修改当前状态实例。
     *
     * @param prop  要设置的属性
     * @param value 新值
     * @param <T>   值的类型
     * @return 属性设置后的目标 CatBlockState
     */
    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> CatBlockState setValue(Property<T> prop, T value) {
        int propIdx = definition.getPropertyIndex(prop);
        int valIdx = prop.getInternalIndex(value);
        return neighbors[propIdx][valIdx];
    }

    /**
     * 返回此 CatBlockState 的定义。
     */
    public CatStateDefinition<?> getDefinition() {
        return definition;
    }

    // ==================== 变体键匹配 ====================

    /**
     * 生成变体匹配键（与 blockstate JSON 中的 variant key 格式相同）。
     *
     * <p>例如，对于属性 "bites=3" → {@code "bites=3"}，
     * 多属性 "facing=north,half=upper" → {@code "facing=north,half=upper"}（排序后）。
     *
     * @return 变体键字符串。无属性时返回 "normal"。
     */
    public String toVariantKey() {
        Property<?>[] properties = definition.getProperties();
        if (properties.length == 0) return "normal";

        // 收集非默认属性值
        CatBlockState anyState = definition.any();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < properties.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(properties[i].getName()).append('=').append(values[i]);
        }
        return sb.toString();
    }

    /**
     * 检查此状态是否匹配给定的 {@code Map<String, String>} 属性映射（用于 multipart when 条件匹配）。
     * 映射中的每个键值对必须与此状态对应的属性值一致。此状态可以有映射中未出现的属性。
     *
     * @param properties 要匹配的属性映射（key=属性名, value=属性值的字符串表示）
     * @return 如果所有指定属性值都匹配则返回 true
     */
    public boolean matches(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) return true;
        Property<?>[] props = definition.getProperties();
        for (int i = 0; i < props.length; i++) {
            String expected = properties.get(props[i].getName());
            if (expected != null) {
                if (!values[i].toString().equals(expected)) return false;
            }
        }
        return true;
    }

    /**
     * 检查此状态是否匹配单个属性的值条件（OR | 管道分隔）。
     *
     * @param propertyName 属性名
     * @param allowedValues 管道分隔的允许值（如 "north|south"）
     * @return 如果当前属性值在 allowedValues 中返回 true
     */
    public boolean matchesProperty(String propertyName, String allowedValues) {
        Property<?>[] props = definition.getProperties();
        String[] allowed = allowedValues.split("\\|");
        for (int i = 0; i < props.length; i++) {
            if (props[i].getName().equals(propertyName)) {
                String current = values[i].toString();
                for (String a : allowed) {
                    if (a.equals(current)) return true;
                }
                return false;
            }
        }
        return false;
    }

    // ==================== 值访问 ====================

    /**
     * 按索引获取当前状态中所有属性值的字符串表示列表。
     * 用于调试和序列化。
     */
    public List<String> getValueNames() {
        List<String> names = new ArrayList<>(values.length);
        for (Comparable<?> v : values) {
            names.add(v.toString());
        }
        return names;
    }

    /**
     * 返回此状态的属性值数量。
     */
    public int getValueCount() {
        return values.length;
    }

    // ==================== Object ====================

    @Override
    public String toString() {
        return "CatBlockState{" + toVariantKey() + "}";
    }

    @Override
    public int hashCode() {
        // 由于所有状态都是单例，直接使用对象引用
        return super.hashCode();
    }
}
