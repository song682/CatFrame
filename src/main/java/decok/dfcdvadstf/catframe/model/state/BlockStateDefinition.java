package decok.dfcdvadstf.catframe.model.state;

import decok.dfcdvadstf.catframe.model.state.property.MetaBlockProperty;
import decok.dfcdvadstf.catframe.model.state.property.Property;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Block state definition manager. Similar to 26.1's {@code StateDefinition<Block, BlockState>}.
 *
 * <p>Manages all the properties and state combinations of an owner (usually a Block).
 * Built via the {@link Builder} pattern:
 * <ol>
 *   <li>Define properties ({@link Builder#add(Property[])})</li>
 *   <li>Create the cartesian product of all states and precompute the neighbor jump table ({@link Builder#create()})</li>
 *   <li>Perform O(1) state transitions at runtime via {@link BlockState#setValue}</li>
 * </ol>
 *
 * <h3>Construction algorithm</h3>
 * <ul>
 *   <li><b>No properties</b>: 1 state (singleton)</li>
 *   <li><b>1 property with N values</b>: N states, neighbor jump table size 1×N</li>
 *   <li><b>Multiple properties</b>: cartesian product, neighbor jump table size N_(props)×N_(values_each)</li>
 * </ul>
 *
 * @param <O> the owner type (usually Block)
 */
public final class BlockStateDefinition<O> {

    private final O owner;
    private final Property<?>[] properties;
    private final BlockState[] allStates;
    private final Map<Property<?>, Integer> propertyIndex;
    private final BlockState defaultState;

    // Meta encode/decode related fields backfilled by Builder.create()
    // dynamicFlags[i]: whether property i is a dynamic property (computed at runtime from the world, not involved in meta decoding)
    private boolean[] dynamicFlags;
    // Static properties (involved in meta decoding), kept in declaration order
    private Property<?>[] staticProperties;
    // Meta codec; when null, uses the natural cartesian-product encoding of the static properties
    private MetaCodec metaCodec;
    // When true, every static property is a MetaBlockProperty and decodes its own metadata bit
    // field (distributed byte mapping). When false, the definition falls back to metaCodec or the
    // natural cartesian-product defaultDecode. Backfilled by Builder.create().
    private boolean usesMetaProperties;
    // Resident table: resolvedByMeta[meta] directly yields the CatBlockState for that meta (length 16)
    private BlockState[] resolvedByMeta;

    /**
     * Meta codec. Only responsible for decoding metadata into the value array of the
     * "static properties" (in static property declaration order); dynamic properties do
     * not participate.
     */
    @FunctionalInterface
    public interface MetaCodec {
        /**
         * Decodes the static property values from metadata.
         *
         * @param meta the block metadata (0-15)
         * @return the value array ordered by static property declaration order
         */
        Comparable<?>[] decode(int meta);
    }

    // Called by Builder.create()
    BlockStateDefinition(O owner, Property<?>[] properties,
                         BlockState[] allStates,
                         Map<Property<?>, Integer> propertyIndex,
                         BlockState defaultState) {
        this.owner = owner;
        this.properties = properties;
        this.allStates = allStates;
        this.propertyIndex = propertyIndex;
        this.defaultState = defaultState;
    }

    // ==================== Queries ====================

    /**
     * Returns the owner.
     */
    public O getOwner() {
        return owner;
    }

    /**
     * Returns the array of all defined properties.
     */
    public Property<?>[] getProperties() {
        return properties;
    }

    /**
     * Returns the index of the given property in the property array.
     */
    public int getPropertyIndex(Property<?> prop) {
        Integer idx = propertyIndex.get(prop);
        if (idx == null) {
            throw new IllegalArgumentException("Property " + prop.getName() + " is not in this definition");
        }
        return idx;
    }

    /**
     * Returns the default state (the first state, with all properties at their first value).
     */
    public BlockState any() {
        return defaultState;
    }

    /**
     * Returns all pre-created state instances (unmodifiable).
     */
    public List<BlockState> getAllStates() {
        return Collections.unmodifiableList(Arrays.asList(allStates));
    }

    /**
     * Returns the total state count.
     */
    public int getStateCount() {
        return allStates.length;
    }

    /**
     * Checks whether there is any defined property.
     */
    public boolean hasProperties() {
        return properties.length > 0;
    }

    /**
     * Checks whether there is only one state (no properties or a single value).
     */
    public boolean isSingletonState() {
        return allStates.length == 1;
    }

    /**
     * Returns the property count.
     */
    public int getPropertyCount() {
        return properties.length;
    }

    /**
     * Finds the corresponding CatBlockState by a combination of property values.
     *
     * @param values the values corresponding to the property array order
     * @return the matching CatBlockState
     * @throws IllegalArgumentException if no matching state is found
     */
    @SuppressWarnings("unchecked")
    public BlockState findState(Comparable<?>... values) {
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

    // ==================== meta encode/decode / resident table ====================

    /**
     * Returns whether the given property is a dynamic property (computed at runtime from
     * the world, not involved in meta decoding).
     */
    public boolean isDynamic(Property<?> prop) {
        Integer idx = propertyIndex.get(prop);
        return idx != null && dynamicFlags != null && dynamicFlags[idx];
    }

    /**
     * Returns the array of static properties involved in meta decoding (declaration order).
     */
    public Property<?>[] getStaticProperties() {
        return staticProperties;
    }

    /**
     * Returns the meta codec; {@code null} when unset (uses the natural cartesian-product
     * encoding of the static properties).
     * <p>
     * Used by {@link BlockStateInheritance} to extract a fragment codec: when a subclass
     * inherits a superclass state definition, the codecs of the fragments must be spliced
     * and combined by bit field.
     *
     * @return the meta codec, or {@code null}
     */
    @Nullable
    public MetaCodec getMetaCodec() {
        return metaCodec;
    }

    /**
     * Looks up the corresponding resident CatBlockState by metadata.
     * <p>Dynamic properties take their default values (first value) and can be jumped to at
     * runtime in O(1) via {@link BlockState#setValue}.
     *
     * @param meta the block metadata (0-15)
     * @return the resident state corresponding to that meta
     */
    public BlockState getStateFromMeta(int meta) {
        if (resolvedByMeta != null) {
            return resolvedByMeta[meta & 15];
        }
        return computeStateFromMeta(meta);
    }

    private BlockState computeStateFromMeta(int meta) {
        Comparable<?>[] staticValues;
        if (metaCodec != null) {
            // Legacy positional codec (kept until the vanilla definitions are restated with
            // per-property meta mapping); takes precedence over per-property decoding.
            staticValues = metaCodec.decode(meta);
        } else if (usesMetaProperties) {
            // Distributed byte mapping: each static property decodes its own bit field.
            staticValues = decodePerProperty(meta);
        } else {
            staticValues = defaultDecode(meta);
        }

        // Assemble the full property value array: static properties take decoded values, dynamic properties take default values
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
     * Per-property meta decoding: each static {@link MetaBlockProperty} extracts its own value
     * from the raw meta, touching only its bit field. This replaces the whole-definition
     * positional {@code MetaCodec} with a distributed byte mapping that a mod developer can
     * express per property.
     */
    private Comparable<?>[] decodePerProperty(int meta) {
        Property<?>[] statics = staticProperties;
        Comparable<?>[] result = new Comparable<?>[statics.length];
        for (int i = 0; i < statics.length; i++) {
            result[i] = ((MetaBlockProperty<?>) statics[i]).decode(meta);
        }
        return result;
    }

    /**
     * Default meta decoding: treats meta as the natural cartesian-product index of the
     * static properties (the last property changes fastest).
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
     * Builder for BlockStateDefinition. Use
     * <pre>{@code
     * BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<>(myBlock)
     *     .add(TYPE, WATERLOGGED)
     *     .create();
     * }</pre>
     *
     * @param <O> the owner type
     */
    public static class Builder<O> {

        private final O owner;
        private final List<Property<?>> props = new ArrayList<>();
        private final Set<String> usedNames = new HashSet<>();
        private final Set<Property<?>> dynamicProps = new HashSet<>();
        private MetaCodec metaCodec;

        /**
         * @param owner the owner of the state definition (e.g. a Block instance)
         */
        public Builder(O owner) {
            if (owner == null) {
                throw new IllegalArgumentException("Owner must not be null");
            }
            this.owner = owner;
        }

        /**
         * Adds one or more properties. Property names must not be duplicated.
         *
         * @param properties the properties to add
         * @return this Builder instance (chainable)
         * @throws IllegalArgumentException if a property name is duplicated
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
         * Marks one or more properties as "dynamic properties" (computed at runtime from the
         * world, not involved in meta decoding).
         * <p>Dynamic properties must still be added first via {@link #add(Property[])} so that
         * they participate in the cartesian product and jump table. Typical use cases: a
         * stair's {@code shape}, a pane's {@code north/east/south/west}.
         *
         * @param properties the properties to mark as dynamic
         * @return this Builder instance (chainable)
         */
        public Builder<O> dynamic(Property<?>... properties) {
            Collections.addAll(dynamicProps, properties);
            return this;
        }

        /**
         * Sets the decoder for non-regular meta encoding (e.g. a log's wood+axis, a slab's
         * half+variant). When unset, defaults to the natural cartesian-product encoding of
         * the static properties.
         *
         * @param codec the meta decoder
         * @return this Builder instance (chainable)
         */
        public Builder<O> metaCodec(MetaCodec codec) {
            this.metaCodec = codec;
            return this;
        }

        /**
         * Builds the BlockStateDefinition. Computes the cartesian product of all state
         * combinations and builds the neighbor jump table.
         *
         * @return a new BlockStateDefinition instance
         */
        public BlockStateDefinition<O> create() {
            int propCount = props.size();
            Property<?>[] properties = props.toArray(new Property<?>[0]);

            // Compute the property index mapping
            Map<Property<?>, Integer> propIndexMap = new HashMap<>();
            for (int i = 0; i < propCount; i++) {
                propIndexMap.put(properties[i], i);
            }

            // Compute the value count of each property
            int[] valueCounts = new int[propCount];
            for (int i = 0; i < propCount; i++) {
                valueCounts[i] = properties[i].getValueCount();
            }

            // Total state count = product of each property's value count
            int totalStates = 1;
            for (int c : valueCounts) {
                totalStates *= c;
            }

            // Create all states
            BlockState[] states = new BlockState[Math.max(totalStates, 1)];
            Comparable<?>[][] allValues = new Comparable<?>[states.length][propCount];

            if (propCount == 0) {
                // No properties: singleton
                states[0] = new BlockState(null, new Comparable<?>[0]);
            } else {
                // Use counters to generate all value combinations (the last property changes fastest)
                int[] counters = new int[propCount];
                for (int stateIdx = 0; stateIdx < totalStates; stateIdx++) {
                    for (int p = 0; p < propCount; p++) {
                        allValues[stateIdx][p] = properties[p].getValues().get(counters[p]);
                    }
                    states[stateIdx] = new BlockState(null, allValues[stateIdx]);

                    // Increment the counters
                    for (int p = propCount - 1; p >= 0; p--) {
                        counters[p]++;
                        if (counters[p] < valueCounts[p]) break;
                        counters[p] = 0;
                    }
                }
            }

            // Compute strides (for O(1) neighbor location)
            int[] strides = new int[propCount];
            if (propCount > 0) {
                strides[propCount - 1] = 1;
                for (int i = propCount - 2; i >= 0; i--) {
                    strides[i] = strides[i + 1] * valueCounts[i + 1];
                }
            }

            // Build the neighbor jump table
            BlockStateDefinition<O> definition = new BlockStateDefinition<>(owner, properties, states, propIndexMap, states[0]);

            // Backfill meta encode/decode related fields
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

            // Detect per-property byte mapping: enabled only when there is at least one static
            // property and every static property carries its own decode(int). A positional
            // metaCodec, when present, still takes precedence at decode time.
            boolean allMeta = !staticList.isEmpty();
            for (Property<?> p : staticList) {
                if (!(p instanceof MetaBlockProperty)) {
                    allMeta = false;
                    break;
                }
            }
            definition.usesMetaProperties = allMeta;

            // [C2 fix] Backfill the definition into all CatBlockState instances
            // (definition is null when the CatBlockState is created, because the definition is not yet built)
            for (BlockState s : states) {
                s.definition = definition;
            }

            for (int stateIdx = 0; stateIdx < states.length; stateIdx++) {
                BlockState state = states[stateIdx];
                state.neighbors = new BlockState[propCount][];

                for (int p = 0; p < propCount; p++) {
                    int valCount = valueCounts[p];
                    state.neighbors[p] = new BlockState[valCount];

                    // Decode the counter value of the current state on this property
                    int currentCounter = 0;
                    int remaining = stateIdx;
                    for (int i = propCount - 1; i >= 0; i--) {
                        int valAtI = remaining % valueCounts[i];
                        remaining /= valueCounts[i];
                        if (i == p) currentCounter = valAtI;
                    }

                    for (int v = 0; v < valCount; v++) {
                        // O(1) computation of the target state index
                        int delta = (v - currentCounter) * strides[p];
                        state.neighbors[p][v] = states[stateIdx + delta];
                    }
                }
            }

            // Prefill the resident table resolvedByMeta[0..15]
            definition.resolvedByMeta = new BlockState[16];
            for (int meta = 0; meta < 16; meta++) {
                definition.resolvedByMeta[meta] = definition.computeStateFromMeta(meta);
            }

            return definition;
        }
    }
}
