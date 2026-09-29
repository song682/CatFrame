package decok.dfcdvadstf.catframe.model.core;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Builtin {@code builtin/generated} model: standard flat item model (single layer),
 * matching modern vanilla {@code ItemModelGenerator} default generation logic.
 * <p>
 * Model consists of a single 1px-thick planar element using {@code #layer0} texture,
 * with full set of display transforms.
 * </p>
 * <p>
 * Reference {@code net.minecraft.client.resources.model.cuboid.ItemModelGenerator} (26.1).
 * </p>
 */
public final class BuiltinGeneratedModel {

    private BuiltinGeneratedModel() {
    }

    /**
     * Create a {@code builtin/generated} model {@link ModelJson} instance.
     *
     * @return new ModelJson instance (fresh copy each call)
     */
    public static ModelJson create() {
        ModelJson model = new ModelJson();
        model.guiLight = "front";
        model.builtinGenerated = true;

        // 1px-thick planar element, matches vanilla ItemModelGenerator
        // MIN_Z=7.5, MAX_Z=8.5, 1px thickness prevents coplanar z-fighting
        ModelJson.Element elem = new ModelJson.Element();
        elem.from = new float[]{0, 0, 7.5f};
        elem.to = new float[]{16, 16, 8.5f};
        elem.faces = new ModelJson.Faces();
        // north face (-Z dir) UV horiz flip: matches 26.1.2 NORTH_FACE_UVS
        elem.faces.north = new ModelJson.Face();
        elem.faces.north.texture = "#layer0";
        elem.faces.north.uv = new float[]{16, 0, 0, 16};
        elem.faces.north.tintIndex = 0;
        // south face (+Z dir) normal UV: matches 26.1.2 SOUTH_FACE_UVS
        elem.faces.south = new ModelJson.Face();
        elem.faces.south.texture = "#layer0";
        elem.faces.south.uv = new float[]{0, 0, 16, 16};
        elem.faces.south.tintIndex = 0;
        model.elements = new ArrayList<>();
        model.elements.add(elem);

        // display transforms
        model.display = new HashMap<>();
        model.display.put("gui",             display(0, 0, 0, 0, 0, 0, 1, 1, 1));
        model.display.put("ground",          display(0, 2, 0, 0, 0, 0, 0.5f, 0.5f, 0.5f));
        model.display.put("fixed",           display(0, 0, 0, 0, 0, 0, 1, 1, 1));
        // head headgear slot transform: matches 26.1 vanilla generated.json display.head value-for-value
        model.display.put("head",            display(0, 13, 7, 0, 180, 0, 1, 1, 1));
        model.display.put("thirdperson_righthand",  display(0, 3, 1, 0, 0, 0, 0.55f, 0.55f, 0.55f));
        model.display.put("firstperson_righthand",  display(1.13f, 3.2f, 1.13f, 0, -90, 25, 0.68f, 0.68f, 0.68f));
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
