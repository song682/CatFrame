package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;

/**
 * Single texture source (mirrors 26.1.2 {@code minecraft:single}).
 * <p>
 * Collects one texture into the atlas under an optionally renamed sprite id —
 * suited to stray textures not referenced by any model, or as an explicit entry
 * point for unstitch / paletted_permutations.
 * <p>
 * Definition JSON example:
 * <pre>{@code {"type": "minecraft:single", "resource": "minecraft:item/foo", "sprite": "minecraft:item/bar"}}</pre>
 * When {@code sprite} is absent the sprite id equals the resource.
 */
@SideOnly(Side.CLIENT)
public final class SingleSource implements AtlasSource {

    /** Source texture path. */
    private final ResourceLocation resource;
    /** Published id (may be null → equals resource). */
    private final ResourceLocation sprite;

    public SingleSource(ResourceLocation resource, ResourceLocation sprite) {
        this.resource = resource;
        this.sprite = sprite;
    }

    @Override
    public String type() {
        return "minecraft:single";
    }

    @Override
    public boolean removesCollected() {
        return false;
    }

    @Override
    public boolean shouldRemove(String spriteId) {
        return false;
    }

    @Override
    public List<SpriteRef> list(IResourceManager manager) {
        ResourceLocation id = sprite != null ? sprite : resource;
        return Collections.singletonList(SpriteRef.of(id, resource));
    }

    @Override
    public String toString() {
        return "SingleSource{" + resource + (sprite != null ? " as " + sprite : "") + "}";
    }
}
