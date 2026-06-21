package decok.dfcdvadstf.catframe.ui.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 纹理拉伸元数据 —— 从 {@code .mcmeta} JSON 文件中加载拉伸参数。<br>
 * Texture stretching metadata — loads stretching parameters from {@code .mcmeta} JSON files.
 * </p>
 *
 * <h3>mcmeta 格式 / Format</h3>
 * <pre>{@code
 * {
 *     "stretching": {
 *         "type": "nine_patch",
 *         "default": { "width": 160, "height": 32 },
 *         "border": { "left": 4, "top": 4, "right": 4, "bottom": 4 }
 *     }
 * }
 *
 * // 或者 three_patch:
 * {
 *     "stretching": {
 *         "type": "three_patch",
 *         "default": { "width": 200, "height": 20 },
 *         "edge": { "left": 2, "right": 2 },
 *         "tileWidth": 196
 *     }
 * }
 * }</pre>
 */
public final class TextureStretchingMetadata {

    private static final Gson GSON = new Gson();
    private static final Map<ResourceLocation, TextureStretchingMetadata> CACHE = new HashMap<>();

    // ──── Fields ────

    private final TextureStretching.StretchType type;
    private final int defaultWidth;
    private final int defaultHeight;

    // nine_patch borders
    private final int borderLeft;
    private final int borderTop;
    private final int borderRight;
    private final int borderBottom;

    // three_patch edges
    private final int edgeLeft;
    private final int edgeRight;
    private final int tileWidth;

    private TextureStretchingMetadata(TextureStretching.StretchType type,
                                      int defaultWidth, int defaultHeight,
                                      int borderLeft, int borderTop, int borderRight, int borderBottom,
                                      int edgeLeft, int edgeRight, int tileWidth) {
        this.type = type;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.borderLeft = borderLeft;
        this.borderTop = borderTop;
        this.borderRight = borderRight;
        this.borderBottom = borderBottom;
        this.edgeLeft = edgeLeft;
        this.edgeRight = edgeRight;
        this.tileWidth = tileWidth;
    }

    // ──── Getters ────

    public TextureStretching.StretchType getType() { return type; }
    public int getDefaultWidth() { return defaultWidth; }
    public int getDefaultHeight() { return defaultHeight; }
    public int getBorderLeft() { return borderLeft; }
    public int getBorderTop() { return borderTop; }
    public int getBorderRight() { return borderRight; }
    public int getBorderBottom() { return borderBottom; }
    public int getEdgeLeft() { return edgeLeft; }
    public int getEdgeRight() { return edgeRight; }
    public int getTileWidth() { return tileWidth; }

    // ──── Loader ────

    /**
     * Load stretching metadata for the given texture.
     * <p>Reads and parses the {@code .mcmeta} file next to the texture PNG.</p>
     * <p>Results are cached. Returns {@code null} if no mcmeta or no "stretching" key.</p>
     *
     * @param textureLocation texture ResourceLocation (e.g. catframe:textures/gui/toast/default.png)
     * @return parsed metadata, or null
     */
    public static TextureStretchingMetadata load(ResourceLocation textureLocation) {
        if (textureLocation == null) return null;

        if (CACHE.containsKey(textureLocation)) {
            return CACHE.get(textureLocation);
        }

        ResourceLocation mcmetaLocation = new ResourceLocation(
                textureLocation.getResourceDomain(),
                textureLocation.getResourcePath() + ".mcmeta"
        );

        TextureStretchingMetadata metadata = null;
        try {
            IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
            IResource resource = rm.getResource(mcmetaLocation);
            InputStream stream = resource.getInputStream();
            JsonObject root = new JsonParser().parse(new InputStreamReader(stream)).getAsJsonObject();

            if (root.has("stretching")) {
                JsonObject s = root.getAsJsonObject("stretching");
                String typeStr = s.has("type") ? s.get("type").getAsString() : "nine_patch";

                int defW = 160, defH = 32;
                if (s.has("default")) {
                    JsonObject def = s.getAsJsonObject("default");
                    defW = def.has("width") ? def.get("width").getAsInt() : defW;
                    defH = def.has("height") ? def.get("height").getAsInt() : defH;
                }

                if ("nine_patch".equals(typeStr)) {
                    int bL = 4, bT = 4, bR = 4, bB = 4;
                    if (s.has("border")) {
                        JsonObject b = s.getAsJsonObject("border");
                        bL = b.has("left") ? b.get("left").getAsInt() : bL;
                        bT = b.has("top") ? b.get("top").getAsInt() : bT;
                        bR = b.has("right") ? b.get("right").getAsInt() : bR;
                        bB = b.has("bottom") ? b.get("bottom").getAsInt() : bB;
                    }
                    metadata = new TextureStretchingMetadata(
                            TextureStretching.StretchType.NINE_PATCH,
                            defW, defH, bL, bT, bR, bB, 0, 0, 0);

                } else if ("three_patch".equals(typeStr)) {
                    int eL = 2, eR = 2;
                    if (s.has("edge")) {
                        JsonObject e = s.getAsJsonObject("edge");
                        eL = e.has("left") ? e.get("left").getAsInt() : eL;
                        eR = e.has("right") ? e.get("right").getAsInt() : eR;
                    }
                    int tw = s.has("tileWidth") ? s.get("tileWidth").getAsInt() : (defW - eL - eR);
                    metadata = new TextureStretchingMetadata(
                            TextureStretching.StretchType.THREE_PATCH,
                            defW, defH, 0, 0, 0, 0, eL, eR, tw);

                } else if ("tile".equals(typeStr)) {
                    metadata = new TextureStretchingMetadata(
                            TextureStretching.StretchType.TILE,
                            defW, defH, 0, 0, 0, 0, 0, 0, 0);
                }
            }
        } catch (Exception e) {
            // No mcmeta file or parse error — return null
        }

        CACHE.put(textureLocation, metadata);
        return metadata;
    }

    /**
     * Clear the metadata cache.
     * <p>清除缓存。</p>
     */
    public static void clearCache() {
        CACHE.clear();
    }
}
