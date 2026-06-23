package decok.dfcdvadstf.catframe.ui.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import decok.dfcdvadstf.catframe.exception.WrongMetadataError;
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
 *         "edge": { "left": 4, "top": 4, "right": 4, "bottom": 4 }
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

    private static final Map<ResourceLocation, TextureStretchingMetadata> CACHE = new HashMap<>();

    // ──── Fields ────

    private final TextureStretching.StretchType type;
    private final int defaultWidth;
    private final int defaultHeight;

    // nine_patch / three_patch edges
    private final int edgeLeft;
    private final int edgeTop;
    private final int edgeRight;
    private final int edgeBottom;

    private final int tileWidth;

    private TextureStretchingMetadata(TextureStretching.StretchType type,
                                      int defaultWidth, int defaultHeight,
                                      int edgeLeft, int edgeTop, int edgeRight, int edgeBottom,
                                      int tileWidth) {
        this.type = type;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.edgeLeft = edgeLeft;
        this.edgeTop = edgeTop;
        this.edgeRight = edgeRight;
        this.edgeBottom = edgeBottom;
        this.tileWidth = tileWidth;
    }

    // ──── Getters ────

    public TextureStretching.StretchType getType() { return type; }
    public int getDefaultWidth() { return defaultWidth; }
    public int getDefaultHeight() { return defaultHeight; }
    public int getEdgeLeft() { return edgeLeft; }
    public int getEdgeTop() { return edgeTop; }
    public int getEdgeRight() { return edgeRight; }
    public int getEdgeBottom() { return edgeBottom; }
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

                int defW = 32, defH = 32;
                if (s.has("default")) {
                    JsonObject def = s.getAsJsonObject("default");
                    defW = def.get("width").getAsInt();
                    defH = def.get("height").getAsInt();
                    if (defW < 0 || defH < 0) throw new WrongMetadataError(defW, defH);
                }

                if ("nine_patch".equals(typeStr)) {
                    int eL = 4, eT = 4, eR = 4, eB = 4;
                    if (s.has("edge")) {
                        if (s.get("edge").isJsonObject()) {
                            JsonObject e = s.getAsJsonObject("edge");
                            eL = e.has("left") ? e.get("left").getAsInt() : eL;
                            eT = e.has("top") ? e.get("top").getAsInt() : eT;
                            eR = e.has("right") ? e.get("right").getAsInt() : eR;
                            eB = e.has("bottom") ? e.get("bottom").getAsInt() : eB;
                        } else {
                            // Shorthand: "edge": 4 means all sides equal
                            int v = s.get("edge").getAsInt();
                            eL = eT = eR = eB = v;
                        }
                    }
                    // Validate edge values / 校验边缘值
                    if (eL < 0) throw new WrongMetadataError("left", eL);
                    if (eT < 0) throw new WrongMetadataError("top", eT);
                    if (eR < 0) throw new WrongMetadataError("right", eR);
                    if (eB < 0) throw new WrongMetadataError("bottom", eB);
                    metadata = new TextureStretchingMetadata(
                            TextureStretching.StretchType.NINE_PATCH,
                            defW, defH, eL, eT, eR, eB, 0);

                } else if ("three_patch".equals(typeStr)) {
                    int eL = 2, eR = 2;
                    if (s.has("edge")) {
                        if (s.get("edge").isJsonObject()) {
                            JsonObject e = s.getAsJsonObject("edge");
                            eL = e.has("left") ? e.get("left").getAsInt() : eL;
                            eR = e.has("right") ? e.get("right").getAsInt() : eR;
                        } else {
                            int v = s.get("edge").getAsInt();
                            eL = eR = v;
                        }
                    }
                    // Validate edge values / 校验边缘值
                    if (eL < 0) throw new WrongMetadataError("left", eL);
                    if (eR < 0) throw new WrongMetadataError("right", eR);
                    int tw = s.has("tileWidth") ? s.get("tileWidth").getAsInt() : (defW - eL - eR);
                    metadata = new TextureStretchingMetadata(
                            TextureStretching.StretchType.THREE_PATCH,
                            defW, defH, eL, 0, eR, 0, tw);

                } else if ("tile".equals(typeStr)) {
                    metadata = new TextureStretchingMetadata(
                            TextureStretching.StretchType.TILE,
                            defW, defH, 0, 0, 0, 0, 0);
                } else {
                    throw new WrongMetadataError(typeStr);
                }
            }
        } catch (WrongMetadataError e) {
            // Validation error — must propagate, not swallowed
            // 校验错误——必须向上传播，不能被吞掉
            throw e;
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
