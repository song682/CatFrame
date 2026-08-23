package decok.dfcdvadstf.catframe.datagen.model;

import decok.dfcdvadstf.catframe.model.core.ModelJson;

import java.util.LinkedHashMap;

/**
 * Fluent builder DSL for {@link ModelJson} model files.
 * <p>
 * This is the low-level building block underneath the preset template library:
 * every template definition, custom element model and display transform is
 * expressed with this builder. It mutates a single {@link ModelJson} POJO and
 * returns it from {@link #build()} so the result can be handed straight to the
 * model collector (and serialized by the shared {@code JsonWriter}).
 * <p>
 * {@link ModelJson} 模型文件的流式构建 DSL。这是预设模板库之下的底层积木：
 * 每个模板定义、自定义 elements 模型与 display 变换都用此构建器表达。
 * 它在单个 {@link ModelJson} POJO 上原地修改，{@link #build()} 返回该实例，
 * 结果可直接交给模型收集器（并由统一 {@code JsonWriter} 序列化）。
 */
public final class ModelBuilder {

    private final ModelJson model;

    private ModelBuilder(ModelJson model) {
        this.model = model;
    }

    /**
     * Start building a new model.
     *
     * @return fresh builder
     */
    public static ModelBuilder of() {
        return new ModelBuilder(new ModelJson());
    }

    /**
     * Wrap an existing model for further mutation (used by template creators).
     *
     * @param model existing POJO
     * @return builder over the given instance
     */
    public static ModelBuilder wrap(ModelJson model) {
        return new ModelBuilder(model);
    }

    /**
     * Set the parent model path (e.g. {@code "block/cube_all"}).
     *
     * @param parent parent path without namespace prefix
     * @return this builder
     */
    public ModelBuilder parent(String parent) {
        model.parent = parent;
        return this;
    }

    /**
     * Bind a texture slot (e.g. {@code "all"}) to a location
     * (e.g. {@code "blocks/stone"} or {@code "#all"}).
     *
     * @param key  slot name
     * @param loc  texture location (or {@code #slot} reference)
     * @return this builder
     */
    public ModelBuilder texture(String key, String loc) {
        if (model.textures == null) {
            model.textures = new LinkedHashMap<>();
        }
        model.textures.put(key, loc);
        return this;
    }

    /**
     * Start a cuboid element.
     *
     * @param from minimum corner [x, y, z]
     * @param to   maximum corner [x, y, z]
     * @return element builder
     */
    public ElementBuilder element(float[] from, float[] to) {
        ModelJson.Element element = new ModelJson.Element();
        element.from = from;
        element.to = to;
        if (model.elements == null) {
            model.elements = new java.util.ArrayList<>();
        }
        model.elements.add(element);
        return new ElementBuilder(this, element);
    }

    /**
     * Set the lighting mode: {@code "front"} for flat items, {@code "side"} for
     * 3D blocks.
     *
     * @param guiLight lighting mode string
     * @return this builder
     */
    public ModelBuilder guiLight(String guiLight) {
        model.guiLight = guiLight;
        return this;
    }

    /**
     * Set the texture size (non-16x16 models, e.g. Blockbench exports).
     *
     * @param width  texture width in pixels
     * @param height texture height in pixels
     * @return this builder
     */
    public ModelBuilder textureSize(int width, int height) {
        model.texture_size = new int[]{width, height};
        return this;
    }

    /**
     * Add a display transform for one context (gui/ground/fixed/thirdperson...).
     *
     * @param context     display context name (e.g. {@code "gui"})
     * @param rotation    rotation angles [x, y, z] in degrees
     * @param translation translation [x, y, z] (1/16 block units)
     * @param scale       scale [x, y, z]
     * @return this builder
     */
    public ModelBuilder display(String context, float[] rotation, float[] translation, float[] scale) {
        if (model.display == null) {
            model.display = new LinkedHashMap<>();
        }
        ModelJson.DisplayTransform t = new ModelJson.DisplayTransform();
        t.rotation = rotation;
        t.translation = translation;
        t.scale = scale;
        model.display.put(context, t);
        return this;
    }

    /**
     * Finish building and return the underlying model POJO.
     *
     * @return the built model
     */
    public ModelJson build() {
        return model;
    }

    /**
     * Element sub-builder: faces, rotation, AO and shade.
     */
    public static final class ElementBuilder {

        private final ModelBuilder parent;
        private final ModelJson.Element element;

        private ElementBuilder(ModelBuilder parent, ModelJson.Element element) {
            this.parent = parent;
            this.element = element;
        }

        /**
         * Add a face. Only faces with non-null {@code uv} are emitted.
         *
         * @param dir      face direction (north/east/south/west/up/down)
         * @param uv       UV rect [u1, v1, u2, v2] (16x16 space)
         * @param texture  texture slot reference (e.g. {@code "#all"})
         * @param cullface cull face or {@code null}
         * @return this element builder
         */
        public ElementBuilder face(String dir, float[] uv, String texture, String cullface) {
            if (element.faces == null) {
                element.faces = new ModelJson.Faces();
            }
            ModelJson.Face face = new ModelJson.Face();
            face.uv = uv;
            face.texture = texture;
            face.cullface = cullface;
            switch (dir) {
                case "north": element.faces.north = face; break;
                case "east":  element.faces.east = face; break;
                case "south": element.faces.south = face; break;
                case "west":  element.faces.west = face; break;
                case "up":    element.faces.up = face; break;
                case "down":  element.faces.down = face; break;
                default: throw new IllegalArgumentException("Unknown face direction: " + dir);
            }
            return this;
        }

        /**
         * Convenience: add a face without culling.
         *
         * @param dir     face direction
         * @param uv      UV rect
         * @param texture texture slot reference
         * @return this element builder
         */
        public ElementBuilder face(String dir, float[] uv, String texture) {
            return face(dir, uv, texture, null);
        }

        /**
         * Set the element rotation (Blockbench-compatible: angle around axis at origin).
         *
         * @param angle  rotation angle in degrees
         * @param axis   rotation axis (x/y/z)
         * @param origin rotation origin [x, y, z]
         * @param rescale whether to apply non-uniform rescale compensation
         * @return this element builder
         */
        public ElementBuilder rotation(float angle, String axis, float[] origin, boolean rescale) {
            ModelJson.Rotation rotation = new ModelJson.Rotation();
            rotation.angle = angle;
            rotation.axis = axis;
            rotation.origin = origin;
            rotation.rescale = rescale;
            element.rotation = rotation;
            return this;
        }

        /**
         * Set the tint index for biome coloring (default -1 = no tint).
         *
         * @param tintIndex tint index (0 = first tint)
         * @return this element builder
         */
        public ElementBuilder tintIndex(int tintIndex) {
            if (element.faces == null) {
                element.faces = new ModelJson.Faces();
            }
            applyTint(tintIndex);
            return this;
        }

        private void applyTint(int tintIndex) {
            if (element.faces.north != null) element.faces.north.tintIndex = tintIndex;
            if (element.faces.east != null) element.faces.east.tintIndex = tintIndex;
            if (element.faces.south != null) element.faces.south.tintIndex = tintIndex;
            if (element.faces.west != null) element.faces.west.tintIndex = tintIndex;
            if (element.faces.up != null) element.faces.up.tintIndex = tintIndex;
            if (element.faces.down != null) element.faces.down.tintIndex = tintIndex;
        }

        /**
         * Disable ambient occlusion for this element (self-luminous).
         *
         * @return this element builder
         */
        public ElementBuilder noAmbientOcclusion() {
            element.ambientocclusion = Boolean.FALSE;
            return this;
        }

        /**
         * Disable directional shading for this element (uniform lighting).
         *
         * @return this element builder
         */
        public ElementBuilder noShade() {
            element.shade = Boolean.FALSE;
            return this;
        }

        /**
         * Return to the parent model builder.
         *
         * @return parent builder
         */
        public ModelBuilder end() {
            return parent;
        }
    }
}
