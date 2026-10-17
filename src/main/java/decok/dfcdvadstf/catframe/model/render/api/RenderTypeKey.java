package decok.dfcdvadstf.catframe.model.render.api;

import net.minecraft.util.ResourceLocation;

/**
 * A read-only handle for a render-group registry entry, modelling the render
 * type of the vanilla 26w+ deferred rendering pipeline.
 * <p>
 * Encodes a "texture atlas + blend (solid/translucent)" grouping and carries an
 * <b>explicit sort key</b> ({@link #sortKey()}, lower flushes earlier, so that
 * translucent layers stack correctly on top of opaque ones).
 * <p>
 * Built-in groups are listed in {@link decok.dfcdvadstf.catframe.model.render.pipeline.RenderTypeRegistry};
 * third-party mods may register new groups (including custom sort positions)
 * through the registry.
 */
public interface RenderTypeKey {

    /** Stable identifier of the group (unique registration key, e.g. {@code catframe:block_solid}). */
    ResourceLocation id();

    /** Texture atlas this group is bound to (blocks / items atlas). */
    ResourceLocation atlas();

    /** Whether this group needs alpha blending enabled (translucent). */
    boolean blend();

    /** Explicit sort key: lower flushes earlier; equal keys are ordered stably by registration order. */
    int sortKey();
}
