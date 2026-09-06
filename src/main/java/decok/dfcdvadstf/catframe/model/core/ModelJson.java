package decok.dfcdvadstf.catframe.model.core;

import com.google.gson.*;

import java.lang.reflect.Type;
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
     * 标记此模型继承自 builtin/generated（需触发生成侧面 quad）。
     * transient — 不由 Gson 反序列化，仅由 ModelResolver 在 resolve 时设置。
     */
    public transient boolean builtinGenerated = false;

    /**
     * Display transforms for different render contexts
     */
    public Map<String, DisplayTransform> display;

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

    public static class Element {
        public float[] from;
        public float[] to;
        public Rotation rotation;
        public Faces faces;

        /**
         * 环境光遮蔽: true(默认) 启用逐顶点 AO(采样相邻方块亮度), false 禁用 AO(自发光)
         */
        public Boolean ambientocclusion;

        /**
         * 方向阴影: true(默认) 根据法线应用方向光照衰减(top=1.0/side=0.8/bottom=0.5), false 均匀照明
         */
        public Boolean shade;
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
         * 重新缩放标记（对应 JSON rotation 中的 "rescale"）。
         * <p>当为 {@code true} 时，在旋转前对顶点沿各局部坐标轴应用非均匀缩放，
         * 使旋转后的最大投影分量恢复至原始大小，补偿旋转造成的视觉收缩。
         * 缩放因子 = 1 / max(abs(rotated_axis_unit))，以旋转中心为原点。
         * 对齐 26.1 {@link net.minecraft.client.resources.model.cuboid.CuboidRotation#computeRescale}。
         */
        public boolean rescale;

        /**
         * Multi-axis rotation angles in degrees (one per axis).
         * <p>高版本 Blockbench 导出的多轴旋转格式：
         * {@code {"x": -37.5, "y": 0, "z": 0, "origin": [0,0,0]}}。
         * 当 {@link #angle}/{@link #axis} 未指定时生效，按 X→Y→Z 顺序依次应用
         * （对齐 26.1 {@code Quaternionf.rotationXYZ} 语义）。
         * 支持任意角度（不限于 22.5° 倍数），支持多轴同时非零。
         * <p>
         * 解析优先级：先尝试 {@code angle}/{@code axis}（单轴），
         * 若缺失则回退到 {@code x}/{@code y}/{@code z}（多轴）。
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
    }

    /**
     * Transform applied when rendering in a specific context (gui, hand, ground, etc.)
     */
    public static class DisplayTransform {
        public float[] rotation;
        public float[] translation;
        public float[] scale;
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
            model.textures = obj.has("textures")
                    ? context.deserialize(obj.get("textures"), Map.class) : null;
            model.elements = obj.has("elements")
                    ? context.deserialize(obj.get("elements"), List.class) : null;

            if (obj.has("texture_size")) {
                JsonArray tsArray = obj.getAsJsonArray("texture_size");
                model.texture_size = new int[]{tsArray.get(0).getAsInt(), tsArray.get(1).getAsInt()};
            }

            model.guiLight = obj.has("gui_light") ? obj.get("gui_light").getAsString() : null;
            model.display = obj.has("display")
                    ? context.deserialize(obj.get("display"), Map.class) : null;

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

            return transform;
        }
    }
}