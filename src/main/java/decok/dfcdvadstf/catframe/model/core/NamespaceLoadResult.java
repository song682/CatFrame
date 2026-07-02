package decok.dfcdvadstf.catframe.model.core;

import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;

import java.util.Map;
import java.util.Set;

/**
 * 单个 namespace 并行加载的产出结果。
 * <p>
 * 所有字段都是本地集合，由 {@link NamespaceLoadTask#execute(String)} 填充，
 * 加载完成后由主线程合并到 {@link VanillaModelManager} 的共享静态字段中。
 * <p>
 * 这种"先局部收集、后合并"的模式避免了多线程写入共享 HashMap 的并发问题。
 */
public class NamespaceLoadResult {

    /** 命名空间名称 */
    public final String namespace;

    /** blockstate 数据: blockName → BlockstateJson */
    public final Map<String, BlockstateJson> blockstates;

    /** model_mappings 数据（可能为 null，如果该 namespace 没有 model_mappings.json） */
    public final VanillaModelManager.ModelMappings mappings;

    /** metadata_map 数据: blockName → (meta → (propKey → propValue)) */
    public final Map<String, Map<Integer, Map<String, String>>> metadataMaps;

    /** 该 namespace 收集到的方块纹理路径 */
    public final Set<String> blockTextures;

    /** 该 namespace 收集到的物品纹理路径 */
    public final Set<String> itemTextures;

    /** items/ ItemState 决策树: itemName → 决策树根节点 */
    public final Map<String, ItemStateNode> itemStates;

    public NamespaceLoadResult(String namespace,
                               Map<String, BlockstateJson> blockstates,
                               VanillaModelManager.ModelMappings mappings,
                               Map<String, Map<Integer, Map<String, String>>> metadataMaps,
                               Set<String> blockTextures,
                               Set<String> itemTextures,
                               Map<String, ItemStateNode> itemStates) {
        this.namespace = namespace;
        this.blockstates = blockstates;
        this.mappings = mappings;
        this.metadataMaps = metadataMaps;
        this.blockTextures = blockTextures;
        this.itemTextures = itemTextures;
        this.itemStates = itemStates;
    }
}
