package decok.dfcdvadstf.catframe.model.core;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Builtin {@code builtin/missing} model: full bounding-box model with MissingNo texture.
 * <p>
 * When the game can't find a specified model or model loading fails, it automatically
 * falls back to this model. The model displays the purple-black checkerboard missingno
 * texture, with all six axial faces (north/south/east/west/up/down) defined so the
 * error texture is visible from any angle.
 */
public final class BuiltinMissingModel {

    private BuiltinMissingModel() {
    }

    /**
     * Create a {@code builtin/missing} model {@link ModelJson} instance.
     *
     * @return new ModelJson instance (fresh copy each call)
     */
    public static ModelJson create() {
        ModelJson model = new ModelJson();
        model.textures = new HashMap<>();
        model.textures.put("missingno", "minecraft:missingno");

        ModelJson.Element elem = new ModelJson.Element();
        elem.from = new float[]{0, 0, 0};
        elem.to = new float[]{16, 16, 16};
        elem.faces = new ModelJson.Faces();
        elem.faces.north = new ModelJson.Face();
        elem.faces.north.texture = "#missingno";
        elem.faces.north.uv = new float[]{0, 0, 16, 16};
        elem.faces.north.cullface = "north";
        elem.faces.south = new ModelJson.Face();
        elem.faces.south.texture = "#missingno";
        elem.faces.south.uv = new float[]{0, 0, 16, 16};
        elem.faces.south.cullface = "south";
        elem.faces.east = new ModelJson.Face();
        elem.faces.east.texture = "#missingno";
        elem.faces.east.uv = new float[]{0, 0, 16, 16};
        elem.faces.east.cullface = "east";
        elem.faces.west = new ModelJson.Face();
        elem.faces.west.texture = "#missingno";
        elem.faces.west.uv = new float[]{0, 0, 16, 16};
        elem.faces.west.cullface = "west";
        elem.faces.down = new ModelJson.Face();
        elem.faces.down.texture = "#missingno";
        elem.faces.down.uv = new float[]{0, 0, 16, 16};
        elem.faces.down.cullface = "down";  
        elem.faces.up = new ModelJson.Face();
        elem.faces.up.texture = "#missingno";
        elem.faces.up.uv = new float[]{0, 0, 16, 16};
        elem.faces.up.cullface = "up";
        elem.shade = false;
        elem.ambientocclusion = false;
        model.elements = new ArrayList<>();
        model.elements.add(elem);
        return model;
    }
}
