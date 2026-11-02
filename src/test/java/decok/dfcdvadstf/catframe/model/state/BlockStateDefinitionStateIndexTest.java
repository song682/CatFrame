package decok.dfcdvadstf.catframe.model.state;

import decok.dfcdvadstf.catframe.model.state.property.BooleanProperty;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verification for {@link BlockStateDefinition#stateIndexOf(Map)} — the discrete "joinCode"
 * encoder behind ResidentStateModel's static-layer part memo.
 * <p>
 * Guards three contracts:
 * <ol>
 *   <li>the code space is exactly the cartesian-product state table (injective, in range,
 *       aligned with {@code findStateIndex} numbering — cross-checked against
 *       {@link BlockState#matches(Map)} per combination);</li>
 *   <li>missing keys / out-of-domain values return -1 (callers bypass the memo, never alias);</li>
 *   <li>ALL properties participate in the code, including static ones a resolver may override
 *       (double-plant variant/half scenario).</li>
 * </ol>
 * Pure state-layer classes: runs without booting the game.
 */
public class BlockStateDefinitionStateIndexTest {

    private static final List<String> PANE_PROPS =
            Arrays.asList("north", "east", "south", "west");

    private static BlockStateDefinition<String> paneDef() {
        Property<?> north = BooleanProperty.create("north");
        Property<?> east = BooleanProperty.create("east");
        Property<?> south = BooleanProperty.create("south");
        Property<?> west = BooleanProperty.create("west");
        return new BlockStateDefinition.Builder<>("pane")
                .add(north, east, south, west)
                .dynamic(north, east, south, west)
                .create();
    }

    private static Map<String, String> props(int mask) {
        Map<String, String> props = new HashMap<>();
        for (int i = 0; i < PANE_PROPS.size(); i++) {
            props.put(PANE_PROPS.get(i), ((mask >> i) & 1) == 1 ? "true" : "false");
        }
        return props;
    }

    @Test
    public void paneSixteenCodesAreDistinctInjunctiveAndAlignedWithStateTable() {
        BlockStateDefinition<String> def = paneDef();
        assertEquals(16, def.getStateCount());

        Set<Integer> codes = new HashSet<>();
        for (int mask = 0; mask < 16; mask++) {
            Map<String, String> p = props(mask);
            int code = def.stateIndexOf(p);
            assertTrue("in-domain combination must encode (mask=" + mask + ")", code >= 0);
            assertTrue("code within table", code < def.getStateCount());
            // Cross-check against the findStateIndex numbering: the state at this index
            // must match the full property table — the code is a genuine palette index.
            assertTrue("code must select the matching state", def.getAllStates().get(code).matches(p));
            assertTrue("encoding must be injective", codes.add(code));
        }
        assertEquals("all 16 pane combinations covered", 16, codes.size());
    }

    @Test
    public void outOfDomainValueReturnsMinusOne() {
        BlockStateDefinition<String> def = paneDef();
        Map<String, String> p = props(0);
        p.put("north", "yes");
        assertEquals(-1, def.stateIndexOf(p));
    }

    @Test
    public void missingKeyReturnsMinusOne() {
        BlockStateDefinition<String> def = paneDef();
        Map<String, String> p = props(5);
        p.remove("west");
        assertEquals(-1, def.stateIndexOf(p));
    }

    @Test
    public void nullMapReturnsMinusOneWhenPropertiesExist() {
        assertEquals(-1, paneDef().stateIndexOf(null));
    }

    /**
     * double_plant guard: a resolver overriding a STATIC property must change the code —
     * only-dynamic-subset encoding would alias different models onto one cache entry.
     */
    @Test
    public void staticPropertiesParticipateInTheCode() {
        Property<String> variant =
                Property.create("variant", String.class, Arrays.asList("sunflower", "tall_grass"));
        Property<String> half =
                Property.create("half", String.class, Arrays.asList("lower", "upper"));
        BlockStateDefinition<String> def = new BlockStateDefinition.Builder<>("double_plant")
                .add(variant, half)
                .dynamic(half) // variant stays static, yet the resolver overrides it at runtime
                .create();

        int a = def.stateIndexOf(mapOf("variant", "sunflower", "half", "lower"));
        int b = def.stateIndexOf(mapOf("variant", "sunflower", "half", "upper"));
        int c = def.stateIndexOf(mapOf("variant", "tall_grass", "half", "lower"));
        assertTrue(a >= 0 && b >= 0 && c >= 0);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(b, c);
    }

    @Test
    public void singletonDefinitionEncodesToZero() {
        BlockStateDefinition<String> def = new BlockStateDefinition.Builder<>("stone").create();
        assertTrue(def.isSingletonState());
        assertEquals(0, def.stateIndexOf(new HashMap<>()));
    }

    private static Map<String, String> mapOf(String k1, String v1, String k2, String v2) {
        Map<String, String> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }
}
