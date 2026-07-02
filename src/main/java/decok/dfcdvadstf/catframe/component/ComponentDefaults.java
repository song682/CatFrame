package decok.dfcdvadstf.catframe.component;

import net.minecraft.item.Item;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 物品原型组件默认值注册中心。
 * <p>
 * 每个 Item 类型可以关联一组默认组件，作为 {@link PatchedDataComponentMap} 的原型。
 * 当物品没有显式设置组件值时，从默认值获取。
 */
public final class ComponentDefaults {

    private ComponentDefaults() {}

    /** 全局空原型 */
    private static final DataComponentMap EMPTY_DEFAULTS = DataComponentMap.EMPTY;

    /** Item → 默认组件映射 */
    private static final Map<Item, DataComponentMap> DEFAULTS = new IdentityHashMap<>();

    /**
     * 为指定物品注册默认组件。
     */
    public static synchronized void register(Item item, DataComponentMap defaults) {
        DEFAULTS.put(item, defaults);
    }

    /**
     * 获取指定物品的默认组件。
     */
    public static DataComponentMap getDefaults(Item item) {
        DataComponentMap map = DEFAULTS.get(item);
        return map != null ? map : EMPTY_DEFAULTS;
    }

    /**
     * 获取全局组件注册表构建器。
     * 在物品构造时（或 preInit/init 阶段）调用注册方法。
     */
    public static ComponentDefaultsBuilder builder() {
        return new ComponentDefaultsBuilder();
    }

    // ========== Builder ==========

    public static final class ComponentDefaultsBuilder {
        private final Map<Item, DataComponentMap.Builder> builders = new IdentityHashMap<>();
        private DataComponentMap.Builder currentBuilder;
        private Item currentItem;

        private ComponentDefaultsBuilder() {}

        public ComponentDefaultsBuilder item(Item item) {
            if (currentBuilder != null && currentItem != null) {
                builders.put(currentItem, currentBuilder);
            }
            currentItem = item;
            currentBuilder = DataComponentMap.builder();
            return this;
        }

        public <T> ComponentDefaultsBuilder with(DataComponentType<T> type, T value) {
            if (currentBuilder != null) {
                currentBuilder.set(type, value);
            }
            return this;
        }

        public void build() {
            if (currentBuilder != null && currentItem != null) {
                builders.put(currentItem, currentBuilder);
            }
            for (Map.Entry<Item, DataComponentMap.Builder> entry : builders.entrySet()) {
                register(entry.getKey(), entry.getValue().build());
            }
            builders.clear();
            currentItem = null;
            currentBuilder = null;
        }
    }
}
