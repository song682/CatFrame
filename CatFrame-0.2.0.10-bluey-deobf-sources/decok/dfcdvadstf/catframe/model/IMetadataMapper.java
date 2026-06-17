package decok.dfcdvadstf.catframe.model;

import java.util.Map;

/**
 * Converts a vanilla block's metadata integer into a property map for
 * blockstate variant matching (1.16.5+ property-key style).
 *
 * <p>Register via {@link VanillaModelManager#registerMetadataMapping}.
 *
 * <p>Example for stone (metadata 0=stone, 1=granite, 3=diorite, 5=andesite):
 * <pre>{@code
 * VanillaModelManager.registerMetadataMapping(Blocks.stone, meta -> {
 *     String[] variants = {"stone", "granite", "stone", "diorite", "stone", "andesite", "stone"};
 *     return Collections.singletonMap("variant", meta < variants.length ? variants[meta] : "stone");
 * });
 * }</pre>
 *
 * <p>Example for log (metadata bits: low 2 = wood type, high 2 = rotation):
 * <pre>{@code
 * VanillaModelManager.registerMetadataMapping(Blocks.log, meta -> {
 *     String[] woods = {"oak", "spruce", "birch", "jungle"};
 *     String[] axes  = {"y", "x", "z"};
 *     Map<String, String> props = new HashMap<>();
 *     props.put("wood", woods[meta & 3]);
 *     props.put("axis", axes[(meta >> 2) % 3]);
 *     return props;
 * });
 * }</pre>
 *
 * @see VanillaModelManager#registerMetadataMapping
 */
@FunctionalInterface
public interface IMetadataMapper {

    /**
     * Produce a property map for the given metadata value.
     * Values must be strings (matching the blockstate JSON variant keys).
     *
     * @param metadata the metadata value (0–15)
     * @return property map (e.g. {@code {"variant":"granite"}}), never null
     */
    Map<String, String> map(int metadata);
}
