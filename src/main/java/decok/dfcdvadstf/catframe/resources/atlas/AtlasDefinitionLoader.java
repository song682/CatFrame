package decok.dfcdvadstf.catframe.resources.atlas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.resources.atlas.source.AtlasSource;
import decok.dfcdvadstf.catframe.resources.atlas.source.FilterSource;
import decok.dfcdvadstf.catframe.resources.atlas.source.SpriteRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Atlas definition discoverer — enumerates and decodes
 * {@code assets/<namespace>/atlases/<id>.json} across all reachable archives
 * (namespace-driven registration, a design-doc confirmed decision).
 * <p>
 * The directory name is plural {@code atlases/} as on the Wiki (the vanilla
 * definition file is {@code assets/minecraft/atlases/blocks.json} → atlas id
 * {@code minecraft:blocks}). Enumeration uses {@link ResourcePackEnumerator}
 * (classpath + resourcepacks); same-id definitions merge across packs because
 * contents are always read via {@code getResource} (which picks the
 * highest-priority version), so repeated puts for the same id carry identical
 * content — naturally satisfying the Wiki semantic that a higher-priority pack
 * overrides the same definition file.
 * <p>
 * [Render three-domain architecture] Collection routing: after loading all
 * definitions they are consumed by atlas id ownership — blocks / items
 * definitions feed the vanilla TextureMap
 * ({@code CatAtlasManager.registerDefinedSprites}), the {@code catframe:gui}
 * definition feeds the CatFrame-built GUI atlas
 * ({@code UiTextureAtlasManager}, independent event chain); definitions of other
 * atlas ids are handled by their respective consumers.
 */
@SideOnly(Side.CLIENT)
public final class AtlasDefinitionLoader {

    private AtlasDefinitionLoader() {
    }

    /**
     * Loads all atlas definitions.
     *
     * @return atlasId ({@code <ns>:<id>}) → decoded source list
     */
    public static Map<String, List<AtlasSource>> loadAll() {
        Map<String, List<AtlasSource>> defs = new LinkedHashMap<>();
        IResourceManager mgr = Minecraft.getMinecraft().getResourceManager();
        for (String path : ResourcePackEnumerator.listAssets("assets/")) {
            // assets/<ns>/atlases/<id>.json (top level, no recursion into subdirectories)
            if (!path.endsWith(".json")) {
                continue;
            }
            int s1 = path.indexOf('/');
            int s2 = path.indexOf('/', s1 + 1);
            int s3 = path.indexOf('/', s2 + 1);
            if (s1 <= 0 || s2 <= 0 || s3 <= 0) {
                continue;
            }
            String ns = path.substring(s1 + 1, s2);
            String dir = path.substring(s2 + 1, s3);
            if (!"atlases".equals(dir)) {
                continue;
            }
            String id = path.substring(s3 + 1, path.length() - ".json".length());
            if (id.isEmpty() || id.indexOf('/') >= 0) {
                continue;
            }
            String atlasId = ns + ":" + id;
            ResourceLocation rl = new ResourceLocation(ns, "atlases/" + id + ".json");
            try {
                IResource res = mgr.getResource(rl);
                List<AtlasSource> sources = AtlasDecoder.decode(res.getInputStream());
                defs.put(atlasId, sources);
                CatFrame.logger.info("[AtlasDefinition] loaded '{}' ({} sources)", atlasId, sources.size());
            } catch (IOException e) {
                CatFrame.logger.warn("[AtlasDefinition] failed to load '{}': {}", rl, e.getMessage());
            }
        }
        return defs;
    }

    /**
     * [Render three-domain architecture] Definition-driven collection (routed by
     * atlas id ownership): all sources of the given atlas definition emit sprite
     * refs in order, ids are used as-is (data-driven keys).
     * <ol>
     *   <li>filter source hit → removed (only affects the definition-driven set);</li>
     *   <li>duplicate sprite id → warn + skip (first wins, mirroring pack override semantics);</li>
     *   <li>the SpriteRef atlasId override field is only debug-logged (the consumer decides whether to accept cross-atlas refs).</li>
     * </ol>
     * Shared by {@code CatAtlasManager.registerDefinedSprites} (vanilla blocks/items
     * output end) and {@code UiTextureAtlasManager} (the {@code catframe:gui} GUI atlas).
     *
     * @param atlasId atlas id ({@code <ns>:<id>}, e.g. {@code minecraft:blocks} / {@code catframe:gui})
     * @return deduplicated sprite ref list (empty when the definition is missing)
     */
    public static List<SpriteRef> collectRefs(String atlasId) {
        Map<String, List<AtlasSource>> defs = loadAll();
        List<AtlasSource> sources = defs.get(atlasId);
        LinkedHashMap<String, SpriteRef> merged = new LinkedHashMap<>();
        if (sources != null) {
            for (AtlasSource source : sources) {
                List<SpriteRef> refs;
                try {
                    refs = source.list(Minecraft.getMinecraft().getResourceManager());
                } catch (RuntimeException e) {
                    CatFrame.logger.warn("[AtlasDefinition] source '{}' in atlas '{}' failed, skipping: {}",
                            source.type(), atlasId, e.getMessage());
                    continue;
                }
                for (SpriteRef ref : refs) {
                    String spriteId = ref.spriteId().toString();
                    if (isFiltered(sources, spriteId)) {
                        continue;
                    }
                    if (merged.containsKey(spriteId)) {
                        CatFrame.logger.warn(
                                "[AtlasDefinition] duplicate sprite '{}' in atlas '{}': earlier entry wins, later skipped",
                                spriteId, atlasId);
                        continue;
                    }
                    if (ref.atlasId() != null && !atlasId.equals(ref.atlasId().toString())) {
                        CatFrame.logger.debug("[AtlasDefinition] sprite '{}' targets atlas '{}' != current '{}', "
                                        + "kept in current atlas (consumer decides whether to accept)",
                                spriteId, ref.atlasId(), atlasId);
                    }
                    merged.put(spriteId, ref);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** Whether the definition-driven set has this id removed by any filter source. */
    private static boolean isFiltered(List<AtlasSource> sources, String spriteId) {
        for (AtlasSource source : sources) {
            if (source instanceof FilterSource && source.shouldRemove(spriteId)) {
                CatFrame.logger.debug("[AtlasDefinition] filter removed '{}'", spriteId);
                return true;
            }
        }
        return false;
    }
}
