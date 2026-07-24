package decok.dfcdvadstf.catframe.model.state;

import decok.dfcdvadstf.catframe.model.state.property.Property;

import java.util.*;

/**
 * 块状态定义管理器。类似于 1.21.5 的 {@code StateDefinition<Block, BlockState>}。
 *
 * <p>管理一个所属者（通常是 Block）的所有属性和状态组合。
 * 通过 {@link Builder} 模式构建：
 * <ol>
 *   <li>定义属性（{@link Builder#add(Property[])}）</li>
 *   <li>创建所有状态的笛卡尔积，预计算邻居跳表（{@link Builder#create()}）</li>
 *   <li>运行时通过 {@link CatBlockState#setValue} O(1) 状态转换</li>
 * </ol>
 *
 * <h3>构建算法</h3>
 * <ul>
 *   <li><b>无属性</b>：1 个状态（单例）</li>
 *   <li><b>1 个属性 N 个值</b>：N 个状态，邻居跳表大小为 1×N</li>
 *   <li><b>多属性</b>：笛卡尔积，邻居跳表大小 N_(props)×N_(values_each)</li>
 * </ul>
 *
 * @param <O> 所属者类型（通常是 Block）
 */
public final class CatStateDefinition<O> {

    private final O owner;
    private final Property<?>[] properties;
    private final CatBlockState[] allStates;
    private final Map<Property<?>, Integer> propertyIndex;
    private final CatBlockState defaultState;

    // 由 Builder.create() 回填的 meta 编解码相关字段
    // dynamicFlags[i]：属性 i 是否为动态属性（运行时从世界计算，不参与 meta 解码）
    private boolean[] dynamicFlags;
    // 静态属性（参与 meta 解码），保持声明顺序
    private Property<?>[] staticProperties;
    // meta 编解码器；为 null 时使用静态属性的笛卡尔积自然编码
    private MetaCodec metaCodec;
    // 常驻表：resolvedByMeta[meta] 直接给出 meta 对应的 CatBlockState（长度 16）
    private CatBlockState[] resolvedByMeta;

    /**
     * meta 编解码器。仅负责把 metadata 解码为「静态属性」的值数组
     * （按静态属性声明顺序），动态属性不参与。
     */
    @FunctionalInterface
    public interface MetaCodec {
        /**
         * 从 metadata 解出静态属性值。
         *
         * @param meta 方块 metadata（0-15）
         * @return 按静态属性声明顺序排列的值数组
         */
        Comparable<?>[] decode(int meta);
    }

    // 由 Builder.create() 调用
    CatStateDefinition(O owner, Property<?>[] properties,
                       CatBlockState[] allStates,
                       Map<Property<?>, Integer> propertyIndex,
                       CatBlockState defaultState) {
        this.owner = owner;
        this.properties = properties;
        this.allStates = allStates;
        this.propertyIndex = propertyIndex;
        this.defaultState = defaultState;
    }

    // ==================== 查询 ====================

    /**
     * 返回所属者。
     */
    public O getOwner() {
        return owner;
    }

    /**
     * 返回所有已定义的属性数组。
     */
    public Property<?>[] getProperties() {
        return properties;
    }

    /**
     * 返回指定属性在属性数组中的索引。
     */
    public int getPropertyIndex(Property<?> prop) {
        Integer idx = propertyIndex.get(prop);
        if (idx == null) {
            throw new IllegalArgumentException("Property " + prop.getName() + " is not in this definition");
        }
        return idx;
    }

    /**
     * 返回默认状态（第一个状态，所有属性取第一个值）。
     */
    public CatBlockState any() {
        return defaultState;
    }

    /**
     * 返回所有预创建的状态实例（不可修改）。
     */
    public List<CatBlockState> getAllStates() {
        return Collections.unmodifiableList(Arrays.asList(allStates));
    }

    /**
     * 返回状态总数。
     */
    public int getStateCount() {
        return allStates.length;
    }

    /**
     * 检查是否有任何已定义的属性。
     */
    public boolean hasProperties() {
        return properties.length > 0;
    }

    /**
     * 检查是否只有一个状态（无属性或单值）。
     */
    public boolean isSingletonState() {
        return allStates.length == 1;
    }

    /**
     * 返回属性数量。
     */
    public int getPropertyCount() {
        return properties.length;
    }

    /**
     * 根据属性值组合查找对应的 CatBlockState。
     *
     * @param values 按属性数组顺序对应的值
     * @return 匹配的 CatBlockState
     * @throws IllegalArgumentException 如果找不到匹配的状态
     */
    @SuppressWarnings("unchecked")
    public CatBlockState findState(Comparable<?>... values) {
        if (values.length != properties.length) {
            throw new IllegalArgumentException("Expected " + properties.length + " values, got " + values.length);
        }

        int idx = findStateIndex(values);
        return allStates[idx];
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int findStateIndex(Comparable<?>[] values) {
        int idx = 0;
        int stride = 1;
        for (int i = properties.length - 1; i >= 0; i--) {
            Property prop = properties[i];
            int valIdx = prop.getInternalIndex(values[i]);
            idx += valIdx * stride;
            stride *= prop.getValueCount();
        }
        return idx;
    }

    // ==================== meta 编解码 / 常驻表 ====================

    /**
     * 返回指定属性是否为动态属性（运行时从世界计算，不参与 meta 解码）。
     */
    public boolean isDynamic(Property<?> prop) {
        Integer idx = propertyIndex.get(prop);
        return idx != null && dynamicFlags != null && dynamicFlags[idx];
    }

    /**
     * 返回参与 meta 解码的静态属性数组（声明顺序）。
     */
    public Property<?>[] getStaticProperties() {
        return staticProperties;
    }

    /**
     * 根据 metadata 查表得到对应的常驻 CatBlockState。
     * <p>动态属性取默认值（第一个值），运行时可再通过 {@link CatBlockState#setValue} O(1) 跳转。
     *
     * @param meta 方块 metadata（0-15）
     * @return 该 meta 对应的常驻状态
     */
    public CatBlockState getStateFromMeta(int meta) {
        if (resolvedByMeta != null) {
            return resolvedByMeta[meta & 15];
        }
        return computeStateFromMeta(meta);
    }

    private CatBlockState computeStateFromMeta(int meta) {
        Comparable<?>[] staticValues = metaCodec != null
                ? metaCodec.decode(meta)
                : defaultDecode(meta);

        // 拼出完整属性值数组：静态属性取解码值，动态属性取默认值
        Comparable<?>[] full = new Comparable<?>[properties.length];
        int si = 0;
        for (int i = 0; i < properties.length; i++) {
            if (dynamicFlags != null && dynamicFlags[i]) {
                full[i] = properties[i].getValues().get(0);
            } else {
                full[i] = staticValues[si++];
            }
        }
        return findState(full);
    }

    /**
     * 缺省 meta 解码：把 meta 当作静态属性的笛卡尔积自然索引（最后一个属性变化最快）。
     */
    private Comparable<?>[] defaultDecode(int meta) {
        Property<?>[] statics = staticProperties;
        Comparable<?>[] result = new Comparable<?>[statics.length];
        int remaining = meta;
        for (int i = statics.length - 1; i >= 0; i--) {
            Property<?> prop = statics[i];
            int count = prop.getValueCount();
            int valIdx = remaining % count;
            remaining /= count;
            result[i] = prop.getValues().get(valIdx);
        }
        return result;
    }

    // ==================== Builder ====================

    /**
     * CatStateDefinition 的构建器。使用
     * <pre>{@code
     * CatStateDefinition<Block> def = new CatStateDefinition.Builder<>(myBlock)
     *     .add(TYPE, WATERLOGGED)
     *     .create();
     * }</pre>
     *
     * @param <O> 所属者类型
     */
    public static class Builder<O> {

        private final O owner;
        private final List<Property<?>> props = new ArrayList<>();
        private final Set<String> usedNames = new HashSet<>();
        private final Set<Property<?>> dynamicProps = new HashSet<>();
        private MetaCodec metaCodec;

        /**
         * @param owner 状态定义的所属者（如 Block 实例）
         */
        public Builder(O owner) {
            if (owner == null) {
                throw new IllegalArgumentException("Owner must not be null");
            }
            this.owner = owner;
        }

        /**
         * 添加一个或多个属性。属性名称不能重复。
         *
         * @param properties 要添加的属性
         * @return 此 Builder 实例（链式调用）
         * @throws IllegalArgumentException 如果属性名重复
         */
        public Builder<O> add(Property<?>... properties) {
            for (Property<?> prop : properties) {
                if (!usedNames.add(prop.getName())) {
                    throw new IllegalArgumentException("Duplicate property name: " + prop.getName());
                }
                props.add(prop);
            }
            return this;
        }

        /**
         * 标记一个或多个属性为「动态属性」（运行时从世界计算，不参与 meta 解码）。
         * <p>动态属性仍需先通过 {@link #add(Property[])} 添加，以保证参与笛卡尔积与跳表。
         * 典型用例：stairs 的 {@code shape}、pane 的 {@code north/east/south/west}。
         *
         * @param properties 要标记为动态的属性
         * @return 此 Builder 实例（链式调用）
         */
        public Builder<O> dynamic(Property<?>... properties) {
            Collections.addAll(dynamicProps, properties);
            return this;
        }

        /**
         * 设置非规则 meta 编码的解码器（如 log 的 wood+axis、slab 的 half+variant）。
         * 不设置时默认用静态属性的笛卡尔积自然编码。
         *
         * @param codec meta 解码器
         * @return 此 Builder 实例（链式调用）
         */
        public Builder<O> metaCodec(MetaCodec codec) {
            this.metaCodec = codec;
            return this;
        }

        /**
         * 构建 CatStateDefinition。计算所有状态组合的笛卡尔积并构建邻居跳表。
         *
         * @return 新的 CatStateDefinition 实例
         */
        public CatStateDefinition<O> create() {
            int propCount = props.size();
            Property<?>[] properties = props.toArray(new Property<?>[0]);

            // 计算属性索引映射
            Map<Property<?>, Integer> propIndexMap = new HashMap<>();
            for (int i = 0; i < propCount; i++) {
                propIndexMap.put(properties[i], i);
            }

            // 计算每个属性的值数量
            int[] valueCounts = new int[propCount];
            for (int i = 0; i < propCount; i++) {
                valueCounts[i] = properties[i].getValueCount();
            }

            // 总状态数 = 各属性值数量的乘积
            int totalStates = 1;
            for (int c : valueCounts) {
                totalStates *= c;
            }

            // 创建所有状态
            CatBlockState[] states = new CatBlockState[Math.max(totalStates, 1)];
            Comparable<?>[][] allValues = new Comparable<?>[states.length][propCount];

            if (propCount == 0) {
                // 无属性：单例
                states[0] = new CatBlockState(null, new Comparable<?>[0]);
            } else {
                // 使用计数器生成所有值组合（最后一个属性变化最快）
                int[] counters = new int[propCount];
                for (int stateIdx = 0; stateIdx < totalStates; stateIdx++) {
                    for (int p = 0; p < propCount; p++) {
                        allValues[stateIdx][p] = properties[p].getValues().get(counters[p]);
                    }
                    states[stateIdx] = new CatBlockState(null, allValues[stateIdx]);

                    // 递增计数器
                    for (int p = propCount - 1; p >= 0; p--) {
                        counters[p]++;
                        if (counters[p] < valueCounts[p]) break;
                        counters[p] = 0;
                    }
                }
            }

            // 计算 stride（用于 O(1) 邻居定位）
            int[] strides = new int[propCount];
            if (propCount > 0) {
                strides[propCount - 1] = 1;
                for (int i = propCount - 2; i >= 0; i--) {
                    strides[i] = strides[i + 1] * valueCounts[i + 1];
                }
            }

            // 构建邻居跳表
            CatStateDefinition<O> definition = new CatStateDefinition<>(owner, properties, states, propIndexMap, states[0]);

            // 回填 meta 编解码相关字段
            definition.metaCodec = this.metaCodec;
            definition.dynamicFlags = new boolean[propCount];
            List<Property<?>> staticList = new ArrayList<>(propCount);
            for (int i = 0; i < propCount; i++) {
                if (dynamicProps.contains(properties[i])) {
                    definition.dynamicFlags[i] = true;
                } else {
                    staticList.add(properties[i]);
                }
            }
            definition.staticProperties = staticList.toArray(new Property<?>[0]);

            // [C2 修复] 回填 definition 到所有 CatBlockState 实例
            // （创建 CatBlockState 时 definition 为 null，因为 definition 尚未构建）
            for (CatBlockState s : states) {
                s.definition = definition;
            }

            for (int stateIdx = 0; stateIdx < states.length; stateIdx++) {
                CatBlockState state = states[stateIdx];
                state.neighbors = new CatBlockState[propCount][];

                for (int p = 0; p < propCount; p++) {
                    int valCount = valueCounts[p];
                    state.neighbors[p] = new CatBlockState[valCount];

                    // 解码当前状态在该属性的 counter 值
                    int currentCounter = 0;
                    int remaining = stateIdx;
                    for (int i = propCount - 1; i >= 0; i--) {
                        int valAtI = remaining % valueCounts[i];
                        remaining /= valueCounts[i];
                        if (i == p) currentCounter = valAtI;
                    }

                    for (int v = 0; v < valCount; v++) {
                        // O(1) 计算目标状态索引
                        int delta = (v - currentCounter) * strides[p];
                        state.neighbors[p][v] = states[stateIdx + delta];
                    }
                }
            }

            // 预填常驻表 resolvedByMeta[0..15]
            definition.resolvedByMeta = new CatBlockState[16];
            for (int meta = 0; meta < 16; meta++) {
                definition.resolvedByMeta[meta] = definition.computeStateFromMeta(meta);
            }

            return definition;
        }
    }
}
