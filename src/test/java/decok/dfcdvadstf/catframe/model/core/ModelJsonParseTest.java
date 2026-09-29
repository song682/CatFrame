package decok.dfcdvadstf.catframe.model.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Runtime verification for the custom ModelJson deserializer
 * (no reflection-based field mapping).
 * <p>
 * Guards against generic-info loss regressions: elements must deserialize
 * into {@link ModelJson.Element}, display values into
 * {@link ModelJson.DisplayTransform}, and textures into a
 * {@code Map<String, String>} -- never into raw {@code LinkedTreeMap}s,
 * which used to cause {@code ClassCastException} at bake time.
 */
public class ModelJsonParseTest {

    private static final String SAMPLE_JSON = "{"
            + "  \"parent\": \"block/cube_all\","
            + "  \"textures\": { \"all\": \"blocks/stone\" },"
            + "  \"display\": {"
            + "    \"gui\": { \"rotation\": [30, 225, 0], \"translation\": [0, 0, 0], \"scale\": [0.625, 0.625, 0.625] }"
            + "  },"
            + "  \"elements\": ["
            + "    {"
            + "      \"from\": [0, 0, 0], \"to\": [16, 16, 16],"
            + "      \"rotation\": { \"angle\": 22.5, \"axis\": \"y\", \"origin\": [8, 8, 8] },"
            + "      \"faces\": {"
            + "        \"north\": { \"uv\": [0, 0, 16, 16], \"texture\": \"#all\", \"tintindex\": 0 },"
            + "        \"south\": { \"uv\": [0, 0, 16, 16], \"texture\": \"#all\" }"
            + "      },"
            + "      \"ambientocclusion\": false, \"shade\": true"
            + "    }"
            + "  ],"
            + "  \"texture_size\": [64, 64],"
            + "  \"gui_light\": \"side\""
            + "}";

    @Test
    public void parsesFullModelJsonWithCorrectTypes() {
        JsonObject root = new JsonParser().parse(SAMPLE_JSON).getAsJsonObject();
        ModelJson model = ModelJson.createGson().fromJson(root, ModelJson.class);

        assertNotNull(model);
        assertEquals("block/cube_all", model.parent);

        // textures must be a String map, not a LinkedTreeMap of raw values
        assertTrue(model.textures instanceof Map);
        assertEquals("blocks/stone", model.textures.get("all"));

        // elements must be ModelJson.Element instances
        assertNotNull(model.elements);
        assertEquals(1, model.elements.size());
        ModelJson.Element element = model.elements.get(0);
        assertTrue(element instanceof ModelJson.Element);

        // nested rotation / faces / flags
        assertNotNull(element.rotation);
        assertEquals(22.5f, element.rotation.angle, 1e-4);
        assertEquals(8.0f, element.rotation.origin[2], 1e-4);
        assertNotNull(element.faces.north);
        assertEquals(0, element.faces.north.tintIndex);
        assertNotNull(element.faces.south);
        assertEquals(-1, element.faces.south.tintIndex);
        assertEquals(Boolean.FALSE, element.ambientocclusion);
        assertEquals(Boolean.TRUE, element.shade);

        // scalar fields
        assertNotNull(model.texture_size);
        assertEquals(64, model.texture_size[0]);
        assertEquals(64, model.texture_size[1]);
        assertEquals("side", model.guiLight);

        // display values must be DisplayTransform instances
        ModelJson.DisplayTransform dt = model.display.get("gui");
        assertTrue(dt instanceof ModelJson.DisplayTransform);
        assertEquals(30.0f, dt.rotation[0], 1e-4);
        assertEquals(0.625f, dt.scale[0], 1e-4);
    }

    @Test
    public void parsesMinimalModelJson() {
        ModelJson model = ModelJson.createGson()
                .fromJson("{ \"parent\": \"builtin/generated\" }", ModelJson.class);
        assertNotNull(model);
        assertEquals("builtin/generated", model.parent);
        assertEquals(null, model.textures);
        assertEquals(null, model.elements);
        assertEquals(null, model.display);
    }

    @Test
    public void parsesMultiElementModelJson() {
        String json = "{ \"elements\": ["
                + "  { \"from\": [0,0,0], \"to\": [8,16,16] },"
                + "  { \"from\": [8,0,0], \"to\": [16,16,16] }"
                + "] }";
        ModelJson model = ModelJson.createGson().fromJson(json, ModelJson.class);
        assertNotNull(model);
        assertEquals(2, model.elements.size());
        assertTrue(model.elements.get(0) instanceof ModelJson.Element);
        assertTrue(model.elements.get(1) instanceof ModelJson.Element);
    }

    @Test
    public void parsesShadeDirectionOverrideWithShadeMutualExclusion() {
        String json = "{ \"elements\": ["
                + "  { \"from\": [0,0,0], \"to\": [16,16,16], \"shade_direction_override\": \"UP\" },"
                + "  { \"from\": [0,0,0], \"to\": [16,16,16], \"shade\": false, \"shade_direction_override\": \"north\" },"
                + "  { \"from\": [0,0,0], \"to\": [16,16,16], \"shade_direction_override\": \"middle\" }"
                + "] }";
        ModelJson model = ModelJson.createGson().fromJson(json, ModelJson.class);
        assertNotNull(model);
        assertEquals(3, model.elements.size());
        // 26.3+ field: case-insensitive input is normalized to lowercase
        assertEquals("up", model.elements.get(0).shadeDirectionOverride);
        // duplicate declaration ('shade' + 'shade_direction_override') is preserved as parsed; a warning is logged
        // and precedence is resolved at bake/render time (override wins)
        assertEquals("north", model.elements.get(1).shadeDirectionOverride);
        assertEquals(Boolean.FALSE, model.elements.get(1).shade);
        // invalid values are dropped with a warning (falls back to face-direction shading)
        assertEquals(null, model.elements.get(2).shadeDirectionOverride);
    }

    @Test
    public void retainsUnrecognizedKeysInRawSnapshot() {
        String json = "{ \"parent\": \"block/cube_all\","
                + "  \"future_root_field\": 7,"
                + "  \"textures\": { \"all\": \"blocks/stone\" },"
                + "  \"elements\": [ {"
                + "    \"from\": [0,0,0], \"to\": [16,16,16], \"shade\": true,"
                + "    \"future_element_field\": { \"nested\": [1,2] },"
                + "    \"faces\": {"
                + "      \"north\": { \"texture\": \"#all\", \"uv\": [0,0,16,16], \"future_face_field\": \"x\" },"
                + "      \"south\": { \"texture\": \"#all\" }"
                + "    } } ] }";
        ModelJson model = ModelJson.createGson().fromJson(json, ModelJson.class);
        assertNotNull(model);

        // root: only the unknown key is captured; known keys must not leak into raw
        assertNotNull(model.raw);
        assertEquals(1, model.raw.size());
        assertEquals(7, model.raw.get("future_root_field").getAsInt());

        // element: unknown retained while known keys keep parsing normally
        ModelJson.Element element = model.elements.get(0);
        assertNotNull(element.raw);
        assertEquals(1, element.raw.size());
        assertTrue(element.raw.get("future_element_field").isJsonObject());
        assertEquals(Boolean.TRUE, element.shade);
        assertEquals(null, element.raw.get("shade"));

        // face: unknown retained; faces without unknowns stay null (no allocation)
        assertNotNull(element.faces.north.raw);
        assertEquals(1, element.faces.north.raw.size());
        assertEquals("x", element.faces.north.raw.get("future_face_field").getAsString());
        assertEquals(null, element.faces.south.raw);
    }

    @Test
    public void retainsUnconsumedDisplaySlotsAndUnknownTransformKeys() {
        String json = "{ \"display\": {"
                + "  \"firstperson_lefthand\": { \"rotation\": [0, 225, 0], \"translation\": [0, 0, 0],"
                + "    \"scale\": [0.4, 0.4, 0.4], \"future_transform_field\": \"x\" },"
                + "  \"on_shelf\": { \"rotation\": [0, 180, 0] },"
                + "  \"gui\": { \"rotation\": [30, 135, 0] }"
                + " } }";
        ModelJson model = ModelJson.createGson().fromJson(json, ModelJson.class);
        assertNotNull(model);
        assertNotNull(model.display);

        // slots this mod does not consume yet are still retained by name (compat layer contract)
        assertTrue(model.display.containsKey("firstperson_lefthand"));
        assertTrue(model.display.containsKey("on_shelf"));

        // known transform fields parse normally; unknown fields land in the per-slot raw snapshot
        ModelJson.DisplayTransform left = model.display.get("firstperson_lefthand");
        assertEquals(225.0f, left.rotation[1], 1e-4);
        assertNotNull(left.raw);
        assertEquals(1, left.raw.size());
        assertEquals("x", left.raw.get("future_transform_field").getAsString());
        assertEquals(null, left.raw.get("rotation"));

        // transforms without unknown fields keep raw null
        assertEquals(null, model.display.get("on_shelf").raw);
        assertEquals(null, model.display.get("gui").raw);
    }
}
