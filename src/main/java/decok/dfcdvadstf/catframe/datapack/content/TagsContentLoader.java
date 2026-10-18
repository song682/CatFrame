package decok.dfcdvadstf.catframe.datapack.content;

import decok.dfcdvadstf.catframe.datapack.DataContentLoader;
import decok.dfcdvadstf.catframe.datapack.DataPackErrors;
import decok.dfcdvadstf.catframe.datapack.DataPackRepository;
import decok.dfcdvadstf.catframe.tags.TagLoader;
import decok.dfcdvadstf.catframe.tags.TagManager;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tag content loader.
 * <p>
 * Reads every enabled pack's {@code data/<ns>/tags/item/**.json} and
 * {@code data/<ns>/tags/block/**.json} into the tag loaders, then builds them
 * once. Packs are visited from lowest to highest priority, so contributions
 * accumulate across packs and a {@code "replace": true} file clears everything
 * contributed by lower-priority packs for that tag.
 * </p>
 * <p>
 * Load-phase problems are forwarded to {@link DataPackErrors} with the file
 * path; build-phase problems (missing required references) are reported with
 * the tag id in place of a file path.
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
        TagManager.itemLoader().unload();
        TagManager.blockLoader().unload();
    }

    @Override
    public void load(DataPackRepository repository, DataPackErrors errors) {
        TagLoader<Item> itemLoader = TagManager.itemLoader();
        TagLoader<Block> blockLoader = TagManager.blockLoader();

        itemLoader.setProblemSink((error, tagId, message) -> errors.report(
                error ? DataPackErrors.Severity.ERROR : DataPackErrors.Severity.WARNING,
                null, "tags/item/" + tagId, message));
        blockLoader.setProblemSink((error, tagId, message) -> errors.report(
                error ? DataPackErrors.Severity.ERROR : DataPackErrors.Severity.WARNING,
                null, "tags/block/" + tagId, message));

        int count = itemLoader.load(repository, errors) + blockLoader.load(repository, errors);
        itemLoader.build();
        blockLoader.build();
        LOGGER.info("Loaded {} tag file(s) from data packs", count);
    }
}
