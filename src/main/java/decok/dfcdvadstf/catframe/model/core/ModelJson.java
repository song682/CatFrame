package decok.dfcdvadstf.catframe.model.core;

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
     * 标记此模型继承自 builtin/generated（需触发生成侧面 quad）。
     * transient — 不由 Gson 反序列化，仅由 ModelResolver 在 resolve 时设置。
     */
    public transient boolean builtinGenerated = false;

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