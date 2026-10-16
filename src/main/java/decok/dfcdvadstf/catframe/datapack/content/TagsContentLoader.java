package decok.dfcdvadstf.catframe.datapack.content;

import com.google.gson.JsonObject;
import decok.dfcdvadstf.catframe.datapack.DataContentLoader;
import decok.dfcdvadstf.catframe.datapack.DataPackErrors;
import decok.dfcdvadstf.catframe.datapack.DataPackJson;
import decok.dfcdvadstf.catframe.datapack.DataPackRepository;
import decok.dfcdvadstf.catframe.tags.CatFrameTags;
import decok.dfcdvadstf.catframe.tags.TagFile;
import decok.dfcdvadstf.catframe.tags.TagLoader;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tags content loader (worklist C1).
 * <p>
 * Reads every enabled pack's {@code data/<ns>/tags/items/**.json} and
 * {@code data/<ns>/tags/blocks/**.json} into the tag loaders, then rebuilds
 * them once. Because packs are visited from lowest to highest priority, the
 * existing {@link TagFile} semantics compose naturally across packs:
 * contributions accumulate (cross-pack merge) and a {@code "replace": true}
 * file clears everything contributed by lower-priority packs for that tag.
 * Dependency ordering across tag references is handled inside
 * {@link TagLoader#rebuild()}.
 * </p>
 * <p>
 * Build-phase problems are forwarded to the consolidated report through the
 * loader's problem sink; discovered/unbuilt tags are reported with their tag id
 * in place of a file path.
 * </p>
 */
public class TagsContentLoader implements DataContentLoader {

    private static final Logger LOGGER = LogManager.getLogger(TagsContentLoader.class);

    public static final String ID = "tags";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void unload(DataPackErrors errors) {
        CatFrameTags.itemLoader().clearRawEntries();
        CatFrameTags.blockLoader().clearRawEntries();
    }

    @Override
    public void load(DataPackRepository repository, DataPackErrors errors) {
        TagLoader<Item> itemLoader = CatFrameTags.itemLoader();
        TagLoader<Block> blockLoader = CatFrameTags.blockLoader();

        itemLoader.setProblemSink((error, tagId, message) -> errors.report(
                error ? DataPackErrors.Severity.ERROR : DataPackErrors.Severity.WARNING,
                null, "tags/" + tagId, message));
        blockLoader.setProblemSink((error, tagId, message) -> errors.report(
                error ? DataPackErrors.Severity.ERROR : DataPackErrors.Severity.WARNING,
                null, "tags/" + tagId, message));

        int count = 0;
        for (String namespace : repository.listNamespaces()) {
            count += loadDirectory(repository, errors, namespace, "items", itemLoader);
            count += loadDirectory(repository, errors, namespace, "blocks", blockLoader);
        }

        itemLoader.rebuild();
        blockLoader.rebuild();
        LOGGER.info("Loaded {} tag file(s) from data packs", count);
    }

    private <T> int loadDirectory(DataPackRepository repository, DataPackErrors errors,
                                  String namespace, String kind, TagLoader<T> loader) {
        String prefix = "data/" + namespace + "/tags/" + kind + "/";
        int count = 0;
        for (DataPackRepository.PackResource resource : repository.listResources(prefix)) {
            String path = resource.getPath();
            String relative = path.substring(prefix.length());
            if (!relative.endsWith(".json")) {
                continue;
            }
            String tagName = relative.substring(0, relative.length() - ".json".length());

            JsonObject json = DataPackJson.parseObject(resource.read(), resource.getPack().getId(), path, errors);
            if (json == null) {
                continue;
            }
            try {
                ResourceLocation tagId = new ResourceLocation(namespace, tagName);
                loader.addTagFile(tagId, TagFile.fromJson(json));
                count++;
            } catch (RuntimeException e) {
                errors.error(resource.getPack().getId(), path, "invalid tag file: " + e);
            }
        }
        return count;
    }
}
