package decok.dfcdvadstf.catframe.model.core;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake;
import decok.dfcdvadstf.catframe.resources.atlas.CatAtlasManager;
import decok.dfcdvadstf.catframe.resources.atlas.CatSprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Texture slot system. Mirrors 26.1.2 {@code TextureSlots}.
 * <p>
 * Resolves model texture references (e.g. {@code "layer0"} → {@code "minecraft:block/stone"})
 * to concrete {@link IIcon} refs, supporting:
 * <ul>
 *   <li>{@code #xxx} reference chain resolution (handled inline in
 *       {@link ModelResolver#resolveTextureVariables(ModelJson)})</li>
 *   <li>Parent model texture overrides (child overrides parent's same-named slot)</li>
 *   <li>Direct lookup from known {@link IIcon} map</li>
 * </ul>
 *
 * <h3>Differences from 26.1.2</h3>
 * <ul>
 *   <li>26.1.2 uses Material/Material.Reference for type-safe refs; here simplified to String → IIcon</li>
 *   <li>No TextureSlots.Data/Resolver layering; static factory methods provided directly</li>
 * </ul>
 * </p>
 */
public class TextureSlots {

    private final Map<String, IIcon> resolvedIcons;

    private TextureSlots(Map<String, IIcon> resolvedIcons) {
        this.resolvedIcons = resolvedIcons;
    }

    // ==================== Factory methods ====================

/**
     * Build TextureSlots from {@link ModelJson} textures map.
     * <p>
     * Iterates model.textures entries, skipping {@code #xxx} refs (resolved in
     * {@link ModelResolver#resolveTextureVariables(ModelJson)}), then looks up
     * corresponding {@link IIcon} from globalIconMap or MC texture atlases.
     *
     * @param model         parsed model (textures # refs already expanded by ModelResolver)
     * @param globalIconMap global IIcon map (from VMM.textureIcons), may be null
     * @param parentOverride parent TextureSlots, child may override its values, may be null
     * @return built TextureSlots
     */
    public static TextureSlots fromModel(ModelJson model,
                                          @Nullable Map<String, IIcon> globalIconMap,
                                          @Nullable TextureSlots parentOverride) {
        Map<String, IIcon> result = new LinkedHashMap<>();

        // 1. Inherit parent's textures first
        if (parentOverride != null) {
            result.putAll(parentOverride.resolvedIcons);
        }

        // 2. Child model's textures override parent's
        if (model.textures != null) {
            for (Map.Entry<String, String> entry : model.textures.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // Skip # refs (expanded in ModelResolver.resolveTextureVariables)
                if (value.startsWith("#")) {
                    CatFrame.logger.debug("[TextureSlots] skipping unresolved reference: {} -> {}", key, value);
                    continue;
                }

// Lookup IIcon: findIcon returns valid icon for non-null refs (missing → missingno),
        // null only possible from empty path ref (defensive skip)
                IIcon icon = findIcon(value, globalIconMap);
                if (icon != null) {
                    result.put(key, icon);
                } else {
                    CatFrame.logger.warn("[TextureSlots] texture error: empty reference for '{}', slot skipped", key);
                }
            }
        }

        return new TextureSlots(result);
    }

    /**
     * Build TextureSlots from pre-built Map (for backward compat).
     */
    public static TextureSlots fromIconMap(Map<String, IIcon> iconMap) {
        return new TextureSlots(new LinkedHashMap<>(iconMap));
    }

    /**
     * Empty TextureSlots.
     */
    public static final TextureSlots EMPTY = new TextureSlots(Collections.emptyMap());

    // ==================== Query ====================

    /**
     * Determines if icon belongs to blocks texture atlas (mirrors 26.1.2
     * {@code sprite.atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS)}).
     * <p>
     * 1.7.10's {@link TextureAtlasSprite} carries no atlas info, so we use three-tier check:
     * <ol>
     *   <li>blocks atlas instance identity → blocks;</li>
     *   <li>items atlas instance identity → items;</li>
     *   <li>neither vanilla atlas contains this instance → classify by path prefix
     *       (forward-compat: {@code items/} / {@code item/} → items, rest with {@code blocks/} /
     *       {@code block/} → blocks), reserved for future custom stitching.</li>
     * </ol>
     * null icon or non-atlas sprite defaults to true (consistent with world paths always binding blocks atlas).
     *
     * @param icon icon to check, may be null
     * @return true=blocks atlas (incl. fallback), false=items atlas
     */
    public static boolean isBlockAtlas(@Nullable IIcon icon) {
        // 0. Custom-atlas sprites: classify by owning atlas id (design doc's custom stitching hook),
        //     must check before instanceof TextureAtlasSprite — otherwise CatSprite always classified as blocks.
        // Custom-atlas sprites are classified by their owning atlas id.
        if (icon instanceof CatSprite) {
            return CatAtlasManager.BLOCK_ATLAS_ID.equals(((CatSprite) icon).getAtlasId());
        }
        if (!(icon instanceof TextureAtlasSprite)) return true;
        try {
            String name = icon.getIconName();
            // 1. blocks atlas instance identity: same-name sprite is same object → from blocks atlas
            if (Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name) == icon) {
                return true;
            }
            // 2. items atlas instance identity
            TextureMap itemsMap = (TextureMap) Minecraft.getMinecraft().getTextureManager()
                    .getTexture(TextureMap.locationItemsTexture);
            if (itemsMap != null && itemsMap.getAtlasSprite(name) == icon) {
                return false;
            }
            // 3. Neither atlas contains this instance (future custom stitching sprite): classify by path prefix
            return !hasItemsPrefix(name);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Checks if texture name (with optional namespace) starts with items atlas folder prefix
     * ({@code items/} and legacy singular {@code item/}).
     */
    private static boolean hasItemsPrefix(@Nullable String name) {
        if (name == null) return false;
        int colon = name.indexOf(':');
        String path = colon >= 0 ? name.substring(colon + 1) : name;
        return path.startsWith("items/") || path.startsWith("item/");
    }

    /**
     * Get {@link IIcon} for the given slot.
     *
     * @param slot texture slot name (e.g. {@code "layer0"}, {@code "particle"})
     * @return IIcon, or null if not found
     */
    @Nullable
    public IIcon getIcon(String slot) {
        return resolvedIcons.get(slot);
    }

    /**
     * Get all resolved texture paths.
     */
    public Set<String> getTexturePaths() {
        return resolvedIcons.keySet();
    }

    /**
     * Get immutable view of underlying icon map.
     */
    public Map<String, IIcon> getIconMap() {
        return Collections.unmodifiableMap(resolvedIcons);
    }

    /**
     * Convert to {@code Map<String, IIcon>} format required by {@link JsonModelBake#bakeElement}.
     * For backward compat.
     */
    public Map<String, IIcon> toIconMap() {
        return new HashMap<>(resolvedIcons);
    }

    /**
     * Whether this TextureSlots is empty (no textures).
     */
    public boolean isEmpty() {
        return resolvedIcons.isEmpty();
    }

    // ==================== Internal helpers ====================

/**
     * Look up IIcon by texture path.
     * <p>
     * [Render three-domain architecture] Vanilla backend semantics (single path): textureIcons
     * directly holds vanilla IIcons; all render paths (item scope / world chunk batches) bind
     * vanilla atlases, UV space globally consistent — lookup accepts any non-null icon;
     * <b>miss → return missingno (purple-black square)</b>, never null, never opaque, no compat fixups.
     * <p>
     * Vanilla backend: globalIconMap holds vanilla IIcons and every render path
     * binds the vanilla atlases, so any non-null icon is accepted; misses resolve
     * to missingno (purple-black square).
     * </p>
     */
    @Nullable
    private static IIcon findIcon(String texturePath, @Nullable Map<String, IIcon> globalIconMap) {
        if (texturePath == null || texturePath.isEmpty()) return null;

        // Accept any non-null icon from the map (vanilla IIcon, vanilla atlas UV;
        // all render paths bind the same vanilla atlas, no mismatch risk).
        // Accept any non-null icon from the map (vanilla atlas UV space).
        IIcon icon = globalIconMap != null ? globalIconMap.get(texturePath) : null;
        if (icon != null) {
            return icon;
        }

        // Final fallback: missingno (vanilla-space purple-black square).
        // Final fallback: missingno (vanilla-space purple-black square).
        CatFrame.logger.warn("[TextureSlots] texture error: '{}' not found in texture table, using missingno", texturePath);
        return CatAtlasManager.getMissingIcon(texturePath);
    }

    // ==================== Object ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextureSlots)) return false;
        TextureSlots that = (TextureSlots) o;
        return resolvedIcons.equals(that.resolvedIcons);
    }

    @Override
    public int hashCode() {
        return resolvedIcons.hashCode();
    }

    @Override
    public String toString() {
        return "TextureSlots{" + resolvedIcons.keySet() + "}";
    }
}
