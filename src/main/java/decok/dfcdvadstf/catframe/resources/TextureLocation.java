package decok.dfcdvadstf.catframe.resources;

import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import decok.dfcdvadstf.catframe.resources.atlas.AtlasManager;
import decok.dfcdvadstf.catframe.resources.atlas.Sprite;
import decok.dfcdvadstf.catframe.ui.UiTextureAtlasManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Atlas-backed texture location — the unified texture handle shared by the model
 * and the UI domain.
 * <p>
 * A {@code TextureLocation} names one sprite of the in-house Atlas system and is
 * deliberately <b>both</b> a {@link ResourceLocation} (so it can be handed to any
 * {@code bindTexture}-style API) and an {@link IIcon} (so it can be handed to any
 * UV-consuming draw path). Nothing is stored per instance beyond the ids: the
 * actual pixels live in the atlas, and the {@link IIcon} methods resolve the
 * current atlas sprite on demand, which is what makes a texture swappable without
 * touching any call site (a resource pack / theme re-publishing the sprite takes
 * effect immediately).
 * </p>
 *
 * <h3>Construction (builder only)</h3>
 * The instance is immutable and is always produced through the static entry points
 * plus {@link Builder#build()}:
 * <pre>{@code
 * TextureLocation loc = TextureLocation.set("catframe", "items/bingo").build();
 * TextureLocation vanilla = TextureLocation.setWithDefaultLocation("items/apple").build();
 * }</pre>
 * The second argument is the <b>atlas sprite id</b> (namespace-free), i.e. the id
 * used by the atlas definitions ({@code atlases/<id>.json}); the namespace always
 * goes to the first argument. The texture file is the projection of that id:
 * {@code set("catframe", "items/bingo")} is equivalent, as a resource path, to
 * {@code new ResourceLocation("catframe", "textures/items/bingo.png")}, and its
 * atlas publish key is {@code catframe:items/bingo}.
 *
 * <h3>Resolution order (atlas first, never cached)</h3>
 * <ol>
 *   <li>UI atlas {@code catframe:gui} ({@link UiTextureAtlasManager#findSprite});</li>
 *   <li>vanilla blocks / items atlas (the sprite id is mapped through
 *       {@link VanillaTextureTracker#toVanillaKey}, and the atlas owning the id is
 *       probed first);</li>
 *   <li>missingno (the vanilla {@code missingImage} semantics: texture not found
 *       never crashes and never renders opaque).</li>
 * </ol>
 * <p>
 * {@code equals} / {@code hashCode} / {@code toString} intentionally inherit the
 * {@link ResourceLocation} implementation, so a {@code TextureLocation} stays
 * interchangeable with the plain {@code ResourceLocation} pointing at the same
 * texture file (map keys, GL state caches, … keep working).
 * </p>
 */
public class TextureLocation extends ResourceLocation implements IIcon {

    /** Namespace used by {@link #setWithDefaultLocation(String)} (vanilla assets). */
    public static final String DEFAULT_NAMESPACE = "minecraft";

    /** Texture-file prefix stripped from / added to an atlas sprite id. */
    private static final String TEXTURES_PREFIX = "textures/";
    /** Texture-file suffix stripped from / added to an atlas sprite id. */
    private static final String PNG_SUFFIX = ".png";
    /** Lookup key of the vanilla missing texture (the {@code missingImage} sprite). */
    private static final String MISSING_KEY = "missingno";

    /** Atlas sprite id without namespace (e.g. {@code items/bingo}). */
    private final String spriteId;
    /** Atlas publish key {@code <namespace>:<spriteId>} (e.g. {@code catframe:items/bingo}). */
    private final String publishKey;

    private TextureLocation(String namespace, String atlasSpriteId) {
        super(normalizeNamespace(namespace), toTexturePath(atlasSpriteId));
        this.spriteId = normalizeSpriteId(atlasSpriteId);
        this.publishKey = getResourceDomain() + ":" + this.spriteId;
    }

    // ==================== Static entry points ====================

    /**
     * Starts building a location for a sprite of the given namespace.
     *
     * @param namespace     atlas namespace (e.g. {@code catframe}); null / empty falls
     *                      back to {@link #DEFAULT_NAMESPACE}, matching the vanilla
     *                      {@link ResourceLocation} two-argument constructor
     * @param atlasSpriteId namespace-free sprite id as used by the atlas definitions
     *                      (e.g. {@code items/bingo}); the {@code textures/} prefix and
     *                      the {@code .png} suffix are accepted and normalized away
     * @return the builder; call {@link Builder#build()} to materialize the location
     */
    public static Builder set(String namespace, String atlasSpriteId) {
        return new Builder(namespace, atlasSpriteId);
    }

    /**
     * Starts building a location for a vanilla sprite (namespace
     * {@link #DEFAULT_NAMESPACE}); the argument is the second argument of
     * {@link #set(String, String)}.
     *
     * @param atlasSpriteId namespace-free sprite id (e.g. {@code items/apple})
     * @return the builder; call {@link Builder#build()} to materialize the location
     */
    public static Builder setWithDefaultLocation(String atlasSpriteId) {
        return new Builder(DEFAULT_NAMESPACE, atlasSpriteId);
    }

    /**
     * Immutable builder produced by {@link #set(String, String)} and
     * {@link #setWithDefaultLocation(String)}: it only carries the two ids and
     * validates them on {@link #build()}.
     */
    public static final class Builder {

        private final String namespace;
        private final String atlasSpriteId;

        private Builder(String namespace, String atlasSpriteId) {
            this.namespace = namespace;
            this.atlasSpriteId = atlasSpriteId;
        }

        /**
         * Materializes the location.
         *
         * @return the immutable {@code TextureLocation}
         * @throws IllegalArgumentException when the sprite id is empty or carries a namespace
         */
        public TextureLocation build() {
            return new TextureLocation(namespace, atlasSpriteId);
        }

        @Override
        public String toString() {
            return "TextureLocation.Builder{" + namespace + ":" + atlasSpriteId + "}";
        }
    }

    // ==================== Identity ====================

    /** Namespace-free atlas sprite id (e.g. {@code items/bingo}). */
    public String getSpriteId() {
        return spriteId;
    }

    /** Atlas publish key {@code <namespace>:<spriteId>} (e.g. {@code catframe:items/bingo}). */
    public String getPublishKey() {
        return publishKey;
    }

    // ==================== Atlas resolution ====================

    /**
     * The icon currently bound to this location, resolved on demand (no caching, so
     * an atlas re-stitch / theme swap is picked up by the next call).
     *
     * @return the resolved icon, or null only when even the missing fallback is
     *         unavailable (vanilla atlas not created yet)
     */
    @Nullable
    public IIcon current() {
        Sprite ui = UiTextureAtlasManager.findSprite(publishKey);
        // A published missing placeholder is not a hit: the vanilla atlas may still own the id
        if (ui != null && !ui.isMissing()) {
            return ui;
        }
        IIcon vanilla = findVanillaIcon();
        if (vanilla != null) {
            return vanilla;
        }
        if (ui != null) {
            // UI-domain request whose source texture failed to decode: stay in UI atlas UV space
            return ui;
        }
        return AtlasManager.getMissingIcon(publishKey);
    }

    /**
     * Probes the vanilla atlases, starting with the one owning this sprite id
     * ({@code items/} / legacy {@code item/} → items atlas, everything else → blocks
     * atlas) because the same flat key can exist in both maps with different UVs.
     */
    @Nullable
    private IIcon findVanillaIcon() {
        try {
            TextureMap blocks = Minecraft.getMinecraft().getTextureMapBlocks();
            TextureMap items = (TextureMap) Minecraft.getMinecraft().getTextureManager()
                    .getTexture(TextureMap.locationItemsTexture);
            boolean itemAtlas = spriteId.startsWith("items/") || spriteId.startsWith("item/");
            IIcon icon = lookupIn(itemAtlas ? items : blocks, itemAtlas);
            if (icon != null) {
                return icon;
            }
            return lookupIn(itemAtlas ? blocks : items, !itemAtlas);
        } catch (Exception e) {
            // Dedicated server / atlas not created yet: nothing to resolve against
            return null;
        }
    }

    @Nullable
    private IIcon lookupIn(@Nullable TextureMap map, boolean itemAtlas) {
        if (map == null) {
            return null;
        }
        String key = VanillaTextureTracker.toVanillaKey(publishKey, itemAtlas);
        if (key == null || key.isEmpty()) {
            return null;
        }
        IIcon icon = map.getAtlasSprite(key);
        // 1.7.10 maps an unknown key to the shared missingImage instance, not to null
        if (icon == null || icon == map.getAtlasSprite(MISSING_KEY)) {
            return null;
        }
        return icon;
    }

    // ==================== IIcon contract (delegated to the atlas) ====================

    @Override
    public int getIconWidth() {
        IIcon icon = current();
        return icon != null ? icon.getIconWidth() : 0;
    }

    @Override
    public int getIconHeight() {
        IIcon icon = current();
        return icon != null ? icon.getIconHeight() : 0;
    }

    @Override
    public float getMinU() {
        IIcon icon = current();
        return icon != null ? icon.getMinU() : 0.0F;
    }

    @Override
    public float getMaxU() {
        IIcon icon = current();
        return icon != null ? icon.getMaxU() : 0.0F;
    }

    @Override
    public float getInterpolatedU(double u) {
        IIcon icon = current();
        return icon != null ? icon.getInterpolatedU(u) : 0.0F;
    }

    @Override
    public float getMinV() {
        IIcon icon = current();
        return icon != null ? icon.getMinV() : 0.0F;
    }

    @Override
    public float getMaxV() {
        IIcon icon = current();
        return icon != null ? icon.getMaxV() : 0.0F;
    }

    @Override
    public float getInterpolatedV(double v) {
        IIcon icon = current();
        return icon != null ? icon.getInterpolatedV(v) : 0.0F;
    }

    /** The atlas publish key, stable even before the atlas is stitched. */
    @Override
    public String getIconName() {
        return publishKey;
    }

    // ==================== Id normalization ====================

    /** Empty / null namespace falls back to vanilla, as the vanilla two-argument constructor does. */
    private static String normalizeNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return DEFAULT_NAMESPACE;
        }
        // The vanilla single-argument constructor lowercases the domain; keep that invariant
        return namespace.toLowerCase();
    }

    /** Atlas sprite id → texture file path: {@code items/bingo} → {@code textures/items/bingo.png}. */
    private static String toTexturePath(String atlasSpriteId) {
        return TEXTURES_PREFIX + normalizeSpriteId(atlasSpriteId) + PNG_SUFFIX;
    }

    /**
     * Sprite id validation: the id must be namespace-free (the namespace is the first
     * {@link #set(String, String)} argument); a {@code textures/} prefix and a
     * {@code .png} suffix are tolerated and stripped so both spellings resolve to the
     * same sprite.
     */
    private static String normalizeSpriteId(String atlasSpriteId) {
        if (atlasSpriteId == null || atlasSpriteId.isEmpty()) {
            throw new IllegalArgumentException("Atlas sprite id must not be empty");
        }
        if (atlasSpriteId.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Atlas sprite id must be namespace-free, "
                    + "pass the namespace to set(namespace, atlasSpriteId) instead: " + atlasSpriteId);
        }
        String id = atlasSpriteId;
        if (id.startsWith(TEXTURES_PREFIX)) {
            id = id.substring(TEXTURES_PREFIX.length());
        }
        if (id.endsWith(PNG_SUFFIX)) {
            id = id.substring(0, id.length() - PNG_SUFFIX.length());
        }
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Atlas sprite id has no content: " + atlasSpriteId);
        }
        return id;
    }
}
