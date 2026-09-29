package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.ResourceLocation;

/**
 * Sprite reference produced by definition-driven sources (mirrors 26.1.2
 * {@code SpriteIdentifier}).
 * <p>
 * Field semantics:
 * <ul>
 *   <li>{@code spriteId} — the publish key (textureIcons key + CatSprite.texturePath),
 *       e.g. {@code minecraft:block/stone};</li>
 *   <li>{@code resource} — the source texture path (default = spriteId), looked up at
 *       decode time in the 1.7.10 plural texture directories (textures/blocks|items);</li>
 *   <li>{@code atlasId} — target atlas override (may be null; inferred from the
 *       spriteId prefix: {@code block/} → blocks atlas, {@code item/} → items atlas);</li>
 *   <li>{@code transform} — optional pixel transform (unstitch clipping /
 *       paletted_permutations key-color replacement).</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class SpriteRef {

    private final ResourceLocation spriteId;
    private final ResourceLocation resource;
    private final ResourceLocation atlasId;
    private final PixelTransform transform;

    private SpriteRef(ResourceLocation spriteId, ResourceLocation resource,
                      ResourceLocation atlasId, PixelTransform transform) {
        this.spriteId = spriteId;
        this.resource = resource;
        this.atlasId = atlasId;
        this.transform = transform;
    }

    /** spriteId = resource = the given path, no atlas override and no transform (the shape of model-driven refs). */
    public static SpriteRef of(ResourceLocation spriteId) {
        return new SpriteRef(spriteId, spriteId, null, null);
    }

    /** spriteId and resource are separate (the single-source rename case). */
    public static SpriteRef of(ResourceLocation spriteId, ResourceLocation resource) {
        return new SpriteRef(spriteId, resource, null, null);
    }

    /** Full form (unstitch / paletted_permutations: atlas override + pixel transform). */
    public static SpriteRef of(ResourceLocation spriteId, ResourceLocation resource,
                               ResourceLocation atlasId, PixelTransform transform) {
        return new SpriteRef(spriteId, resource, atlasId, transform);
    }

    /** The publish key (textureIcons key). */
    public ResourceLocation spriteId() {
        return spriteId;
    }

    /** Source texture path (used for decode lookup). */
    public ResourceLocation resource() {
        return resource;
    }

    /** Target atlas override (may be null → inferred from the spriteId prefix). */
    public ResourceLocation atlasId() {
        return atlasId;
    }

    /** Pixel transform (may be null). */
    public PixelTransform transform() {
        return transform;
    }

    @Override
    public String toString() {
        return "SpriteRef{" + spriteId + (resource.equals(spriteId) ? "" : " <- " + resource)
                + (atlasId != null ? " @ " + atlasId : "") + "}";
    }
}
