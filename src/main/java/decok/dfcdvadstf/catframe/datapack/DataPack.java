package decok.dfcdvadstf.catframe.datapack;

/**
 * One discovered datapack (worklist A1/A3).
 * <p>
 * A pack is a content root plus identity: {@link #getId()} is its stable name
 * (the directory name on disk, or the subtree name inside a jar) and is the key
 * persisted by {@link DataPackState}; {@link #getSourceId()} names the source
 * it came from ({@code builtin}, {@code config}, {@code game}, {@code world});
 * {@link #getTier()} is the source priority — higher tiers override lower ones,
 * so a world pack can shadow a builtin pack with the same id.
 * </p>
 */
public final class DataPack {

    private final String id;
    private final String sourceId;
    private final int tier;
    private final PackContent content;
    private final PackMetadata metadata;

    private boolean enabled = true;

    public DataPack(String id, String sourceId, int tier, PackContent content, PackMetadata metadata) {
        this.id = id;
        this.sourceId = sourceId;
        this.tier = tier;
        this.content = content;
        this.metadata = metadata;
    }

    /** Stable pack name; persistence and command key. */
    public String getId() {
        return id;
    }

    /** Source the pack was discovered from. */
    public String getSourceId() {
        return sourceId;
    }

    /** Source priority; higher values win conflicts and apply later. */
    public int getTier() {
        return tier;
    }

    public PackContent getContent() {
        return content;
    }

    public PackMetadata getMetadata() {
        return metadata;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return id + " (" + sourceId + ")";
    }
}
