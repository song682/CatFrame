package decok.dfcdvadstf.catframe.model.state.property;

import java.util.Arrays;

/**
 * 常用块状态属性常量与便捷构造工具。
 * <p>
 * 供 vanilla 与外部模组复用类型安全属性定义。属性值一律用 {@link String}，
 * 使其 {@code toString()} 与 blockstate JSON 中的 variant key 值精确一致
 * （避免枚举默认大写 {@code toString} 破坏匹配）。
 */
public final class StateDefinitions {

    private StateDefinitions() {}

    // ==================== 便捷构造 ====================

    /**
     * 创建一个字符串值属性。值的 {@code toString()} 即字符串本身，
     * 与 blockstate JSON 的 variant key 值直接对齐。
     *
     * @param name   属性名
     * @param values 取值（顺序决定内部索引与 meta 自然编码）
     * @return 字符串属性
     */
    public static Property<String> stringProp(String name, String... values) {
        return Property.create(name, String.class, Arrays.asList(values));
    }

    // ==================== 共享属性常量 ====================

    /** Minecraft 1.7.10 羊毛/染料 16 色顺序。 */
    public static final String[] COLORS = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime",
        "pink", "gray", "light_gray", "cyan", "purple", "blue",
        "brown", "green", "red", "black"
    };

    /** 16 色属性（wool / carpet / stained_glass / stained_hardened_clay）。 */
    public static final Property<String> COLOR = stringProp("color", COLORS);

    /** 原木/石英柱轴向。 */
    public static final Property<String> AXIS = stringProp("axis", "y", "x", "z");

    /** 台阶半层。 */
    public static final Property<String> SLAB_HALF = stringProp("half", "bottom", "top");
}
