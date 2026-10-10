package decok.dfcdvadstf.catframe.datapack;

/**
 * One content type of the datapack pipeline (worklist A5/C1/C2).
 * <p>
 * Loaders run as an ordered list on every apply pass: all {@link #unload}s
 * first (removing artifacts of the previous pass), then all {@link #load}s in
 * registration order. The order matters — tags load before recipes so recipe
 * ingredients can be resolved against freshly built tags.
 * </p>
 * <p>
 * Implementations must be tolerant: bad files are reported through
 * {@link DataPackErrors} and skipped, never thrown.
 * </p>
 */
public interface DataContentLoader {

    /** Stable loader id used in reports, e.g. {@code "tags"}. */
    String getId();

    /** Removes artifacts registered by the previous load pass. */
    void unload(DataPackErrors errors);

    /** Loads this content type from all enabled packs. */
    void load(DataPackRepository repository, DataPackErrors errors);
}
