package decok.dfcdvadstf.catframe.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verification for the {@link BakedModelCache} generation counter: every clear (resource
 * reload / re-stitch cycle) must bump it so downstream memo caches (ResidentStateModel's
 * state-index part memo) can detect stale bake results.
 * <p>
 * Only touches cache bookkeeping (no bake calls): runs without booting the game.
 */
public class BakedModelCacheGenerationTest {

    @Test
    public void clearBumpsGeneration() {
        long before = BakedModelCache.INSTANCE.getGeneration();
        BakedModelCache.INSTANCE.clear();
        assertEquals(before + 1, BakedModelCache.INSTANCE.getGeneration());

        BakedModelCache.INSTANCE.clear(null);
        assertEquals(before + 2, BakedModelCache.INSTANCE.getGeneration());
        assertTrue("generation is monotonic", BakedModelCache.INSTANCE.getGeneration() > before);
    }
}
