package decok.dfcdvadstf.catframe.model.core;

import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;

import java.util.Map;
import java.util.Set;

/**
 * Result produced by loading a single namespace in parallel.
 * <p>
 * Every field is a local collection, filled in by {@link NamespaceLoadTask#execute(String)};
 * once loading finishes the main thread merges them into {@link VanillaModelManager}'s shared
 * static fields.
 * <p>
 * This "collect locally first, merge afterwards" pattern avoids the concurrency problems of
 * multiple threads writing to a shared HashMap.
 */
public class NamespaceLoadResult {

    /** The namespace name */
    public final String namespace;

    /** blockstate data: blockName → BlockstateJson */
    public final Map<String, BlockstateJson> blockstates;

    /** model_mappings data (may be null if the namespace has no model_mappings.json) */
    public final VanillaModelManager.ModelMappings mappings;

    /** Block texture paths collected for this namespace */
    public final Set<String> blockTextures;

    /** Item texture paths collected for this namespace */
    public final Set<String> itemTextures;

    /** items/ ItemState decision trees: itemName → decision tree root node */
    public final Map<String, ItemStateNode> itemStates;

    /** Set of item names declaring {@code oversized_in_gui=true} */
    public final Set<String> oversizedItems;

    public NamespaceLoadResult(String namespace,
                               Map<String, BlockstateJson> blockstates,
                               VanillaModelManager.ModelMappings mappings,
                               Set<String> blockTextures,
                               Set<String> itemTextures,
                               Map<String, ItemStateNode> itemStates,
                               Set<String> oversizedItems) {
        this.namespace = namespace;
        this.blockstates = blockstates;
        this.mappings = mappings;
        this.blockTextures = blockTextures;
        this.itemTextures = itemTextures;
        this.itemStates = itemStates;
        this.oversizedItems = oversizedItems;
    }
}
