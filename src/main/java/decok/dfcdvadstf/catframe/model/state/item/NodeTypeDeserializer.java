package decok.dfcdvadstf.catframe.model.state.item;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

/**
 * Functional interface for deserializing a custom {@link ItemStateNode} type from JSON.
 * <p>
 * Register implementations via {@link ItemStateNode#registerNodeType(String, NodeTypeDeserializer)}
 * to extend the items/ decision tree with custom node types beyond the built-in set.
 *
 * @see ItemStateNode#registerNodeType(String, NodeTypeDeserializer)
 */
@FunctionalInterface
public interface NodeTypeDeserializer {

    /**
     * Deserialize a JSON object into an {@link ItemStateNode}.
     *
     * @param obj the JSON object (already extracted from the {@code "type"} discriminator)
     * @param ctx the Gson deserialization context (for recursive child node deserialization)
     * @return the deserialized node, never null
     */
    ItemStateNode deserialize(JsonObject obj, JsonDeserializationContext ctx);
}
