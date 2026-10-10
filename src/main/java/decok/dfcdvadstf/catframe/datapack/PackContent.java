package decok.dfcdvadstf.catframe.datapack;

import java.util.List;

/**
 * Content reader of one datapack, abstracting over the two physical forms a
 * pack can take: a directory on disk and a {@code datapacks/<name>/} subtree
 * embedded in a zip/jar (mod jars and the CatFrame jar itself).
 * <p>
 * All paths are pack-relative, forward-slash separated, e.g.
 * {@code data/mymod/tags/items/foo.json}. Implementations list and read
 * defensively: IO failures degrade to empty lists / {@code null} reads and are
 * reported by the caller through {@link DataPackErrors}.
 * </p>
 */
public interface PackContent {

    /**
     * Lists all file paths under the given prefix (recursively), sorted for a
     * stable, deterministic processing order.
     *
     * @param prefix pack-relative directory prefix, e.g. {@code "data/mymod/tags/items/"}
     * @return matching pack-relative file paths, possibly empty
     */
    List<String> list(String prefix);

    /**
     * Reads a pack-relative file as UTF-8 text.
     *
     * @param path pack-relative file path
     * @return file content, or {@code null} when missing / unreadable
     */
    String read(String path);

    /** Human-readable location of this content (log and command output). */
    String describe();
}
