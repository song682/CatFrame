package decok.dfcdvadstf.catframe.component;

import decok.dfcdvadstf.catframe.component.predicates.ItemStackComponents;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataComponent 全局注册表——与 OreDictionary 平行的组件类型注册中心。
 * <p>
 * 设计定位：类似 {@code OreDictionary} 对 NBT 的关系，
 * DataComponents 是一个全局的、类型级别的注册表，负责：
 * <ul>
 *   <li>注册和查询 {@link DataComponentType}（组件类型）</li>
 *   <li>为每种 {@link Item} 注册默认组件值（原型）</li>
 *   <li>提供与 NBT 的双向转换基础（通过 {@link ComponentMigration}）</li>
 * </ul>
 * <p>
 * per-ItemStack 实例数据仍存储在 NBT (stackTagCompound) 中，
 * 运行时通过 {@link ItemStackComponents} 合并默认值 + NBT 实例数据。
 * <p>
 * 参考 26.1.2 {@code net.minecraft.core.component.DataComponents}。
 */
public final class DataComponents {

    private DataComponents() {}

    // ========== 内置组件类型（对标 26.1.2 DataComponents 常量） ==========

    /** 附魔光效覆盖（对标 26.1.2 ENCHANTMENT_GLINT_OVERRIDE） */
    public static final DataComponentType<Boolean> ENCHANTMENT_GLINT =
            DataComponentType.<Boolean>builder(new ResourceLocation("minecraft", "enchantment_glint"))
                    .persistent(ComponentSerializers.ofBoolean("EnchantmentGlint"))
                    .networkSynchronized(ComponentSerializers.ofBoolean("EnchantmentGlint"))
                    .build();

    // ========== 类型注册表 ==========

    private static final Map<ResourceLocation, DataComponentType<?>> BY_ID = new LinkedHashMap<>();
    private static final List<DataComponentType<?>> BY_NETWORK_ID = new ArrayList<>();
    private static final AtomicInteger NETWORK_ID_GEN = new AtomicInteger(0);

    // ========== 物品默认组件（原型） ==========

    /** 全局空原型 */
    private static final DataComponentMap EMPTY_DEFAULTS = DataComponentMap.EMPTY;

    /** Item → 默认组件映射 */
    private static final Map<Item, DataComponentMap> DEFAULTS = new IdentityHashMap<>();

    // ========== 类型注册 API ==========

    /**
     * 注册一个组件类型。
     *
     * @throws IllegalArgumentException 如果 ID 已注册
     */
    public static synchronized <T> DataComponentType<T> register(DataComponentType<T> type) {
        ResourceLocation id = type.getId();
        if (BY_ID.containsKey(id)) {
            throw new IllegalArgumentException("DataComponentType already registered: " + id);
        }
        int networkId = NETWORK_ID_GEN.getAndIncrement();
        type.networkId = networkId;
        BY_ID.put(id, type);
        BY_NETWORK_ID.add(type);
        return type;
    }

    /**
     * 构建并注册一个组件类型。
     */
    public static <T> DataComponentType<T> register(ResourceLocation id, DataComponentType.Builder<T> builder) {
        return register(builder.build());
    }

    // ========== 类型查询 API ==========

    /**
     * 通过 ID 查找组件类型。
     */
    @Nullable
    public static DataComponentType<?> get(ResourceLocation id) {
        return BY_ID.get(id);
    }

    /**
     * 通过网络编码 ID 查找组件类型。
     */
    @Nullable
    public static DataComponentType<?> byNetworkId(int networkId) {
        if (networkId < 0 || networkId >= BY_NETWORK_ID.size()) {
            return null;
        }
        return BY_NETWORK_ID.get(networkId);
    }

    /**
     * 返回已注册的组件类型数量。
     */
    public static int getNetworkCount() {
        return BY_NETWORK_ID.size();
    }

    /**
     * 返回所有已注册的组件类型（不可变视图）。
     */
    public static Collection<DataComponentType<?>> getAll() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /**
     * 返回所有持久化（非瞬态）的组件类型。
     */
    public static Collection<DataComponentType<?>> getPersistent() {
        List<DataComponentType<?>> result = new ArrayList<>();
        for (DataComponentType<?> type : BY_ID.values()) {
            if (!type.isTransient()) {
                result.add(type);
            }
        }
        return result;
    }

    // ========== 物品默认组件 API ==========

    /**
     * 为指定物品注册默认组件。
     */
    public static synchronized void registerDefaults(Item item, DataComponentMap defaults) {
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
     * 获取物品默认组件构建器。
     */
    public static DefaultsBuilder defaultsBuilder() {
        return new DefaultsBuilder();
    }

    // ========== DefaultsBuilder ==========

    /**
     * 物品默认组件构建器——支持链式为多种物品注册默认组件。
     */
    public static final class DefaultsBuilder {
        private final Map<Item, DataComponentMap.Builder> builders = new IdentityHashMap<>();
        private DataComponentMap.Builder currentBuilder;
        private Item currentItem;

        private DefaultsBuilder() {}

        public DefaultsBuilder item(Item item) {
            if (currentBuilder != null && currentItem != null) {
                builders.put(currentItem, currentBuilder);
            }
            currentItem = item;
            currentBuilder = DataComponentMap.builder();
            return this;
        }

        public <T> DefaultsBuilder with(DataComponentType<T> type, T value) {
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
                registerDefaults(entry.getKey(), entry.getValue().build());
            }
            builders.clear();
            currentItem = null;
            currentBuilder = null;
        }
    }
}
