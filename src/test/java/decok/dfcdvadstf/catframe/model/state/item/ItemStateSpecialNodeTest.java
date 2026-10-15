package decok.dfcdvadstf.catframe.model.state.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Runtime verification for the {@link ItemStateNode.SpecialNode} extension point:
 * a custom namespaced node type registered through {@link ItemStateNode#registerNodeType}
 * must (1) materialize during items/ decision-tree deserialization and (2) contribute
 * dynamic layers at evaluation time while (3) preserving composite layer order.
 * <p>
 * This guards the DMod bundle acceptance contract: front plate + dynamic content + back
 * plate stay in the composite declaration order (the render-order contract).
 * <p>
 * No {@code ItemStack} instance is created (a null-stack context is passed) so the test
 * runs without booting the game.
 */
public class ItemStateSpecialNodeTest {

    private static final String TYPE_ID = "test:recording_special";

    /** Concrete special node that emits a fixed dynamic marker layer when a context is present. */
    private static class RecordingSpecialNode extends ItemStateNode.SpecialNode {
        @Override
        public EvalResult evaluate(Map<String, Comparable<?>> properties, EvalContext ctx) {
            if (ctx == null) return EvalResult.empty();
            return EvalResult.compositeLayers(
                    Collections.singletonList(EvalResult.Layer.ofModel("dyn/marker")));
        }
    }

    private static ItemStateNode parseTree(String modelJson) {
        JsonObject root = new JsonParser().parse("{\"model\": " + modelJson + "}").getAsJsonObject();
        return ItemStateNode.parseRoot(root);
    }

    @Test
    public void customSpecialNodeTypeMaterializesAndEvaluates() {
        ItemStateNode.registerNodeType(TYPE_ID, (obj, ctx) -> new RecordingSpecialNode());

        ItemStateNode node = parseTree("{ \"type\": \"" + TYPE_ID + "\" }");
        assertTrue("registered custom type must materialize as the SpecialNode subclass",
                node instanceof RecordingSpecialNode);
        assertTrue(node instanceof ItemStateNode.SpecialNode);

        Map<String, Comparable<?>> props = new HashMap<>();

        // No context (e.g. texture collection): base SpecialNode contributes nothing.
        assertTrue(node.evaluate(props).isEmpty());

        // With context: dynamic layer materializes.
        EvalResult withCtx = node.evaluate(props, new EvalContext(null, null));
        assertFalse(withCtx.isEmpty());
        assertEquals(1, withCtx.getLayers().size());
        assertEquals("dyn/marker", withCtx.getLayers().get(0).modelPath);

        // Special node declares no static model path for texture collection.
        Set<String> paths = new HashSet<>();
        node.collectModelPaths(paths);
        assertTrue(paths.isEmpty());
    }

    @Test
    public void compositePreservesDynamicLayerOrder() {
        ItemStateNode.registerNodeType(TYPE_ID, (obj, ctx) -> new RecordingSpecialNode());

        // front plate + dynamic content + back plate
        ItemStateNode tree = parseTree(
                "{ \"type\": \"minecraft:composite\", \"models\": ["
                        + "  { \"type\": \"minecraft:model\", \"model\": \"front\" },"
                        + "  { \"type\": \"" + TYPE_ID + "\" },"
                        + "  { \"type\": \"minecraft:model\", \"model\": \"back\" }"
                        + "]}");

        EvalResult result = tree.evaluate(new HashMap<>(), new EvalContext(null, null));
        List<EvalResult.Layer> layers = result.getLayers();
        assertEquals(3, layers.size());
        assertEquals("front", layers.get(0).modelPath);
        assertEquals("dyn/marker", layers.get(1).modelPath);
        assertEquals("back", layers.get(2).modelPath);
        assertFalse(layers.get(1).isDynamic()); // this test's special node emits a static marker layer

        // getModels() keeps its original meaning: static paths only, in order.
        List<String> models = result.getModels();
        assertEquals(3, models.size());
        assertEquals("front", models.get(0));
        assertEquals("dyn/marker", models.get(1));
        assertEquals("back", models.get(2));
    }

    @Test
    public void unknownNamespacedTypeStillThrows() {
        boolean threw = false;
        try {
            parseTree("{ \"type\": \"test:not_registered_anywhere\" }");
        } catch (Exception e) {
            threw = true;
        }
        assertTrue("unregistered namespaced type must not silently materialize", threw);
    }
}
