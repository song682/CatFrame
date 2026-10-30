package decok.dfcdvadstf.catframe.model.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Verifies {@link ModelResolver#canonicalKey(String)} — the single key space shared by the resolved
 * model cache, the parent-chain cycle detector and the dependency graph.
 * <p>
 * The shipped assets mix both spellings of one model: {@code "parent": "item/generated"} in
 * {@code assets/catframe/models/item/bluey_inventory.json} against
 * {@code "parent": "minecraft:item/generated"} in {@code bingo_inventory.json}. Without
 * canonicalization the same file is parsed and cached twice.
 * <p>
 * The {@code builtin/} cases are the load-bearing ones. {@code builtin/generated} is referenced bare
 * throughout {@code assets/minecraft/models}, and {@code AtlasGuard} calls
 * {@code resolve("builtin/missing")}; prefixing either would defeat the
 * {@code startsWith("builtin/")} branch in {@code ModelResolver.resolveInternalChecked} and drop
 * every affected model to missingno.
 */
public class ModelResolverCanonicalKeyTest {

    @Test
    public void barePathDefaultsToMinecraftNamespace() {
        assertEquals("minecraft:block/cube", ModelResolver.canonicalKey("block/cube"));
        assertEquals("minecraft:item/generated", ModelResolver.canonicalKey("item/generated"));
    }

    @Test
    public void qualifiedMinecraftPathIsUnchanged() {
        assertEquals("minecraft:block/cube", ModelResolver.canonicalKey("minecraft:block/cube"));
        assertEquals("minecraft:item/generated", ModelResolver.canonicalKey("minecraft:item/generated"));
    }

    @Test
    public void thirdPartyNamespaceIsNeverRewritten() {
        // Every reference to the catframe-namespace models is already fully qualified in
        // assets/catframe/items/*.json; rewriting one would point at a nonexistent file.
        assertEquals("catframe:item/bluey", ModelResolver.canonicalKey("catframe:item/bluey"));
        assertEquals("catframe:item/bingo_inventory",
                ModelResolver.canonicalKey("catframe:item/bingo_inventory"));
    }

    @Test
    public void builtinPathsAreVirtualAndStayBare() {
        assertEquals("builtin/generated", ModelResolver.canonicalKey("builtin/generated"));
        assertEquals("builtin/missing", ModelResolver.canonicalKey("builtin/missing"));
    }

    @Test
    public void nullAndEmptyPassThrough() {
        assertNull(ModelResolver.canonicalKey(null));
        assertEquals("", ModelResolver.canonicalKey(""));
    }

    @Test
    public void canonicalizationIsIdempotent() {
        String[] samples = {
                "block/cube_all",
                "minecraft:block/cube_all",
                "catframe:item/bluey",
                "builtin/generated",
                "builtin/missing"
        };
        for (String sample : samples) {
            String once = ModelResolver.canonicalKey(sample);
            assertEquals("canonicalKey must be stable when applied twice: " + sample,
                    once, ModelResolver.canonicalKey(once));
        }
    }

    @Test
    public void bothSpellingsOfOneModelCollapseToOneKey() {
        // The concrete regression this guards: bluey_inventory.json (bare) and bingo_inventory.json
        // (qualified) name the same parent and must land on the same cache key.
        assertEquals(ModelResolver.canonicalKey("minecraft:item/generated"),
                ModelResolver.canonicalKey("item/generated"));
    }
}
