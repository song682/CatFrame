package decok.dfcdvadstf.catframe.datapack.content;

import decok.dfcdvadstf.catframe.datapack.DataContentLoader;
import decok.dfcdvadstf.catframe.datapack.DataPackErrors;
import decok.dfcdvadstf.catframe.datapack.DataPackRepository;
import decok.dfcdvadstf.catframe.datapack.mcfunction.FunctionManager;
import decok.dfcdvadstf.catframe.datapack.mcfunction.McFunction;
import decok.dfcdvadstf.catframe.datapack.mcfunction.McFunctionParser;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Function content loader (worklist D5, reconsidered).
 * <p>
 * Reads {@code data/<ns>/functions/**.mcfunction} and the function tags under
 * {@code data/<ns>/tags/functions/**.json}. Functions use override semantics:
 * for the same pack-relative path the highest-priority pack wins, as in vanilla.
 * Tags use merge semantics through the shared {@link decok.dfcdvadstf.catframe.tags.TagLoader}.
 * </p>
 * <p>
 * Functions are parsed before tags are built, so a tag may reference any
 * function from any enabled pack.
 * </p>
 */
public class FunctionsContentLoader implements DataContentLoader {

    private static final Logger LOGGER = LogManager.getLogger(FunctionsContentLoader.class);

    public static final String ID = "functions";

    private static final String FUNCTION_EXTENSION = ".mcfunction";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void unload(DataPackErrors errors) {
        FunctionManager.clearFunctions();
        FunctionManager.tagLoader().unload();
    }

    @Override
    public void load(DataPackRepository repository, DataPackErrors errors) {
        FunctionManager.tagLoader().setProblemSink((error, tagId, message) -> errors.report(
                error ? DataPackErrors.Severity.ERROR : DataPackErrors.Severity.WARNING,
                null, "tags/functions/" + tagId, message));

        Map<ResourceLocation, McFunction> functions = loadFunctions(repository, errors);
        FunctionManager.setFunctions(functions);

        int tagFiles = FunctionManager.tagLoader().load(repository, errors);
        FunctionManager.tagLoader().build();
        LOGGER.info("Loaded {} function(s) and {} function tag file(s) from data packs", functions.size(), tagFiles);
    }

    private static Map<ResourceLocation, McFunction> loadFunctions(DataPackRepository repository, DataPackErrors errors) {
        // Override semantics: same pack-relative path, later (higher-priority) pack wins.
        Map<String, DataPackRepository.PackResource> winners = new LinkedHashMap<>();
        for (String namespace : repository.listNamespaces()) {
            String prefix = "data/" + namespace + "/functions/";
            for (DataPackRepository.PackResource resource : repository.listResources(prefix)) {
                if (resource.getPath().endsWith(FUNCTION_EXTENSION)) {
                    winners.put(resource.getPath(), resource);
                }
            }
        }

        Map<ResourceLocation, McFunction> functions = new LinkedHashMap<>();
        for (DataPackRepository.PackResource resource : winners.values()) {
            String path = resource.getPath();
            String packId = resource.getPack().getId();
            try {
                ResourceLocation id = functionId(path);
                McFunction function = McFunctionParser.parse(id, packId, path, resource.read(), errors);
                if (function != null) {
                    functions.put(id, function);
                }
            } catch (RuntimeException e) {
                errors.error(packId, path, "invalid function: " + e.getMessage());
            }
        }
        return functions;
    }

    /** {@code data/<ns>/functions/<path>.mcfunction} to {@code <ns>:<path>}. */
    private static ResourceLocation functionId(String packPath) {
        String rest = packPath.substring("data/".length());
        int slash = rest.indexOf('/');
        String namespace = rest.substring(0, slash);
        String relative = rest.substring(slash + "/functions/".length());
        String name = relative.substring(0, relative.length() - FUNCTION_EXTENSION.length());
        return new ResourceLocation(namespace, name);
    }
}
