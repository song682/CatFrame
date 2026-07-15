package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.item.ItemStack;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 惰性属性 Map：只在决策树实际 {@link #get(Object)} 时才调用 provider 计算值。
 * <p>
 * 大多数决策树每帧只访问 1-3 个属性，惰性求值避免了无谓的 NBT 读取和玩家状态查询。
 * 已计算的值缓存在内部 {@link HashMap} 中，同一帧内重复访问零开销。
 * <p>
 * 此 Map 是只读的——不支持 {@link #put}、{@link #remove} 等修改操作。
 */
public class LazyPropertyMap implements Map<String, Comparable<?>> {

    /** 属性提供者注册表（key → provider） */
    private final Map<String, ItemPropertyProvider> providers;

    /** 绑定的 ItemStack */
    private final ItemStack stack;

    /** 绑定的 RenderPhase */
    private final RenderPhase phase;

    /** 已计算值的缓存 */
    private final Map<String, Comparable<?>> cache = new HashMap<>();

    /**
     * @param providers 属性提供者注册表
     * @param stack     当前渲染的 ItemStack
     * @param phase     当前渲染阶段
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

        // 先查缓存
        if (cache.containsKey(name)) return cache.get(name);

        // 查提供者
        ItemPropertyProvider provider = providers.get(name);
        if (provider == null) return null;

        // 计算并缓存
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
        // 惰性求值所有值（仅在调试/遍历时触发）
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

    // ==================== 不支持的修改操作 ====================

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
