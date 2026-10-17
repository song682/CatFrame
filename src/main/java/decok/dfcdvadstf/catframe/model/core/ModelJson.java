package decok.dfcdvadstf.catframe.model.core;

import com.google.gson.*;
import decok.dfcdvadstf.catframe.CatFrame;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModelJson {
    public String parent;
    public Map<String, String> textures;
    public List<Element> elements;

    /**
     * Texture size in pixels [width, height]. Default is [16, 16].
     * Used for UV coordinate scaling in models with non-standard texture sizes (e.g. 64x64).
     */
    public int[] texture_size;

    /**
     * Lighting mode: "front" for flat items, "side" for 3D blocks
     */
    public String guiLight;

    /**
     * Model-level ambient occlusion flag (top-level {@code "ambientocclusion"} in the model JSON,
     * aligned with 26.1.2 {@code CuboidModel.getAmbientOcclusion}).
     * <p>
     * true = per-vertex AO (smooth lighting), false = flat lighting (uniform, e.g. the
     * cross/tinted_cross plants), null = not declared by this model — inherit it from the
     * parent chain; when nothing in the chain declares it the 26.1.2 default is true.
     */
    public Boolean ambientocclusion;

    /**
     * Marks this model as inheriting from builtin/generated (side quads must be generated).
     * transient — not deserialized by Gson; set only by ModelResolver during resolution.
     */
    public transient boolean builtinGenerated = false;

    /**
     * Display transforms for different render contexts
     */
    public Map<String, DisplayTransform> display;

    /**
     * Raw snapshot of unrecognized JSON keys (every key besides the known ones), retained as-is and never
     * interpreted, so compatibility layers can read future format additions (e.g. 26.x fields) without
     * re-parsing the resource. Null when the model declares none.
     */
    public Map<String, JsonElement> raw;

    /**
     * Create a Gson instance configured with custom deserializers for model parsing.
     */
    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(ModelJson.class, new ModelJsonDeserializer())
                .registerTypeAdapter(Element.class, new ElementDeserializer())
                .registerTypeAdapter(Rotation.class, new RotationDeserializer())
                .registerTypeAdapter(Faces.class, new FacesDeserializer())
                .registerTypeAdapter(Face.class, new FaceDeserializer())
                .registerTypeAdapter(DisplayTransform.class, new DisplayTransformDeserializer())
                .create();
    }

    /**
     * Validates that a 26.3+ {@code shade_direction_override} value is one of the six direction
     * names (up/down/north/south/east/west).
     */
    private static boolean isValidShadeDirection(String name) {
        if (name == null) return false;
        switch (name) {
            case "down": case "up": case "north": case "south": case "west": case "east":
                return true;
            default:
                return false;
        }
    }

    /**
     * Collects all unrecognized keys (anything beyond the known keys) as an as-is snapshot,
     * preserving JSON order.
     * <p>Unrecognized keys are stored but never interpreted or dropped, so compatibility layers
     * can read future format additions (e.g. new 26.x fields); returns null (no allocation)
     * when there are no unknown keys.
     *
     * @param where     log location (model / element / face)
     * @param obj       the source JSON object
     * @param knownKeys the recognized key names
     */
    private static Map<String, JsonElement> collectUnknown(String where, JsonObject obj, String... knownKeys) {
        Map<String, JsonElement> raw = null;
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            boolean known = false;
            for (String k : knownKeys) {
                if (k.equals(e.getKey())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                if (raw == null) raw = new LinkedHashMap<>();
                raw.put(e.getKey(), e.getValue());
            }
        }
        if (raw != null) {
            CatFrame.logger.debug("[ModelJson] {} retains unrecognized keys in raw: {}", where, raw.keySet());
        }
        return raw;
    }

    public static class Element {
        public float[] from;
        public float[] to;
        public Rotation rotation;
        public Faces faces;

        /**
         * Ambient occlusion: true (default) enables per-vertex AO (sampling neighboring block brightness), false disables AO (self-lit)
         */
        public Boolean ambientocclusion;

        /**
         * Directional shading: true (default) applies directional light falloff based on normals (top=1.0/side=0.8/bottom=0.5), false gives uniform lighting
         */
        public Boolean shade;

        /**
         * 26.3+ directional shading override: one of up/down/north/south/east/west.
         * <p>When set, all faces of this element look up the directional shading from this direction
         * instead of using each face's actual orientation; mutually exclusive with {@link #shade}
         * (declaring both is treated as a duplicate and warned about; this field takes precedence).
         * An invalid value is warned about and ignored (falls back to computing from each face's
         * actual orientation).
         */
        public String shadeDirectionOverride;

        /**
         * As-is snapshot of unrecognized keys (anything beyond the known keys; never interpreted or
         * dropped), so compatibility layers can read future format additions; null when there are no
         * unknown keys. Same convention as the root-level {@link ModelJson#raw}.
         */
        public Map<String, JsonElement> raw;
    }

    public static class Rotation {
        /**
         * Single-axis rotation angle in degrees (positive = CW when looking along +axis).
         * Used with {@link #axis} for the legacy single-axis format:
         * {@code {"angle": -22.5, "axis": "x", "origin": [0,0,0]}}.
         */
        public float angle;
        /**
         * Single-axis rotation axis: "x", "y", or "z".
         * Used with {@link #angle} for the legacy single-axis format.
         */
        public String axis;
        /**
         * Rotation center in pixel coordinates (0-16). Defaults to [8, 8, 8] if absent.
         */
        public float[] origin;

        /**
         * Rescale flag (corresponds to "rescale" in the JSON rotation).
         * <p>When {@code true}, a non-uniform scale is applied to the vertices along each local axis
         * before rotating, restoring the largest projected component to its original size and
         * compensating for the visual shrinkage caused by rotation.
         * Scale factor = 1 / max(abs(rotated_axis_unit)), centered on the rotation origin.
         * Aligned with 26.1 {@code net.minecraft.client.resources.model.cuboid.CuboidRotation#computeRescale}.
         */
        public boolean rescale;

        /**
         * Multi-axis rotation angles in degrees (one per axis).
         * <p>Multi-axis rotation format exported by modern Blockbench versions:
         * {@code {"x": -37.5, "y": 0, "z": 0, "origin": [0,0,0]}}.
         * Takes effect when {@link #angle}/{@link #axis} are unspecified, applied in X→Y→Z order
         * (aligned with 26.1 {@code Quaternionf.rotationXYZ} semantics).
         * Accepts any angle (not limited to multiples of 22.5°) and multiple non-zero axes at once.
         * <p>
         * Parsing priority: first try {@code angle}/{@code axis} (single-axis),
         * falling back to {@code x}/{@code y}/{@code z} (multi-axis) when absent.
         */
        public float x, y, z;
    }

    public static class Faces {
        public Face north, east, south, west, up, down;
    }

    public static class Face {
        public float[] uv;
        public String texture;
        public String rotation;
        public String cullface;
        /**
         * Tint index for biome-based coloring (e.g. grass top). -1 means no tint.
         */
        public int tintIndex = -1;

        /**
         * Raw snapshot of unrecognized keys (same convention as {@link ModelJson.Element#raw}); null when none.
         */
        public Map<String, JsonElement> raw;
    }

    /**
     * Transform applied when rendering in a specific context (gui, hand, ground, etc.)
     */
    public static class DisplayTransform {
        public float[] rotation;
        public float[] translation;
        public float[] scale;

        /**
         * Raw snapshot of unrecognized keys (same convention as {@link ModelJson#raw}); null when none.
         * The display slot name itself is always retained as a key of {@link ModelJson#display}, including
         * slots this mod does not yet consume (e.g. firstperson_lefthand, on_shelf).
         */
        public Map<String, JsonElement> raw;
    }

    // ==================== Custom Deserializers ====================

    /**
     * Custom deserializer for ModelJson to eliminate reflection mapping.
     */
    private static class ModelJsonDeserializer implements JsonDeserializer<ModelJson> {
        @Override
        public ModelJson deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("ModelJson must be a JSON object");
            }
            JsonObject obj = json.getAsJsonObject();
            ModelJson model = new ModelJson();

            model.parent = obj.has("parent") ? obj.get("parent").getAsString() : null;

            // textures: {key: value}; values are all strings and must be parsed item by item
            // context.deserialize(..., Map.class) cannot be used: losing the generic type info
            // makes Gson parse it as a LinkedTreeMap, while Map<String, String> is required here
            if (obj.has("textures") && obj.get("textures").isJsonObject()) {
                JsonObject texturesObj = obj.getAsJsonObject("textures");
                model.textures = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : texturesObj.entrySet()) {
                    model.textures.put(e.getKey(), e.getValue().getAsString());
                }
            }

            // elements: an Element array; Element.class must be specified item by item
            // context.deserialize(..., List.class) cannot be used: Gson cannot infer the element type
            // from a raw type and would parse each element as a LinkedTreeMap instead of ModelJson.Element
            if (obj.has("elements") && obj.get("elements").isJsonArray()) {
                JsonArray elementsArray = obj.getAsJsonArray("elements");
                model.elements = new ArrayList<>();
                for (JsonElement elem : elementsArray) {
                    model.elements.add(context.deserialize(elem, Element.class));
                }
            }

            if (obj.has("texture_size")) {
                JsonArray tsArray = obj.getAsJsonArray("texture_size");
                model.texture_size = new int[]{tsArray.get(0).getAsInt(), tsArray.get(1).getAsInt()};
            }

            model.guiLight = obj.has("gui_light") ? obj.get("gui_light").getAsString() : null;

            // Model-level ambient occlusion (top-level "ambientocclusion"): null = absent
            // (inherit along the parent chain; 26.1.2 default true when nothing declares it)
            model.ambientocclusion = obj.has("ambientocclusion")
                    ? obj.get("ambientocclusion").getAsBoolean() : null;

            // display: {slot: DisplayTransform}; DisplayTransform.class must be specified item by item
            // context.deserialize(..., Map.class) cannot be used: same as above, it would be parsed as a LinkedTreeMap
            if (obj.has("display") && obj.get("display").isJsonObject()) {
                JsonObject displayObj = obj.getAsJsonObject("display");
                model.display = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : displayObj.entrySet()) {
                    model.display.put(e.getKey(),
                            context.deserialize(e.getValue(), DisplayTransform.class));
                }
            }

            // Unrecognized top-level keys -> raw snapshot (stored, never interpreted)
            model.raw = collectUnknown("model", obj,
                    "parent", "textures", "elements", "texture_size", "gui_light", "display", "ambientocclusion");

            return model;
        }
    }

    /**
     * Custom deserializer for Element to eliminate reflection mapping.
     */
    private static class ElementDeserializer implements JsonDeserializer<Element> {
        @Override
        public Element deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("Element must be a JSON object");
            }
            JsonObject obj = json.getAsJsonObject();
            Element element = new Element();

            if (obj.has("from")) {
                JsonArray fromArray = obj.getAsJsonArray("from");
                element.from = new float[]{fromArray.get(0).getAsFloat(), fromArray.get(1).getAsFloat(), fromArray.get(2).getAsFloat()};
            }

            if (obj.has("to")) {
                JsonArray toArray = obj.getAsJsonArray("to");
                element.to = new float[]{toArray.get(0).getAsFloat(), toArray.get(1).getAsFloat(), toArray.get(2).getAsFloat()};
            }

            element.rotation = obj.has("rotation")
                    ? context.deserialize(obj.get("rotation"), Rotation.class) : null;
            element.faces = obj.has("faces")
                    ? context.deserialize(obj.get("faces"), Faces.class) : null;

            element.ambientocclusion = obj.has("ambientocclusion")
                    ? obj.get("ambientocclusion").getAsBoolean() : null;
            element.shade = obj.has("shade")
                    ? obj.get("shade").getAsBoolean() : null;

            // 26.3+: "shade_direction_override" is mutually exclusive with the legacy "shade"; declaring both triggers a duplicate-declaration warning (this field takes precedence)
            if (obj.has("shade_direction_override")) {
                if (obj.has("shade")) {
                    CatFrame.logger.warn("[ModelJson] element declares both 'shade' and 'shade_direction_override' "
                            + "(duplicate); they are mutually exclusive - 'shade_direction_override' takes precedence "
                            + "and 'shade' is ignored");
                }
                String value = obj.get("shade_direction_override").getAsString();
                String normalized = value != null ? value.toLowerCase() : null;
                if (isValidShadeDirection(normalized)) {
                    element.shadeDirectionOverride = normalized;
                } else {
                    CatFrame.logger.warn("[ModelJson] invalid 'shade_direction_override' value '{}', expected one of "
                            + "up/down/north/south/east/west; ignoring", value);
                }
            }

            // Unrecognized element keys -> raw snapshot (stored, never interpreted)
            element.raw = collectUnknown("element", obj,
                    "from", "to", "rotation", "faces", "ambientocclusion", "shade", "shade_direction_override");

            return element;
        }
    }

    /**
     * Custom deserializer for Rotation to eliminate reflection mapping.
     */
    private static class RotationDeserializer implements JsonDeserializer<Rotation> {
        @Override
        public Rotation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("Rotation must be a JSON object");
            }
            JsonObject obj = json.getAsJsonObject();
            Rotation rotation = new Rotation();

            // Priority: angle/axis (single-axis) > x/y/z (multi-axis)
            rotation.angle = obj.has("angle") ? obj.get("angle").getAsFloat() : 0;
            rotation.axis = obj.has("axis") ? obj.get("axis").getAsString() : null;

            rotation.x = obj.has("x") ? obj.get("x").getAsFloat() : 0;
            rotation.y = obj.has("y") ? obj.get("y").getAsFloat() : 0;
            rotation.z = obj.has("z") ? obj.get("z").getAsFloat() : 0;

            rotation.origin = new float[]{8, 8, 8};
            if (obj.has("origin")) {
                JsonArray originArray = obj.getAsJsonArray("origin");
                rotation.origin = new float[]{originArray.get(0).getAsFloat(), originArray.get(1).getAsFloat(), originArray.get(2).getAsFloat()};
            }

            rotation.rescale = obj.has("rescale") && obj.get("rescale").getAsBoolean();

            return rotation;
        }
    }

    /**
     * Custom deserializer for Faces to eliminate reflection mapping.
     */
    private static class FacesDeserializer implements JsonDeserializer<Faces> {
        @Override
        public Faces deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("Faces must be a JSON object");
            }
            JsonObject obj = json.getAsJsonObject();
            Faces faces = new Faces();

            faces.north = obj.has("north") ? context.deserialize(obj.get("north"), Face.class) : null;
            faces.east = obj.has("east") ? context.deserialize(obj.get("east"), Face.class) : null;
            faces.south = obj.has("south") ? context.deserialize(obj.get("south"), Face.class) : null;
            faces.west = obj.has("west") ? context.deserialize(obj.get("west"), Face.class) : null;
            faces.up = obj.has("up") ? context.deserialize(obj.get("up"), Face.class) : null;
            faces.down = obj.has("down") ? context.deserialize(obj.get("down"), Face.class) : null;

            return faces;
        }
    }

    /**
     * Custom deserializer for Face to eliminate reflection mapping.
     */
    private static class FaceDeserializer implements JsonDeserializer<Face> {
        @Override
        public Face deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("Face must be a JSON object");
            }
            JsonObject obj = json.getAsJsonObject();
            Face face = new Face();

            if (obj.has("uv")) {
                JsonArray uvArray = obj.getAsJsonArray("uv");
                face.uv = new float[]{uvArray.get(0).getAsFloat(), uvArray.get(1).getAsFloat(),
                             uvArray.get(2).getAsFloat(), uvArray.get(3).getAsFloat()};
            }

            face.texture = obj.has("texture") ? obj.get("texture").getAsString() : null;
            face.rotation = obj.has("rotation") ? obj.get("rotation").getAsString() : null;
            face.cullface = obj.has("cullface") ? obj.get("cullface").getAsString() : null;
            face.tintIndex = obj.has("tintindex") ? obj.get("tintindex").getAsInt() : -1;

            // Unrecognized face keys -> raw snapshot (stored, never interpreted)
            face.raw = collectUnknown("face", obj, "uv", "texture", "rotation", "cullface", "tintindex");

            return face;
        }
    }

    /**
     * Custom deserializer for DisplayTransform to eliminate reflection mapping.
     */
    private static class DisplayTransformDeserializer implements JsonDeserializer<DisplayTransform> {
        @Override
        public DisplayTransform deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("DisplayTransform must be a JSON object");
            }
            JsonObject obj = json.getAsJsonObject();
            DisplayTransform transform = new DisplayTransform();

            if (obj.has("rotation")) {
                JsonArray rotArray = obj.getAsJsonArray("rotation");
                transform.rotation = new float[]{rotArray.get(0).getAsFloat(), rotArray.get(1).getAsFloat(), rotArray.get(2).getAsFloat()};
            }

            if (obj.has("translation")) {
                JsonArray transArray = obj.getAsJsonArray("translation");
                transform.translation = new float[]{transArray.get(0).getAsFloat(), transArray.get(1).getAsFloat(), transArray.get(2).getAsFloat()};
            }

            if (obj.has("scale")) {
                JsonArray scaleArray = obj.getAsJsonArray("scale");
                transform.scale = new float[]{scaleArray.get(0).getAsFloat(), scaleArray.get(1).getAsFloat(), scaleArray.get(2).getAsFloat()};
            }

            // Unrecognized transform keys -> raw snapshot (stored, never interpreted)
            transform.raw = collectUnknown("display transform", obj, "rotation", "translation", "scale");

            return transform;
        }
    }
}