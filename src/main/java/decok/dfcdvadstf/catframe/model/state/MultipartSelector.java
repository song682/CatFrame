package decok.dfcdvadstf.catframe.model.state;

import decok.dfcdvadstf.catframe.model.state.property.Property;

import java.util.*;

/**
 * Multipart 选择器。对标 26.1.2 的 multipart 条件匹配机制。
 * <p>
 * 封装从属性映射到匹配的 Variant 列表的选择逻辑。
 * 根据 {@link BlockstateJson.MultipartCase} 列表和当前块属性，
 * 返回所有条件匹配的 {@link BlockstateJson.Variant}。
 * <p>
 * 结果被缓存（以属性映射为键），在属性不变的情况下 O(1) 返回。
 *
 * <p>当前三处独立的 multipart 合并逻辑
 * （{@link decok.dfcdvadstf.catframe.model.StateBlockModel}、
 *  {@link decok.dfcdvadstf.catframe.model.StateProviderBlockModel}、
 *  VMM PublicRenderAPI）将统一委托于此。
 */
public class MultipartSelector {

    private final List<BlockstateJson.MultipartCase> cases;

    /**
     * 选择结果缓存。key = 属性映射（immutable 拷贝），value = 匹配的 Variant 列表。
     */
    private final Map<Map<String, String>, List<BlockstateJson.Variant>> cache = new HashMap<>();

    /**
     * @param cases multipart 条件列表（来自 {@link BlockstateJson#multipart}）
     */
    public MultipartSelector(List<BlockstateJson.MultipartCase> cases) {
        this.cases = cases;
    }

    // ==================== 选择入口 ====================

    /**
     * 根据属性映射选择所有匹配的 Variant。
     * 结果会被缓存（cache hit 时 O(1) 返回）。
     *
     * @param properties 块属性映射（key=属性名, value=属性值的字符串表示）
     * @return 匹配的 Variant 列表，可能为空
     */
    public List<BlockstateJson.Variant> select(Map<String, String> properties) {
        if (properties == null) properties = Collections.emptyMap();

        // 使用不可变 key 进行缓存查找
        Map<String, String> cacheKey = properties.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(properties));

        List<BlockstateJson.Variant> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<BlockstateJson.Variant> result = new ArrayList<>();
        for (BlockstateJson.MultipartCase mpc : cases) {
            boolean applies = (mpc.when == null) || mpc.when.matches(properties);
            if (applies && mpc.apply != null && mpc.apply.model != null) {
                result.add(mpc.apply);
            }
        }

        cache.put(cacheKey, Collections.unmodifiableList(result));
        return result;
    }

    /**
     * 根据 {@link CatBlockState} 选择所有匹配的 Variant。
     * 将 CatBlockState 转换为属性映射后委托给 {@link #select(Map)}。
     *
     * @param state CatBlockState 实例
     * @return 匹配的 Variant 列表，可能为空
     */
    public List<BlockstateJson.Variant> select(CatBlockState state) {
        if (state == null) return Collections.emptyList();

        Map<String, String> propMap = buildPropertyMap(state);
        return select(propMap);
    }

    // ==================== 缓存管理 ====================

    /**
     * 清除所有缓存的选择结果。当资源重载时应调用此方法。
     */
    public void clearCache() {
        cache.clear();
    }

    // ==================== 辅助方法 ====================

    /**
     * 将 CatBlockState 转换为用于 multipart 条件匹配的 Map。
     */
    private static Map<String, String> buildPropertyMap(CatBlockState state) {
        CatStateDefinition<?> def = state.getDefinition();
        if (def == null) return Collections.emptyMap();

        Property<?>[] properties = def.getProperties();
        if (properties.length == 0) return Collections.emptyMap();

        Map<String, String> result = new LinkedHashMap<>();
        for (Property<?> prop : properties) {
            result.put(prop.getName(), state.getValue(prop).toString());
        }
        return result;
    }
}
