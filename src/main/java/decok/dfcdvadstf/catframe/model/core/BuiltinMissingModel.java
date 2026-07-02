package decok.dfcdvadstf.catframe.model.core;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 内建 {@code builtin/missing} 模型：MissingNo 纹理的完整包围盒模型。
 *
 * <p>当游戏找不到指定模型或模型加载出错时，自动 fallback 到此模型。
 * 该模型显示为紫黑相间的 missingno 纹理，且四个轴向面（north/south/east/west）
 * 均有定义，确保从任意方向观察都能看到错误纹理。
 */
public final class BuiltinMissingModel {

    private BuiltinMissingModel() {
    }

    /**
     * 创建 {@code builtin/missing} 模型的 {@link ModelJson} 实例。
     *
     * @return 新的 ModelJson 实例（每次调用返回独立副本）
     */
    public static ModelJson create() {
        ModelJson model = new ModelJson();
        model.textures = new HashMap<>();
        model.textures.put("all", "minecraft:missingno");

        ModelJson.Element elem = new ModelJson.Element();
        elem.from = new float[]{0, 0, 8};
        elem.to = new float[]{16, 16, 8};
        elem.faces = new ModelJson.Faces();
        elem.faces.north = new ModelJson.Face();
        elem.faces.north.texture = "#all";
        elem.faces.north.uv = new float[]{0, 0, 16, 16};
        elem.faces.south = new ModelJson.Face();
        elem.faces.south.texture = "#all";
        elem.faces.south.uv = new float[]{0, 0, 16, 16};
        elem.faces.east = new ModelJson.Face();
        elem.faces.east.texture = "#all";
        elem.faces.east.uv = new float[]{0, 0, 16, 16};
        elem.faces.west = new ModelJson.Face();
        elem.faces.west.texture = "#all";
        elem.faces.west.uv = new float[]{0, 0, 16, 16};
        model.elements = new ArrayList<>();
        model.elements.add(elem);
        return model;
    }
}
