package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.resources.atlas.ResourcePackEnumerator;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Directory scanning source (mirrors 26.1.2 {@code minecraft:directory}).
 * <p>
 * Scans top-level PNGs under {@code textures/<source>/} in every namespace
 * (1.7.10 uses plural texture directories) and emits sprite id =
 * {@code <ns>:<prefix><basename>}. Since the 1.7.10 resource manager cannot list
 * directories, the scan is implemented by {@link ResourcePackEnumerator}
 * enumerating classpath / resourcepacks archives; when enumeration is
 * unavailable an empty list is returned (model-driven refs serve as fallback).
 * <p>
 * Definition JSON example:
 * <pre>{@code {"type": "minecraft:directory", "source": "items", "prefix": "items/"}}</pre>
 */
@SideOnly(Side.CLIENT)
public final class DirectorySource implements AtlasSource {

    /** Texture directory name (plural in 1.7.10, e.g. {@code "items"}). */
    private final String source;
    /** sprite id prefix (e.g. {@code "items/"}), matching the 1.7.10 model reference format —
     * the prefix is prepended directly to the namespace id path, so {@code source="items"} +
     * {@code prefix="items/"} yields {@code minecraft:items/apple} for {@code textures/items/apple.png}). */
    private final String prefix;

    public DirectorySource(String source, String prefix) {
        this.source = source;
        this.prefix = prefix;
    }

    @Override
    public String type() {
        return "minecraft:directory";
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
        List<SpriteRef> out = new ArrayList<>();
        // De-duplicate: the same sprite id may appear in multiple archives (pack overrides); keep the first
        Set<String> seen = new HashSet<>();
        String expected = "textures/" + source + "/";
        for (String path : ResourcePackEnumerator.listAssets("assets/")) {
            // assets/<ns>/textures/<source>/<file>.png
            String rest = path.substring("assets/".length());
            int slash = rest.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            String ns = rest.substring(0, slash);
            String tail = rest.substring(slash + 1);
            if (!tail.startsWith(expected) || !tail.endsWith(".png")) {
                continue;
            }
            String middle = tail.substring(expected.length(), tail.length() - 4);
            if (middle.isEmpty() || middle.indexOf('/') >= 0) {
                continue; // Non-recursive: top-level PNGs only
            }
            String spriteId = ns + ":" + prefix + middle;
            if (seen.add(spriteId)) {
                out.add(SpriteRef.of(new ResourceLocation(spriteId)));
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return "DirectorySource{" + source + " -> " + prefix + "}";
    }
}
