package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

import java.util.Collections;
import java.util.Map;

/**
 * Utility methods extracted from {@link VanillaModelManager}.
 * <p>
 * Package-private helpers for texture name resolution, block/item lookup,
 * and metadata map entry discovery.
 */
@SideOnly(Side.CLIENT)
public class VanillaModelUtil {

    static String resolveTextureName(String texturePath) {
        if (texturePath == null) return null;

        // Keep namespace if present, only strip 'blocks/' or 'items/' prefix
        String namespace = "";
        String pathPart = texturePath;

        if (texturePath.contains(":")) {
            namespace = texturePath.substring(0, texturePath.indexOf(':') + 1);
            pathPart = texturePath.substring(texturePath.indexOf(':') + 1);
        }

        if (pathPart.startsWith("blocks/")) {
            pathPart = pathPart.substring("blocks/".length());
        } else if (pathPart.startsWith("items/")) {
            pathPart = pathPart.substring("items/".length());
        } else if (pathPart.startsWith("block/")) {
            pathPart = pathPart.substring("block/".length());
        } else if (pathPart.startsWith("item/")) {
            pathPart = pathPart.substring("item/".length());
        }

        return namespace + pathPart;
    }

    // ==================== Block/Item Lookup ====================

    static Block findBlock(String namespace, String name) {
        Block block = Block.getBlockFromName(name);
        if (block != null) return block;
        block = Block.getBlockFromName(namespace + ":" + name);
        return block;
    }

    static Item findItem(String namespace, String name) {
        // Try with full qualified name
        Item item = (Item) Item.itemRegistry.getObject(namespace + ":" + name);
        if (item != null) return item;
        item = (Item) Item.itemRegistry.getObject(name);
        return item;
    }

    /**
     * Try to find a metadata_map.json entry for the given block and build an
     * {@link IMetadataMapper} from it.  Returns null if no entry exists.
     */
    static IMetadataMapper findMetadataMapEntry(String namespace, String blockName) {
        // Search the same namespace first, then fallback to all registered namespaces
        Map<Integer, Map<String, String>> metaMap = null;
        Map<String, Map<Integer, Map<String, String>>> nsData = VanillaModelManager.loadedMetadataMaps.get(namespace);
        if (nsData != null) {
            metaMap = nsData.get(blockName);
        }
        if (metaMap == null) {
            for (Map.Entry<String, Map<String, Map<Integer, Map<String, String>>>> e : VanillaModelManager.loadedMetadataMaps.entrySet()) {
                metaMap = e.getValue().get(blockName);
                if (metaMap != null) break;
            }
        }
        if (metaMap == null) return null;

        // Build a mapper lambda from the data
        final Map<Integer, Map<String, String>> finalMap = metaMap;
        return metadata -> finalMap.getOrDefault(metadata, Collections.emptyMap());
    }
}
