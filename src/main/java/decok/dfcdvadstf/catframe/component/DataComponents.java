package decok.dfcdvadstf.catframe.component;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据组件注册中心。
 * <p>
 * 管理所有已知的 {@link DataComponentType}，分配网络编码 ID。
 * 参考 26.1.2 {@code net.minecraft.core.component.DataComponents}。
 */
public final class DataComponents {

    private DataComponents() {}

    // ========== 注册表 ==========

    private static final Map<ResourceLocation, DataComponentType<?>> BY_ID = new LinkedHashMap<>();
    private static final List<DataComponentType<?>> BY_NETWORK_ID = new ArrayList<>();
    private static final AtomicInteger NETWORK_ID_GEN = new AtomicInteger(0);

    // ========== 注册 API ==========

    /**
     * 注册一个数据组件类型。
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

    // ========== 查询 API ==========

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
}
