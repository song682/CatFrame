package decok.dfcdvadstf.catframe.model.state.block;

import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.state.BlockStateDefinition;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel.CollectedPart;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.property.BooleanProperty;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Verification for the state-index (joinCode) part memo in {@link ResidentStateModel} and its
 * build-time weighted-array exclusion.
 * <p>
 * The collect paths here use a blockstate with neither variants nor multipart, so resolution
 * terminates in the empty-part branch without triggering the bake cache — the memo semantics
 * (same code → same instance, generation change → rebuild, weighted → excluded) stay fully
 * observable. Runs without booting the game provided the 1.7.10 Block registry statics
 * initialize standalone.
 */
public class ResidentStateModelMemoTest {

    /** Two static boolean properties; meta decodes them as a cartesian product (east fastest). */
    private static BlockStateDefinition<String> twoStaticBools() {
        return new BlockStateDefinition.Builder<>("memo-test")
                .add(BooleanProperty.create("north"), BooleanProperty.create("east"))
                .create();
    }

    /** Empty blockstate (no variants / no multipart) → collect resolves to the empty part. */
    private static BlockstateJson emptyTarget() {
        return new BlockstateJson();
    }

    private static ResidentStateModel model(BlockstateJson bs, BlockStateDefinition<?> def) {
        return ResidentStateModel.builder(null).blockstate(bs).stateDefinition(def).build();
    }

    @Test
    public void sameCodeReturnsIdenticalPartInstance() {
        ResidentStateModel m = model(emptyTarget(), twoStaticBools());

        CollectedPart first = m.collectPartsWithProps(null, 10, 64, 10, 1);
        CollectedPart second = m.collectPartsWithProps(null, 20, 64, 30, 1);
        assertSame("same state code must reuse the memoized part instance",
                first.part, second.part);
        // props still flow out per position (read-only tint view stays populated)
        assertFalse("memo hit must still expose the resolved properties", first.blockstateProps.isEmpty());
    }

    @Test
    public void differentCodesUseSeparateMemoEntries() {
        ResidentStateModel m = model(emptyTarget(), twoStaticBools());

        CollectedPart meta1 = m.collectPartsWithProps(null, 0, 0, 0, 1); // north=true,  east=false
        CollectedPart meta2 = m.collectPartsWithProps(null, 0, 0, 0, 2); // north=false, east=true
        assertNotSame("different state codes must not share an entry", meta1.part, meta2.part);

        // Each code keeps its own stable instance on repetition
        assertSame(meta1.part, m.collectPartsWithProps(null, 5, 5, 5, 1).part);
        assertSame(meta2.part, m.collectPartsWithProps(null, 6, 6, 6, 2).part);
    }

    @Test
    public void weightedArrayTargetsAreExcludedFromMemo() throws Exception {
        Method compute = ResidentStateModel.class
                .getDeclaredMethod("computeMemoEligible", BlockstateJson.class);
        compute.setAccessible(true);

        // Single-entry weight-1 array: selection is deterministic → eligible
        BlockstateJson single = new BlockstateJson();
        single.variants = new HashMap<>();
        single.variants.put("normal", entryOf(weights(1)));
        assertTrue((Boolean) compute.invoke(null, single));

        // Weighted array (sum > 1): position-seeded roll must keep re-running → excluded
        BlockstateJson weighted = new BlockstateJson();
        weighted.variants = new HashMap<>();
        weighted.variants.put("normal", entryOf(weights(1, 1)));
        assertFalse((Boolean) compute.invoke(null, weighted));

        // Weighted array under multipart apply is excluded as well
        BlockstateJson mp = new BlockstateJson();
        mp.multipart = new ArrayList<>();
        BlockstateJson.MultipartCase mpc = new BlockstateJson.MultipartCase();
        mpc.apply = entryOf(weights(2, 1));
        mp.multipart.add(mpc);
        assertFalse((Boolean) compute.invoke(null, mp));
    }

    @Test
    public void emptyAndSingleWeightTargetsStillMemoize() {
        ResidentStateModel m = model(emptyTarget(), twoStaticBools());
        BlockStateModelPart first = m.collectPartsWithProps(null, 0, 0, 0, 0).part;
        assertTrue("empty-branch part is a valid memo value", first.isEmpty());
        assertSame(first, m.collectPartsWithProps(null, 1, 2, 3, 0).part);
    }

    /** B3 contract: a bake-cache generation bump (reload / re-stitch) drops stale memo parts. */
    @Test
    public void generationBumpRebuildsMemo() {
        ResidentStateModel m = model(emptyTarget(), twoStaticBools());
        BlockStateModelPart before = m.collectPartsWithProps(null, 0, 0, 0, 1).part;
        assertSame(before, m.collectPartsWithProps(null, 0, 0, 0, 1).part);

        BakedModelCache.INSTANCE.clear(); // simulate resource reload / second stitch cycle

        BlockStateModelPart after = m.collectPartsWithProps(null, 0, 0, 0, 1).part;
        assertNotSame("generation change must drop stale memo parts", before, after);
        assertSame(after, m.collectPartsWithProps(null, 9, 9, 9, 1).part);
    }

    private static BlockstateJson.VariantEntry entryOf(List<BlockstateJson.Variant> list) {
        BlockstateJson.VariantEntry entry = new BlockstateJson.VariantEntry();
        // stored as list to exercise isArray()/weight semantics on the eligibility scan
        entry.list = list;
        return entry;
    }

    private static List<BlockstateJson.Variant> weights(int... ws) {
        List<BlockstateJson.Variant> list = new ArrayList<>();
        for (int w : ws) {
            BlockstateJson.Variant v = new BlockstateJson.Variant();
            v.model = "test/model";
            v.weight = w;
            list.add(v);
        }
        return Arrays.asList(list.toArray(new BlockstateJson.Variant[0]));
    }
}
