package decok.dfcdvadstf.catframe.model.state;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verification for the {@link BlockstateJson.MultipartWhen} precomputed pipe-split tables:
 * deserialization must fill the allowed-value arrays (so the render-time match loop never
 * re-splits), and a programmatically built instance without precomputation must keep matching
 * identically through the original split fallback.
 * <p>
 * Pure Gson-layer class: runs without booting the game.
 */
public class MultipartWhenMatchTest {

    private static final String JSON = "{\"multipart\":["
            + "{\"when\":{\"north\":\"true|false\"},\"apply\":{\"model\":\"m_post\"}},"
            + "{\"when\":{\"OR\":[{\"east\":\"true\"},{\"west\":\"true\",\"south\":\"true\"}]},"
            + "\"apply\":{\"model\":\"m_side\"}}"
            + "]}";

    private static BlockstateJson parse(String json) {
        return BlockstateJson.createGson().fromJson(json, BlockstateJson.class);
    }

    private static Map<String, String> props(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    public void deserializationPrecomputesSplitTables() {
        BlockstateJson bs = parse(JSON);
        BlockstateJson.MultipartWhen simple = bs.multipart.get(0).when;
        assertNotNull("conditions table must be precomputed", simple.allowedByProp);
        assertArrayEquals(new String[]{"true", "false"}, simple.allowedByProp.get("north"));

        BlockstateJson.MultipartWhen orWhen = bs.multipart.get(1).when;
        assertNotNull("OR groups must be precomputed", orWhen.allowedByOrGroup);
        assertEquals(2, orWhen.allowedByOrGroup.size());
        assertArrayEquals(new String[]{"true"}, orWhen.allowedByOrGroup.get(0).get("east"));
    }

    @Test
    public void precomputedAndFallbackPathsMatchIdentically() {
        BlockstateJson bs = parse(JSON);
        // Programmatic twin of case 0: conditions set, but precomputeAllowed() never runs →
        // matchesAll must fall back to the original split path.
        BlockstateJson.MultipartWhen fallback = new BlockstateJson.MultipartWhen();
        fallback.conditions = new HashMap<>();
        fallback.conditions.put("north", "true|false");
        assertNull(fallback.allowedByProp);

        // OR twin of case 1.
        BlockstateJson.MultipartWhen orFallback = new BlockstateJson.MultipartWhen();
        orFallback.or = new ArrayList<>();
        Map<String, String> g0 = new HashMap<>();
        g0.put("east", "true");
        Map<String, String> g1 = new HashMap<>();
        g1.put("west", "true");
        g1.put("south", "true");
        orFallback.or.add(g0);
        orFallback.or.add(g1);
        assertNull(orFallback.allowedByOrGroup);

        List<Map<String, String>> probes = Arrays.asList(
                props("north", "true"),
                props("north", "false"),
                props("north", "yes"),
                props(), // missing key -> no match (actual.get == null path)
                props("east", "true"),
                props("west", "true", "south", "true"),
                props("west", "true", "south", "false"),
                props("west", "true"),
                props("east", "false"));

        for (Map<String, String> p : probes) {
            assertEquals("pipe semantics must not change: " + p,
                    bs.multipart.get(0).when.matches(p), fallback.matches(p));
            assertEquals("OR semantics must not change: " + p,
                    bs.multipart.get(1).when.matches(p), orFallback.matches(p));
        }

        // Sanity: representative expectations (regression guard beyond the equivalence itself)
        assertTrue(bs.multipart.get(0).when.matches(props("north", "true")));
        assertTrue(bs.multipart.get(1).when.matches(props("west", "true", "south", "true")));
    }

    @Test
    public void explicitPrecomputeEnablesTablePath() {
        BlockstateJson.MultipartWhen when = new BlockstateJson.MultipartWhen();
        when.conditions = new HashMap<>();
        when.conditions.put("facing", "north|east");
        when.precomputeAllowed();
        assertNotNull(when.allowedByProp);
        assertArrayEquals(new String[]{"north", "east"}, when.allowedByProp.get("facing"));
        assertTrue(when.matches(props("facing", "east")));
        assertTrue(!when.matches(props("facing", "south")));
    }
}
