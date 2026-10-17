package decok.dfcdvadstf.catframe.model.state;

import decok.dfcdvadstf.catframe.model.state.block.StateProviderBlockModel;
import decok.dfcdvadstf.catframe.model.state.property.Property;

import java.util.*;

/**
 * Multipart selector. Mirrors 26.1.2's multipart condition matching mechanism.
 * <p>
 * Encapsulates the selection logic that maps a property mapping to a matching Variant list.
 * Based on the {@link BlockstateJson.MultipartCase} list and the current block properties,
 * it returns all {@link BlockstateJson.Variant} entries whose conditions match.
 * <p>
 * Results are cached (keyed by the property mapping), returning in O(1) while the properties stay unchanged.
 *
 * <p>The currently scattered independent multipart merging logic
 * ({@link StateProviderBlockModel},
 *  VMM PublicRenderAPI) will all delegate here.
 */
public class MultipartSelector {

    private final List<BlockstateJson.MultipartCase> cases;

    /**
     * Selection result cache. key = property mapping (immutable copy), value = matching Variant list.
     */
    private final Map<Map<String, String>, List<BlockstateJson.Variant>> cache = new HashMap<>();

    /**
     * @param cases the multipart condition list (from {@link BlockstateJson#multipart})
     */
    public MultipartSelector(List<BlockstateJson.MultipartCase> cases) {
        this.cases = cases;
    }

    // ==================== Selection entry points ====================

    /**
     * Selects all matching Variants based on the property mapping.
     * Results are cached (returned in O(1) on a cache hit).
     *
     * @param properties the block property mapping (key = property name, value = string representation of the property value)
     * @return the matching Variant list, possibly empty
     */
    public List<BlockstateJson.Variant> select(Map<String, String> properties) {
        if (properties == null) properties = Collections.emptyMap();

        // Use an immutable key for the cache lookup
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
            if (applies && mpc.apply != null) {
                // Resolve variant from VariantEntry (use seed 0 for deterministic cached selection)
                BlockstateJson.Variant v = mpc.apply.getVariant(0);
                if (v != null && v.model != null) {
                    result.add(v);
                }
            }
        }

        cache.put(cacheKey, Collections.unmodifiableList(result));
        return result;
    }

    /**
     * Selects all matching Variants based on a {@link BlockState}.
     * Converts the CatBlockState into a property mapping and delegates to {@link #select(Map)}.
     *
     * @param state the CatBlockState instance
     * @return the matching Variant list, possibly empty
     */
    public List<BlockstateJson.Variant> select(BlockState state) {
        if (state == null) return Collections.emptyList();

        Map<String, String> propMap = buildPropertyMap(state);
        return select(propMap);
    }

    // ==================== Cache management ====================

    /**
     * Clears all cached selection results. Should be called on resource reload.
     */
    public void clearCache() {
        cache.clear();
    }

    // ==================== Helper methods ====================

    /**
     * Converts a CatBlockState into a Map used for multipart condition matching.
     */
    private static Map<String, String> buildPropertyMap(BlockState state) {
        BlockStateDefinition<?> def = state.getDefinition();
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
