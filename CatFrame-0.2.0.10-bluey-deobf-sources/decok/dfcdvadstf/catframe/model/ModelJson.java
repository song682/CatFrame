package decok.dfcdvadstf.catframe.model;

import com.google.gson.annotations.SerializedName;

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
    @SerializedName("texture_size")
    public int[] texture_size;

    /**
     * Lighting mode: "front" for flat items, "side" for 3D blocks
     */
    @SerializedName("gui_light")
    public String guiLight;

    /**
     * Display transforms for different render contexts
     */
    public Map<String, DisplayTransform> display;

    public static class Element {
        public float[] from;
        public float[] to;
        public Rotation rotation;
        public Faces faces;

        /**
         * 环境光遮蔽: true(默认) 启用逐顶点 AO(采样相邻方块亮度), false 禁用 AO(自发光)
         */
        @SerializedName("ambientocclusion")
        public Boolean ambientocclusion;

        /**
         * 方向阴影: true(默认) 根据法线应用方向光照衰减(top=1.0/side=0.8/bottom=0.5), false 均匀照明
         */
        public Boolean shade;
    }

    public static class Rotation {
        public float angle;
        public String axis;
        public float[] origin;
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
        @SerializedName("tintindex")
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
}