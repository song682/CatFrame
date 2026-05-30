package decok.dfcdvadstf.catframe.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class ModelJson {
  public String parent;
  public Map<String, String> textures;
  public List<Element> elements;

  /** Lighting mode: "front" for flat items, "side" for 3D blocks */
  @SerializedName("gui_light")
  public String guiLight;

  /** Display transforms for different render contexts */
  public Map<String, DisplayTransform> display;

  public static class Element {
    public float[] from;
    public float[] to;
    public Rotation rotation;
    public Faces faces;
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
    /** Tint index for biome-based coloring (e.g. grass top). -1 means no tint. */
    @SerializedName("tintindex")
    public int tintIndex = -1;
  }

  /** Transform applied when rendering in a specific context (gui, hand, ground, etc.) */
  public static class DisplayTransform {
    public float[] rotation;
    public float[] translation;
    public float[] scale;
  }
}