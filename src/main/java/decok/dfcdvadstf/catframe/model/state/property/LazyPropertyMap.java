package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Lazy property Map: calls the provider to compute a value only when the decision tree actually
 * {@link #get(Object)}s it.
 * <p>
 * Most decision trees access only 1-3 properties per frame; lazy evaluation avoids unnecessary
 * NBT reads and player state queries. Computed values are cached in the internal {@link HashMap},
 * making repeated access within the same frame zero-cost.
 * <p>
 * This Map is read-only — it does not support modifying operations such as {@link #put} and
 * {@link #remove}.
 */
public class LazyPropertyMap implements Map<String, Comparable<?>> {

    /** Property provider registry (key → provider) */
    private final Map<String, ItemPropertyProvider> providers;

    /** The bound ItemStack */
    private final ItemStack stack;

    /** The bound RenderPhase */
    private final RenderPhase phase;

    /** Cache of computed values */
    private final Map<String, Comparable<?>> cache = new HashMap<>();

    /**
     * @param providers the property provider registry
     * @param stack     the ItemStack currently being rendered
     * @param phase     the current render phase
     */
    public LazyPropertyMap(Map<String, ItemPropertyProvider> providers, ItemStack stack, RenderPhase phase) {
        this.providers = providers;
        this.stack = stack;
        this.phase = phase;
    }

    @Override
    public Comparable<?> get(Object key) {
        if (!(key instanceof String)) return null;
        String name = (String) key;

        // Check the cache first
        if (cache.containsKey(name)) return cache.get(name);

        // Look up the provider
        ItemPropertyProvider provider = providers.get(name);
        if (provider == null) return null;

        // Compute and cache
        Comparable<?> value = provider.compute(stack, phase);
        cache.put(name, value);
        return value;
    }

    @Override
    public boolean containsKey(Object key) {
        return providers.containsKey(key);
    }

    @Override
    public int size() {
        return providers.size();
    }

    @Override
    public boolean isEmpty() {
        return providers.isEmpty();
    }

    @Override
    public Set<String> keySet() {
        return providers.keySet();
    }

    @Override
    public Collection<Comparable<?>> values() {
        // Lazily evaluate all values (only triggered during debugging/iteration)
        Map<String, Comparable<?>> all = new HashMap<>();
        for (String key : providers.keySet()) {
            all.put(key, get(key));
        }
        return all.values();
    }

    @Override
    public Set<Entry<String, Comparable<?>>> entrySet() {
        Map<String, Comparable<?>> all = new HashMap<>();
        for (String key : providers.keySet()) {
            all.put(key, get(key));
        }
        return all.entrySet();
    }

    // ==================== Unsupported modifying operations ====================

    @Override
    public Comparable<?> put(String key, Comparable<?> value) {
        throw new UnsupportedOperationException("LazyPropertyMap is read-only");
    }

    @Override
    public Comparable<?> remove(Object key) {
        throw new UnsupportedOperationException("LazyPropertyMap is read-only");
    }

    @Override
    public void putAll(Map<? extends String, ? extends Comparable<?>> m) {
        throw new UnsupportedOperationException("LazyPropertyMap is read-only");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("LazyPropertyMap is read-only");
    }

    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException("Use containsKey instead");
    }
}
