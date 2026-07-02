package decok.dfcdvadstf.catframe.model.core;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 内建 {@code builtin/generated} 模型：标准平面物品模型（单层），
 * 对应原版高版本 {@code ItemModelGenerator} 的默认生成逻辑。
 *
 * <p>模型由一块带 1px 厚度的平面 element 构成，使用 {@code #layer0} 纹理，
 * 并配齐全套 display transforms。
 *
 * <p>参考 {@code net.minecraft.client.resources.model.cuboid.ItemModelGenerator} (26.1)。
 */
public final class BuiltinGeneratedModel {

    private BuiltinGeneratedModel() {
    }

    /**
     * 创建 {@code builtin/generated} 模型的 {@link ModelJson} 实例。
     *
     * @return 新的 ModelJson 实例（每次调用返回独立副本）
     */
    public static ModelJson create() {
        ModelJson model = new ModelJson();
        model.guiLight = "front";
        model.builtinGenerated = true;

        // 带 1px 厚度的平面 element，与原版 ItemModelGenerator 一致
        // MIN_Z=7.5, MAX_Z=8.5，1像素厚度防止双面共面 z-fighting
        ModelJson.Element elem = new ModelJson.Element();
        elem.from = new float[]{0, 0, 7.5f};
        elem.to = new float[]{16, 16, 8.5f};
        elem.faces = new ModelJson.Faces();
        // north 面（-Z方向）UV 水平翻转：与 26.1.2 NORTH_FACE_UVS 一致
        elem.faces.north = new ModelJson.Face();
        elem.faces.north.texture = "#layer0";
        elem.faces.north.uv = new float[]{16, 0, 0, 16};
        elem.faces.north.tintIndex = 0;
        // south 面（+Z方向）正常 UV：与 26.1.2 SOUTH_FACE_UVS 一致
        elem.faces.south = new ModelJson.Face();
        elem.faces.south.texture = "#layer0";
        elem.faces.south.uv = new float[]{0, 0, 16, 16};
        elem.faces.south.tintIndex = 0;
        model.elements = new ArrayList<>();
        model.elements.add(elem);

        // display transforms
        model.display = new HashMap<>();
        model.display.put("gui",             display(0, 0, 0, 0, 0, 0, 1, 1, 1));
        model.display.put("ground",          display(0, 0, 0, 0, 2, 0, 0.5f, 0.5f, 0.5f));
        model.display.put("fixed",           display(0, 0, 0, 0, 0, 0, 1, 1, 1));
        model.display.put("thirdperson_righthand",  display(0, 0, 0, 0, 3, 1, 0.55f, 0.55f, 0.55f));
        model.display.put("firstperson_righthand",  display(1.13f, 3.2f, 1.13f, 0, 0, 0, 0.68f, 0.68f, 0.68f));
        model.display.put("firstperson_lefthand",   display(1.13f, 3.2f, 1.13f, 0, 0, 0, 0.68f, 0.68f, 0.68f));
        return model;
    }

    private static ModelJson.DisplayTransform display(float tx, float ty, float tz,
                                                       float rx, float ry, float rz,
                                                       float sx, float sy, float sz) {
        ModelJson.DisplayTransform dt = new ModelJson.DisplayTransform();
        dt.translation = new float[]{tx, ty, tz};
        dt.rotation = new float[]{rx, ry, rz};
        dt.scale = new float[]{sx, sy, sz};
        return dt;
    }
}
