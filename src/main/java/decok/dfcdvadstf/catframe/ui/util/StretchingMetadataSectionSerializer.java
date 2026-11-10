package decok.dfcdvadstf.catframe.ui.util;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.minecraft.client.resources.data.BaseMetadataSectionSerializer;

import java.lang.reflect.Type;

/**
 * <p>
 * Vanilla {@code IMetadataSectionSerializer} for the CatFrame {@code stretching}
 * section of a texture {@code .mcmeta} file.
 * </p>
 *
 * <p>
 * Registering this class with the client's {@code IMetadataSerializer} makes the
 * section reachable through {@code IResource#getMetadata("stretching")} — the same
 * code path vanilla uses for {@code texture}, {@code animation}, {@code font},
 * {@code language} and {@code pack}.
 * </p>
 *
 * <p>
 * The section keeps the plain object shape documented on
 * {@link TextureStretchingMetadata}; parsing is delegated to
 * {@link TextureStretchingMetadata#parse(com.google.gson.JsonObject)} so that the
 * format is implemented once and shared with the bare-mcmeta fallback path.
 * </p>
 */
public class StretchingMetadataSectionSerializer extends BaseMetadataSectionSerializer {

    /**
     * Deserialize one {@code stretching} object.
     *
     * @param json    the {@code stretching} object
     * @param type    target type (unused, the result type is fixed)
     * @param context Gson context (unused, the section has no nested adapters)
     * @return parsed metadata
     * @throws JsonParseException   when the section is not a JSON object
     * @throws RuntimeException     when a field fails validation
     *                              ({@code WrongMetadataError} extends
     *                              {@code IllegalArgumentException})
     */
    @Override
    public TextureStretchingMetadata deserialize(JsonElement json, Type type,
            JsonDeserializationContext context) {
        if (json == null || !json.isJsonObject()) {
            throw new JsonParseException(
                    "Invalid stretching metadata: expected object, was " + json);
        }
        return TextureStretchingMetadata.parse(json.getAsJsonObject());
    }

    /**
     * The name of this section type as it appears in JSON.
     */
    @Override
    public String getSectionName() {
        return TextureStretchingMetadata.SECTION_NAME;
    }
}
