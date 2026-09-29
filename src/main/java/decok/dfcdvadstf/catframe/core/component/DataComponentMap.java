package decok.dfcdvadstf.catframe.core.component;

import decok.dfcdvadstf.catframe.core.component.predicates.TypedDataComponent;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Immutable component map.
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.core.component.DataComponentMap}.
 * <p>
 * Provides the {@link #EMPTY} map, the {@link Builder}, and the {@link #composite} view.
 */
public interface DataComponentMap extends Iterable<TypedDataComponent<?>>, DataComponentGetter {

    /** Empty map constant */
    DataComponentMap EMPTY = new DataComponentMap() {
        @Nullable
        @Override
        public <T> T get(DataComponentType<? extends T> type) {
            return null;
        }

        @Override
        public Set<DataComponentType<?>> keySet() {
            return Collections.emptySet();
        }

        @Override
        public Iterator<TypedDataComponent<?>> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    };

    // ========== Static factories ==========

    /**
     * Composites two maps: prototype + overrides.
     * Reads look at overrides first and fall back to the prototype when absent.
     */
    static DataComponentMap composite(DataComponentMap prototype, DataComponentMap overrides) {
        return new DataComponentMap() {
            @Nullable
            @Override
            public <T> T get(DataComponentType<? extends T> type) {
                T value = overrides.get(type);
                return value != null ? value : prototype.get(type);
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                Set<DataComponentType<?>> keys = new HashSet<>(prototype.keySet());
                keys.addAll(overrides.keySet());
                return Collections.unmodifiableSet(keys);
            }

            @Override
            public Iterator<TypedDataComponent<?>> iterator() {
                Set<DataComponentType<?>> overridden = overrides.keySet();
                List<TypedDataComponent<?>> list = new ArrayList<>(size());
                for (DataComponentType<?> key : overrides.keySet()) {
                    TypedDataComponent<?> td = overrides.getTyped(key);
                    if (td != null) list.add(td);
                }
                for (DataComponentType<?> key : prototype.keySet()) {
                    if (!overridden.contains(key)) {
                        TypedDataComponent<?> td = prototype.getTyped(key);
                        if (td != null) list.add(td);
                    }
                }
                return list.iterator();
            }

            @Override
            public int size() {
                Set<DataComponentType<?>> keys = new HashSet<>(prototype.keySet());
                keys.addAll(overrides.keySet());
                return keys.size();
            }

            @Override
            public boolean isEmpty() {
                return prototype.isEmpty() && overrides.isEmpty();
            }
        };
    }

    static Builder builder() {
        return new Builder();
    }

    // ========== Instance methods ==========

    /** Returns the set of all component types. */
    Set<DataComponentType<?>> keySet();

    /** Returns the number of components. */
    int size();

    /** Whether this is an empty map. */
    boolean isEmpty();

    /** Whether a component of the given type is present. */
    default boolean has(DataComponentType<?> type) {
        return get(type) != null;
    }

    /** Returns a stream of all components. */
    default Stream<TypedDataComponent<?>> stream() {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator(), 0),
                false
        );
    }

    /** Filters the map, keeping only the components matching the predicate. */
    default DataComponentMap filter(Predicate<DataComponentType<?>> predicate) {
        DataComponentMap self = this;
        return new DataComponentMap() {
            @Nullable
            @Override
            public <T> T get(DataComponentType<? extends T> type) {
                return predicate.test(type) ? self.get(type) : null;
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                Set<DataComponentType<?>> result = new HashSet<>();
                for (DataComponentType<?> key : self.keySet()) {
                    if (predicate.test(key)) {
                        result.add(key);
                    }
                }
                return Collections.unmodifiableSet(result);
            }

            @Override
            public Iterator<TypedDataComponent<?>> iterator() {
                List<TypedDataComponent<?>> list = new ArrayList<>();
                for (TypedDataComponent<?> td : self) {
                    if (predicate.test(td.getType())) {
                        list.add(td);
                    }
                }
                return list.iterator();
            }

            @Override
            public int size() {
                int count = 0;
                for (DataComponentType<?> key : self.keySet()) {
                    if (predicate.test(key)) count++;
                }
                return count;
            }

            @Override
            public boolean isEmpty() {
                return size() == 0;
            }
        };
    }

    // ========== Builder ==========

    final class Builder {
        private final Map<DataComponentType<?>, Object> map = new IdentityHashMap<>();

        private Builder() {}

        public <T> Builder set(DataComponentType<T> type, @Nullable T value) {
            if (value != null) {
                map.put(type, value);
            } else {
                map.remove(type);
            }
            return this;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public Builder addAll(DataComponentMap other) {
            for (TypedDataComponent<?> entry : other) {
                DataComponentType type = entry.getType();
                map.put(type, entry.getValue());
            }
            return this;
        }

        /**
         * Builds the immutable map.
         */
        public DataComponentMap build() {
            if (map.isEmpty()) {
                return EMPTY;
            }
            final Map<DataComponentType<?>, Object> copy = new IdentityHashMap<>(map);
            return new DataComponentMap() {
                @Nullable
                @Override
                @SuppressWarnings("unchecked")
                public <T> T get(DataComponentType<? extends T> type) {
                    return (T) copy.get(type);
                }

                @Override
                public Set<DataComponentType<?>> keySet() {
                    return Collections.unmodifiableSet(copy.keySet());
                }

                @Override
                public Iterator<TypedDataComponent<?>> iterator() {
                    List<TypedDataComponent<?>> list = new ArrayList<>(copy.size());
                    for (Map.Entry<DataComponentType<?>, Object> e : copy.entrySet()) {
                        list.add(TypedDataComponent.fromEntry(e));
                    }
                    return list.iterator();
                }

                @Override
                public int size() {
                    return copy.size();
                }

                @Override
                public boolean isEmpty() {
                    return copy.isEmpty();
                }
            };
        }
    }

    // ========== Utility methods ==========

    /**
     * Wraps the map into an unmodifiable view.
     */
    static DataComponentMap unmodifiable(DataComponentMap map) {
        if (map == EMPTY) return EMPTY;
        return new DataComponentMap() {
            @Nullable
            @Override
            public <T> T get(DataComponentType<? extends T> type) {
                return map.get(type);
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return Collections.unmodifiableSet(map.keySet());
            }

            @Override
            public Iterator<TypedDataComponent<?>> iterator() {
                return new Iterator<TypedDataComponent<?>>() {
                    private final Iterator<TypedDataComponent<?>> it = map.iterator();

                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public TypedDataComponent<?> next() {
                        TypedDataComponent<?> td = it.next();
                        return TypedDataComponent.unchecked(td.getType(), td.getValue());
                    }
                };
            }

            @Override
            public int size() {
                return map.size();
            }

            @Override
            public boolean isEmpty() {
                return map.isEmpty();
            }
        };
    }
}
