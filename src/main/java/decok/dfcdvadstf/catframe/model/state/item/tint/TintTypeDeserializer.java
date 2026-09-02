package decok.dfcdvadstf.catframe.model.state.item.tint;

import com.google.gson.JsonObject;

/**
 * Functional interface for deserializing a custom {@link ItemTint} type from JSON.
 * <p>
 * Register implementations via {@link decok.dfcdvadstf.catframe.model.state.item.ItemStateNode#registerTintType(String, TintTypeDeserializer)}
 * to extend the tint system with custom tint types beyond the built-in set.
 *
 * @see decok.dfcdvadstf.catframe.model.state.item.ItemStateNode#registerTintType(String, TintTypeDeserializer)
 */
@FunctionalInterface
public interface TintTypeDeserializer {

    /**
     * Deserialize a JSON tint object into an {@link ItemTint}.
     *
     * @param tintObj the JSON object for a single tint entry (contains type-specific fields)
     * @return the deserialized tint, never null
     */
    ItemTint deserialize(JsonObject tintObj);
}
