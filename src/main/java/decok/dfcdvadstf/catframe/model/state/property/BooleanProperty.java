package decok.dfcdvadstf.catframe.model.state.property;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 布尔属性。值域为 {@code true} / {@code false}。
 *
 * <p>内部索引：{@code true → 0, false → 1}。
 *
 * <p>用法：
 * <pre>{@code
 * public static final Property<Boolean> LIT = BooleanProperty.create("lit");
 * }</pre>
 */
public final class BooleanProperty extends Property<Boolean> {

    private static final List<Boolean> VALUES = Collections.unmodifiableList(Arrays.asList(true, false));

    private BooleanProperty(String name) {
        super(name, Boolean.class, VALUES);
    }

    @Override
    public int getInternalIndex(Boolean value) {
        return value ? 0 : 1;
    }

    /**
     * 创建一个布尔属性。
     *
     * @param name 属性名称（如 "lit", "waterlogged"）
     * @return 新的 BooleanProperty 实例
     */
    public static BooleanProperty create(String name) {
        return new BooleanProperty(name);
    }
}
