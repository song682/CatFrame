package decok.dfcdvadstf.catframe.component;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 不可变的组件映射。
 * <p>
 * 参考 26.1.2 {@code net.minecraft.core.component.DataComponentMap}。
 * <p>
 * 提供 {@link #EMPTY} 空映射、{@link #Builder} 构建器、{@link #composite} 组合视图。
 */
public interface DataComponentMap extends Iterable<TypedDataComponent<?>>, DataComponentGetter {

    /** 空映射常量 */
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

    // ========== 静态工厂 ==========

    /**
     * 组合两个映射：原型 + 覆写。
     * 读取时先查 overrides，不存在时回退到 prototype。
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

    // ========== 实例方法 ==========

    /** 返回所有组件类型的集合。 */
    Set<DataComponentType<?>> keySet();

    /** 返回组件数量。 */
    int size();

    /** 是否为空映射。 */
    boolean isEmpty();

    /** 是否包含指定类型的组件。 */
    default boolean has(DataComponentType<?> type) {
        return get(type) != null;
    }

    /** 返回所有组件的流。 */
    default Stream<TypedDataComponent<?>> stream() {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator(), 0),
                false
        );
    }

    /** 过滤组件映射，仅保留满足条件的组件。 */
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
         * 构建不可变映射。
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

    // ========== 工具方法 ==========

    /**
     * 将映射转为不可修改的视图。
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
