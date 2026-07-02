package decok.dfcdvadstf.catframe.component;

/**
 * 标记值 - 用于只需要存在/不存在标记的组件。
 * <p>
 * 类似 26.1.2 的 {@code net.minecraft.core.component.DataComponentType.Unit}。
 * 使用布尔值表示存在状态，与 {@link Boolean#TRUE} 等价。
 */
public final class Unit {

    public static final Unit INSTANCE = new Unit();

    private Unit() {}

    @Override
    public String toString() {
        return "Unit";
    }

    /**
     * 解析布尔值到 Unit。
     */
    public static Unit of(boolean present) {
        return present ? INSTANCE : null;
    }
}
