package decok.dfcdvadstf.catframe.ui.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import decok.dfcdvadstf.catframe.exception.WrongMetadataError;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * Texture stretching metadata — loads stretching parameters from {@code
 * .mcmeta} JSON files.
 * </p>
 *
 * <h3>mcmeta Format</h3>
 * <pre>{@code
 * {
 * "stretching": {
 * "type": "nine_patch",
 * "default": { "width": 160, "height": 32 },
 * "edge": { "left": 4, "top": 4, "right": 4, "bottom": 4 }
 * }
 * }
 *
 * // Or three_patch:
 * {
 * "stretching": {
 * "type": "three_patch",
 * "default": { "width": 200, "height": 20 },
 * "edge": { "left": 2, "right": 2 },
 * "tileWidth": 196
 * }
 * }
 * }</pre>
 */
public final class TextureStretchingMetadata implements IMetadataSection {

    /**
     * Section name of the {@code stretching} block inside a {@code .mcmeta} file.
     * <p>
     * The same name is used when the section is registered with the vanilla
     * {@link IMetadataSerializer}.
     * </p>
     */
    public static final String SECTION_NAME = "stretching";

    private static final Map<ResourceLocation, TextureStretchingMetadata> CACHE = new HashMap<>();

    /**
     * Vanilla serializer this section was registered with, or {@code null} while
     * unregistered. Non-null means {@link #load(ResourceLocation)} can ask
     * {@link IResource#getMetadata(String)} instead of parsing the mcmeta itself.
     */
    private static IMetadataSerializer registeredSerializer;

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

    /** Whether the inner (center) region of a nine-patch should be stretched instead of tiled. */
    private final boolean stretchInner;

    private TextureStretchingMetadata(TextureStretching.StretchType type,
            int defaultWidth, int defaultHeight,
            int edgeLeft, int edgeTop, int edgeRight, int edgeBottom,
            int tileWidth) {
        this(type, defaultWidth, defaultHeight, edgeLeft, edgeTop, edgeRight, edgeBottom, tileWidth, false);
    }

    private TextureStretchingMetadata(TextureStretching.StretchType type,
            int defaultWidth, int defaultHeight,
            int edgeLeft, int edgeTop, int edgeRight, int edgeBottom,
            int tileWidth, boolean stretchInner) {
        this.type = type;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.edgeLeft = edgeLeft;
        this.edgeTop = edgeTop;
        this.edgeRight = edgeRight;
        this.edgeBottom = edgeBottom;
        this.tileWidth = tileWidth;
        this.stretchInner = stretchInner;
    }

    // ──── Getters ────

    public TextureStretching.StretchType getType() {
        return type;
    }

    public int getDefaultWidth() {
        return defaultWidth;
    }

    public int getDefaultHeight() {
        return defaultHeight;
    }

    public int getEdgeLeft() {
        return edgeLeft;
    }

    public int getEdgeTop() {
        return edgeTop;
    }

    public int getEdgeRight() {
        return edgeRight;
    }

    public int getEdgeBottom() {
        return edgeBottom;
    }

    public int getTileWidth() {
        return tileWidth;
    }

    /**
     * Whether the inner (center) region of a nine-patch should be stretched
     * (single quad covering the entire center) instead of tiled.
     * <p>Corresponds to {@code "inner": { "will_stretch": true }} in mcmeta.</p>
     */
    public boolean isStretchInner() {
        return stretchInner;
    }

    // ──── Loader ────

    /**
     * Load stretching metadata for the given texture.
     * <p>
     * Prefers the vanilla metadata pipeline: once the {@code stretching} section is
     * registered (see {@link #registerSection(IMetadataSerializer)}),
     * {@link IResource#getMetadata(String)} parses the {@code .mcmeta} file and this
     * method only asks for the result. While unregistered — or when the resource
     * manager cannot hand out the texture — it falls back to reading the
     * {@code .mcmeta} file directly, so stretching data also works before the first
     * resource refresh.
     * </p>
     * <p>
     * Results are cached. Returns {@code null} if no mcmeta or no "stretching" key.
     * </p>
     *
     * @param textureLocation texture ResourceLocation (e.g.
     *                        catframe:textures/gui/sprites/toast/default.png)
     * @return parsed metadata, or null
     */
    public static TextureStretchingMetadata load(ResourceLocation textureLocation) {
        if (textureLocation == null)
            return null;

        if (CACHE.containsKey(textureLocation)) {
            return CACHE.get(textureLocation);
        }

        TextureStretchingMetadata metadata = registeredSerializer != null
                ? loadFromResourceMetadata(textureLocation)
                : loadFromRawMcmeta(textureLocation);

        CACHE.put(textureLocation, metadata);
        return metadata;
    }

    /**
     * Register the {@code stretching} section with the vanilla metadata serializer.
     * <p>
     * Called from the {@code Minecraft.refreshResources()} hook, which runs after the
     * resource manager exists and before any GUI asks for stretching metadata.
     * Registering again with the same serializer is a no-op.
     * </p>
     *
     * @param serializer vanilla metadata serializer of the running client
     */
    public static void registerSection(IMetadataSerializer serializer) {
        if (serializer == null || serializer == registeredSerializer)
            return;

        serializer.registerMetadataSectionType(new StretchingMetadataSectionSerializer(),
                TextureStretchingMetadata.class);
        registeredSerializer = serializer;
    }

    /**
     * Parse a {@code "stretching"} JSON object.
     * <p>
     * Shared by {@link StretchingMetadataSectionSerializer} and the bare-mcmeta
     * fallback, so the section format lives in exactly one place.
     * </p>
     *
     * @param s the {@code stretching} object of an mcmeta file
     * @return parsed metadata
     * @throws WrongMetadataError when a field fails validation
     */
    public static TextureStretchingMetadata parse(JsonObject s) {
        String typeStr = s.has("type") ? s.get("type").getAsString() : "nine_patch";

        int defW = 32, defH = 32;
        if (s.has("default")) {
            JsonObject def = s.getAsJsonObject("default");
            defW = def.get("width").getAsInt();
            defH = def.get("height").getAsInt();
            if (defW < 0 || defH < 0)
                throw new WrongMetadataError(defW, defH);
        }

        if ("nine_patch".equals(typeStr)) {
            int eL = 4, eT = 4, eR = 4, eB = 4;
            if (s.has("edge")) {
                if (s.get("edge").isJsonObject()) {
                    JsonObject e = s.getAsJsonObject("edge");
                    eL = e.has("left") ? e.get("left").getAsInt() : eL;
                    // Accept both "top"/"bottom" and "up"/"down" key names
                    eT = e.has("top") ? e.get("top").getAsInt()
                         : e.has("up") ? e.get("up").getAsInt() : eT;
                    eR = e.has("right") ? e.get("right").getAsInt() : eR;
                    eB = e.has("bottom") ? e.get("bottom").getAsInt()
                         : e.has("down") ? e.get("down").getAsInt() : eB;
                } else {
                    // Shorthand: "edge": 4 means all sides equal
                    int v = s.get("edge").getAsInt();
                    eL = eT = eR = eB = v;
                }
            }
            // Parse optional inner region behaviour (stretch vs tile)
            boolean stretchInner = false;
            if (s.has("inner") && s.get("inner").isJsonObject()) {
                JsonObject inner = s.getAsJsonObject("inner");
                if (inner.has("will_stretch")) {
                    stretchInner = inner.get("will_stretch").getAsBoolean();
                }
            }
            // Validate edge values
            if (eL < 0)
                throw new WrongMetadataError("left", eL);
            if (eT < 0)
                throw new WrongMetadataError("top", eT);
            if (eR < 0)
                throw new WrongMetadataError("right", eR);
            if (eB < 0)
                throw new WrongMetadataError("bottom", eB);
            return new TextureStretchingMetadata(
                    TextureStretching.StretchType.NINE_PATCH,
                    defW, defH, eL, eT, eR, eB, 0, stretchInner);

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
            // Validate edge values
            if (eL < 0)
                throw new WrongMetadataError("left", eL);
            if (eR < 0)
                throw new WrongMetadataError("right", eR);
            int tw = s.has("tileWidth") ? s.get("tileWidth").getAsInt() : (defW - eL - eR);
            return new TextureStretchingMetadata(
                    TextureStretching.StretchType.THREE_PATCH,
                    defW, defH, eL, 0, eR, 0, tw);

        } else if ("tile".equals(typeStr)) {
            return new TextureStretchingMetadata(
                    TextureStretching.StretchType.TILE,
                    defW, defH, 0, 0, 0, 0, 0);
        } else if ("static".equals(typeStr)) {
            // Whole-texture stretch — draws the whole texture over the target area.
            return new TextureStretchingMetadata(
                    TextureStretching.StretchType.STATIC,
                    defW, defH, 0, 0, 0, 0, 0);
        } else {
            throw new WrongMetadataError(typeStr);
        }
    }

    /**
     * Read the section through the vanilla metadata pipeline.
     * <p>
     * The texture resource itself is requested so that {@link IResource#getMetadata(String)}
     * can parse its sibling mcmeta; the unused stream is closed right away because
     * {@code IResource} never owns it.
     * </p>
     */
    private static TextureStretchingMetadata loadFromResourceMetadata(ResourceLocation textureLocation) {
        IResource resource = null;
        try {
            IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
            resource = rm.getResource(textureLocation);
            return (TextureStretchingMetadata) resource.getMetadata(SECTION_NAME);
        } catch (WrongMetadataError e) {
            // Validation error — must propagate, not swallowed
            throw e;
        } catch (Exception e) {
            // Missing texture or unparsable mcmeta — treated as "no stretching data"
            return null;
        } finally {
            if (resource != null)
                closeQuietly(resource.getInputStream());
        }
    }

    /**
     * Fallback reader: parse the {@code .mcmeta} file next to the texture directly.
     * <p>
     * Used while the section is not registered with the vanilla serializer.
     * </p>
     */
    private static TextureStretchingMetadata loadFromRawMcmeta(ResourceLocation textureLocation) {
        ResourceLocation mcmetaLocation = new ResourceLocation(
                textureLocation.getResourceDomain(),
                textureLocation.getResourcePath() + ".mcmeta");

        InputStream stream = null;
        try {
            IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
            stream = rm.getResource(mcmetaLocation).getInputStream();
            JsonObject root = new JsonParser().parse(new InputStreamReader(stream)).getAsJsonObject();

            if (!root.has(SECTION_NAME))
                return null;

            JsonObject s = root.getAsJsonObject(SECTION_NAME);
            return s == null ? null : parse(s);
        } catch (WrongMetadataError e) {
            // Validation error — must propagate, not swallowed
            throw e;
        } catch (Exception e) {
            // No mcmeta file or parse error — return null
            return null;
        } finally {
            closeQuietly(stream);
        }
    }

    /** Close a stream handed out by the resource manager, ignoring failures. */
    private static void closeQuietly(Closeable closeable) {
        if (closeable == null)
            return;
        try {
            closeable.close();
        } catch (Exception ignored) {
            // nothing left to release
        }
    }

    /**
     * Clear the metadata cache.
     */
    public static void clearCache() {
        CACHE.clear();
    }
}
