package decok.dfcdvadstf.catframe.model.state.block;

import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.state.BlockStateDefinition;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel.CollectedPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.property.BooleanProperty;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import decok.dfcdvadstf.catframe.model.state.property.StateDefinitions;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Verification for the canonical-state (typed) memo lane of {@link ResidentStateModel}:
 * resolvers fold their string output into the interned {@code BlockState} instance through the
 * {@code resolveState} default conversion, and the memo keys on that reference (identity
 * equality). Also covers the domain checks that downgrade a resolver to the legacy string-code
 * lane, and the bake-cache generation clear for the new table.
 * <p>
 * Like {@link ResidentStateModelMemoTest}, the blockstate carries neither variants nor
 * multipart, so every collect terminates in the empty-part branch without touching the bake
 * cache — the memo plumbing stays fully observable. The empty part is a fresh instance per
 * build, so same-instance assertions genuinely prove a memo hit.
 */
public class ResidentStateModelTypedMemoTest {

    private static final Property<Boolean> NORTH = BooleanProperty.create("north");
    private static final Property<Boolean> EAST = BooleanProperty.create("east");
    private static final Property<String> SHAPE =
            StateDefinitions.stringProp("shape", "straight", "outer_left");

    /** north/east decode from meta; shape is the dynamic property the resolver writes. */
    private static BlockStateDefinition<String> shapeDef() {
        return new BlockStateDefinition.Builder<>("typed-test")
                .add(NORTH, EAST, SHAPE)
                .dynamic(SHAPE)
                .create();
    }

    private static BlockstateJson emptyTarget() {
        return new BlockstateJson();
    }

    private static ResidentStateModel model(ResidentStateModel.DynamicPropertyResolver dynamic) {
        return ResidentStateModel.builder(null)
                .blockstate(emptyTarget())
                .stateDefinition(shapeDef())
                .dynamic(dynamic)
                .build();
    }

    private static Map<?,?> memoField(ResidentStateModel m, String name) throws ReflectiveOperationException {
        Field f = ResidentStateModel.class.getDeclaredField(name);
        f.setAccessible(true);
        return (Map<?,?>) f.get(m);
    }

    /** A SAM lambda resolver (source compatibility) must land in the typed lane automatically. */
    @Test
    public void lambdaResolverRunsThroughTypedLaneWithSharedPropsSnapshot() throws Exception {
        ResidentStateModel m = model((world, x, y, z, meta, props) ->
                props.put("shape", (x & 1) == 0 ? "straight" : "outer_left"));

        CollectedPart first = m.collectPartsWithProps(null, 0, 64, 0, 1);
        CollectedPart sameCombo = m.collectPartsWithProps(null, 2, 64, 4, 1);
        CollectedPart otherCombo = m.collectPartsWithProps(null, 1, 64, 0, 1);

        assertSame("same canonical state across positions must reuse the memoized part",
                first.part, sameCombo.part);
        assertSame("typed-lane hit returns the shared props snapshot (no re-build)",
                first.blockstateProps, sameCombo.blockstateProps);
        assertNotSame("different shape must key a separate entry", first.part, otherCombo.part);
        assertFalse("stateMemo must carry the entries", memoField(m, "stateMemo").isEmpty());
    }

    /** Resolver overrides of static (meta-decoded) properties converge onto one canonical. */
    @Test
    public void resolverOverrideOfStaticPropertyConvergesAcrossMeta() throws Exception {
        ResidentStateModel m = model((world, x, y, z, meta, props) -> {
            props.put("north", "true");
            props.put("east", "true");
        });

        // meta 1 decodes (north=false, east=true), meta 2 decodes (north=true, east=false);
        // both overrides produce the same canonical (true, true) combination.
        CollectedPart fromMeta1 = m.collectPartsWithProps(null, 0, 64, 0, 1);
        CollectedPart fromMeta2 = m.collectPartsWithProps(null, 0, 64, 0, 2);
        assertSame("distinct metas folding to one canonical state must share the memo entry",
                fromMeta1.part, fromMeta2.part);

        // And the canonical really carries the overridden values:
        Map<?,?> stateMemo = memoFieldOf(m);
        Object bucket = stateMemo.values().iterator().next();
        assertEquals("one merged canonical key expected", 1, ((Map<?, ?>) bucket).size());
    }

    /** An out-of-domain value drops the whole fold to the legacy lane (which then bypasses memo). */
    @Test
    public void outOfDomainValueDowngradesToLegacyLane() throws Exception {
        ResidentStateModel m = model((world, x, y, z, meta, props) ->
                props.put("shape", "bogus_value"));

        CollectedPart first = m.collectPartsWithProps(null, 0, 64, 0, 0);
        CollectedPart second = m.collectPartsWithProps(null, 0, 64, 0, 0);

        assertNotSame("legacy lane: stateIndexOf returns -1, nothing is memoized",
                first.part, second.part);
        assertTrue("typed lane must stay empty after a downgrade", memoField(m, "stateMemo").isEmpty());
        assertTrue("legacy code memo must stay empty for a -1 code", memoField(m, "partMemo").isEmpty());
    }

    /** An unknown property name also downgrades (typed lane is stricter than the string lane). */
    @Test
    public void unknownPropertyNameDowngradesButLegacyStillMemoizes() throws Exception {
        ResidentStateModel m = model((world, x, y, z, meta, props) -> {
            props.put("ghost", "straight");
            props.put("shape", "outer_left");
        });

        CollectedPart first = m.collectPartsWithProps(null, 0, 64, 0, 0);
        CollectedPart second = m.collectPartsWithProps(null, 5, 64, 7, 0);

        assertTrue("unknown name must keep the entry out of the typed lane",
                memoField(m, "stateMemo").isEmpty());
        assertSame("legacy lane still memoizes by the full-property code",
                first.part, second.part);
    }

    /** B3 contract extended: a generation bump must clear the canonical-state memo too. */
    @Test
    public void generationBumpClearsStateMemo() {
        ResidentStateModel m = model((world, x, y, z, meta, props) -> props.put("shape", "straight"));

        CollectedPart before = m.collectPartsWithProps(null, 0, 64, 0, 0);
        assertSame(before.part, m.collectPartsWithProps(null, 3, 64, 3, 0).part);

        BakedModelCache.INSTANCE.clear(); // simulate resource reload / second stitch cycle

        CollectedPart after = m.collectPartsWithProps(null, 0, 64, 0, 0);
        assertNotSame("generation change must drop memoized canonical entries",
                before.part, after.part);
        assertSame(after.part, m.collectPartsWithProps(null, 8, 64, 8, 0).part);
    }

    private static Map<?,?> memoFieldOf(ResidentStateModel m) {
        try {
            return memoField(m, "stateMemo");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
