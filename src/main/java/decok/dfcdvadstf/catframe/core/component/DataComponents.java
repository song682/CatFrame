package decok.dfcdvadstf.catframe.core.component;

import decok.dfcdvadstf.catframe.core.component.predicates.ItemStackComponents;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global DataComponent registry — a component type registry parallel to OreDictionary.
 * <p>
 * Design role: like the relationship {@code OreDictionary} has with NBT,
 * DataComponents is a global, type-level registry responsible for:
 * <ul>
 *   <li>registering and looking up {@link DataComponentType}s (component types)</li>
 *   <li>registering default component values (prototypes) for each {@link Item}</li>
 *   <li>providing the basis for two-way NBT conversion (via {@link ComponentMigration})</li>
 * </ul>
 * <p>
 * per-ItemStack instance data is still stored in NBT (stackTagCompound); at runtime the
 * defaults are merged with the NBT instance data through {@link ItemStackComponents}.
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.core.component.DataComponents}.
 */
public final class DataComponents {

    private DataComponents() {}

    // ========== Built-in component types (mirroring the 26.1.2 DataComponents constants) ==========

    /** Enchantment glint override (mirrors 26.1.2 ENCHANTMENT_GLINT_OVERRIDE) */
    public static final DataComponentType<Boolean> ENCHANTMENT_GLINT =
            DataComponentType.<Boolean>builder(new ResourceLocation("minecraft", "enchantment_glint"))
                    .persistent(ComponentSerializers.ofBoolean("EnchantmentGlint"))
                    .networkSynchronized(ComponentSerializers.ofBoolean("EnchantmentGlint"))
                    .build();

    /**
     * Item model mapping (mirrors 26.1.2 {@code minecraft:item_model} / {@code DataComponents.ITEM_MODEL}).
     * <p>
     * The value is a namespaced ID string (e.g. {@code "catframe:bluey_plushy"}) resolved to the
     * {@code assets/<namespace>/items/<path>.json} item model mapping; when that mapping is missing
     * or cannot be parsed, the invalid model ({@code builtin/missing}) is used.
     * <p>
     * Default semantics: when not set explicitly the item's registry ID is used
     * ({@code Item.itemRegistry.getNameForObject}), matching vanilla's
     * {@code model = ResourceKey::identifier}.
     */
    public static final DataComponentType<String> ITEM_MODEL =
            DataComponentType.<String>builder(new ResourceLocation("minecraft", "item_model"))
                    .persistent(ComponentSerializers.ofString("ItemModel"))
                    .networkSynchronized(ComponentSerializers.ofString("ItemModel"))
                    .build();

    /**
     * Tooltip style (mirrors 26.1.2 {@code minecraft:tooltip_style} / {@code DataComponents.TOOLTIP_STYLE}).
     * <p>
     * The value is a namespaced ID string (e.g. {@code "catframe:my_style"}); at render time it
     * resolves to the texture pair {@code assets/<namespace>/textures/gui/tooltips/<path>_background.png}
     * and {@code _frame.png}; when unset, the default style textures are used.
     */
    public static final DataComponentType<String> TOOLTIP_STYLE =
            DataComponentType.<String>builder(new ResourceLocation("minecraft", "tooltip_style"))
                    .persistent(ComponentSerializers.ofString("TooltipStyle"))
                    .networkSynchronized(ComponentSerializers.ofString("TooltipStyle"))
                    .build();

    // ========== Type registry ==========

    private static final Map<ResourceLocation, DataComponentType<?>> BY_ID = new LinkedHashMap<>();
    private static final List<DataComponentType<?>> BY_NETWORK_ID = new ArrayList<>();
    private static final AtomicInteger NETWORK_ID_GEN = new AtomicInteger(0);

    // ========== Per-item default components (prototypes) ==========

    /** Global empty prototype */
    private static final DataComponentMap EMPTY_DEFAULTS = DataComponentMap.EMPTY;

    /** Item → default component mapping */
    private static final Map<Item, DataComponentMap> DEFAULTS = new IdentityHashMap<>();

    // ========== Type registration API ==========

    /**
     * Registers a component type.
     *
     * @throws IllegalArgumentException if the ID is already registered
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
     * Builds and registers a component type.
     */
    public static <T> DataComponentType<T> register(ResourceLocation id, DataComponentType.Builder<T> builder) {
        return register(builder.build());
    }

    // ========== Type lookup API ==========

    /**
     * Looks up a component type by ID.
     */
    @Nullable
    public static DataComponentType<?> get(ResourceLocation id) {
        return BY_ID.get(id);
    }

    /**
     * Looks up a component type by network-encoded ID.
     */
    @Nullable
    public static DataComponentType<?> byNetworkId(int networkId) {
        if (networkId < 0 || networkId >= BY_NETWORK_ID.size()) {
            return null;
        }
        return BY_NETWORK_ID.get(networkId);
    }

    /**
     * Returns the number of registered component types.
     */
    public static int getNetworkCount() {
        return BY_NETWORK_ID.size();
    }

    /**
     * Returns all registered component types (immutable view).
     */
    public static Collection<DataComponentType<?>> getAll() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /**
     * Returns all persistent (non-transient) component types.
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

    // ========== Per-item default component API ==========

    /**
     * Registers the default components for the given item.
     */
    public static synchronized void registerDefaults(Item item, DataComponentMap defaults) {
        DEFAULTS.put(item, defaults);
    }

    /**
     * Returns the default components of the given item.
     */
    public static DataComponentMap getDefaults(Item item) {
        DataComponentMap map = DEFAULTS.get(item);
        return map != null ? map : EMPTY_DEFAULTS;
    }

    /**
     * Returns a builder for per-item default components.
     */
    public static DefaultsBuilder defaultsBuilder() {
        return new DefaultsBuilder();
    }

    // ========== DefaultsBuilder ==========

    /**
     * Per-item default component builder — supports chaining defaults for multiple items.
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
