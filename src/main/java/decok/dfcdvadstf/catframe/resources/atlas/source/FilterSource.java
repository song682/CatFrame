package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Regex filter source (mirrors 26.1.2 {@code minecraft:filter}).
 * <p>
 * Produces no sprites; instead the collector removes its <b>already-collected</b>
 * ids matching the namespace / path regexes (full-match via {@code matches()}).
 * An absent regex matches everything. Only the definition-driven set is affected;
 * model-driven refs are never filtered (textures referenced by models must be
 * stitched).
 * <p>
 * Definition JSON example:
 * <pre>{@code {"type": "minecraft:filter", "namespace": "minecraft", "path": "block/.*_debug.*"}}</pre>
 */
@SideOnly(Side.CLIENT)
public final class FilterSource implements AtlasSource {

    /** Namespace regex (null = match all). */
    private final Pattern namespace;
    /** Path regex (null = match all). */
    private final Pattern path;

    public FilterSource(String namespaceRegex, String pathRegex) {
        this.namespace = (namespaceRegex == null || namespaceRegex.isEmpty())
                ? null : Pattern.compile(namespaceRegex);
        this.path = (pathRegex == null || pathRegex.isEmpty())
                ? null : Pattern.compile(pathRegex);
    }

    @Override
    public String type() {
        return "minecraft:filter";
    }

    @Override
    public List<SpriteRef> list(IResourceManager manager) {
        return Collections.emptyList();
    }

    @Override
    public boolean removesCollected() {
        return true;
    }

    @Override
    public boolean shouldRemove(String spriteId) {
        try {
            ResourceLocation id = new ResourceLocation(spriteId);
            if (namespace != null && !namespace.matcher(id.getResourceDomain()).matches()) {
                return false;
            }
            return path == null || path.matcher(id.getResourcePath()).matches();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "FilterSource{" + namespace + " / " + path + "}";
    }
}
