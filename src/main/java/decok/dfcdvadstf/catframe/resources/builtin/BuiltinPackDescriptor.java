package decok.dfcdvadstf.catframe.resources.builtin;


/**
 * Immutable description of a built-in resource pack: the pack identity plus the
 * translation keys used by the resource pack GUI and by synthesized pack
 * metadata.
 * <p>
 * This class deliberately touches no Minecraft class, so descriptors can be
 * built and registered at any point, including stages where the game classes
 * are not loadable yet.
 * </p>
 */
public final class BuiltinPackDescriptor {

    /** Prefix of the stable repository name, see {@link #getPackName()}. */
    private static final String PACK_NAME_PREFIX = "builtin/";

    private final String id;
    private final String nameKey;
    private final String descriptionKey;

    /**
     * @param id             globally unique pack id (lowercase letters, digits
     *                       and underscores); it names the classpath folder
     *                       {@code builtin_packs/<id>} and is embedded in the
     *                       stable repository name
     * @param nameKey        translation key for the pack name shown in the GUI
     * @param descriptionKey translation key used when the pack ships no
     *                       {@code pack.mcmeta}
     */
    public BuiltinPackDescriptor(String id, String nameKey, String descriptionKey) {
        if (id == null || !id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid built-in pack id: " + id);
        }
        if (nameKey == null || nameKey.isEmpty()) {
            throw new IllegalArgumentException("Missing name key for built-in pack: " + id);
        }
        if (descriptionKey == null || descriptionKey.isEmpty()) {
            throw new IllegalArgumentException("Missing description key for built-in pack: " + id);
        }
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
    }

    public String getId() {
        return this.id;
    }

    public String getNameKey() {
        return this.nameKey;
    }

    public String getDescriptionKey() {
        return this.descriptionKey;
    }

/**
     * Stable repository name of the pack. It is the persistence key written to
     * {@code options.txt} and it participates in repository entry equality
     * ({@code Entry.equals()} compares {@code toString()} which embeds the file
     * name), so it must <em>never</em> be localized.
     */
    public String getPackName() {
        return PACK_NAME_PREFIX + this.id;
    }
}
