package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import decok.dfcdvadstf.catframe.model.ItemModel;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Manages vanilla block/item model overrides using JSON model files.
 * <p>
 * Since the module split (E3), this class is a facade that holds all shared static fields
 * and provides backward-compatible inner class names. Actual logic has been extracted to:
 * <ul>
 *   <li>{@link VMMDataLoader} — namespace discovery, blockstate/mappings loading</li>
 *   <li>{@link VMMTextureTracker} — texture collection, stitch callbacks</li>
 *   <li>{@link VMMModelBaking} — baking pipeline, caches</li>
 *   <li>{@link VMMRegistry} — model registration API</li>
 *   <li>{@link VMMRenderDispatcher} — rendering dispatch</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class VanillaModelManager {

    // ==================== Block Models ====================

    /** Block -> metadata -> list of baked quads */
    static final Map<Block, Map<Integer, List<BakedQuad>>> bakedBlockModels = new HashMap<>();
    /** Block -> metadata -> Y rotation degrees */
    static final Map<Block, Map<Integer, Integer>> blockRotations = new HashMap<>();

    // ==================== IBlockStateProvider Registry ====================

    static final List<Block> registeredStateBlocks = new ArrayList<>();
    /** Block -> loaded BlockstateJson */
    static final Map<Block, BlockstateJson> stateBlockData = new HashMap<>();

    // ==================== Metadata → Properties Mapper Registry ====================

    static final Map<Block, IMetadataMapper> metadataMappers = new HashMap<>();

    // ==================== Blockstate Redirect Registry ====================

    /** Block -> per-meta blockstate redirect */
    static final Map<Block, IMetadataBlockstateRedirect> blockstateRedirects = new HashMap<>();

    // ==================== Model Bake Cache ====================

    /** Cache key "modelPath@rotX@rotY" -> baked quads */
    static final Map<String, List<BakedQuad>> bakedModelCache = new HashMap<>();

    // ==================== Item Models ====================

    /** Item -> damage -> list of baked quads */
    static final Map<Item, Map<Integer, List<BakedQuad>>> bakedItemModels = new HashMap<>();

    // ==================== JsonBlock Transition Flags ====================

    static final Set<Block> randomRotationBlocks = new HashSet<>();
    static final Set<Block> autoOverlayBlocks = new HashSet<>();

    // ==================== BlockStateModel / ItemModel Registry ====================

    static final Map<Block, BlockStateModel> registeredBlockModels = new HashMap<>();
    static final Map<Block, Map<Integer, Integer>> registeredBlockRotations = new HashMap<>();
    static final Map<Item, ItemModel> registeredItemModels = new HashMap<>();
    static final Set<Item> persistentItemModels = new HashSet<>();
    static final Map<Item, IItemJsonModel> interfaceItemModels = new LinkedHashMap<>();
    static final Set<Item> autoDiscoveryMissCache = new HashSet<>();

    // ==================== CatStateDefinition Registry ====================

    static final Map<Block, decok.dfcdvadstf.catframe.model.state.CatStateDefinition<?>> blockStateDefinitions = new HashMap<>();

    // ==================== Texture Tracking ====================

    static final Set<String> pendingTextures = new LinkedHashSet<>();
    static final Set<String> pendingItemTextures = new LinkedHashSet<>();
    static final Map<String, IIcon> textureIcons = new HashMap<>();

    // ==================== Loaded Data ====================

    static final Map<String, Map<String, BlockstateJson>> loadedBlockstates = new HashMap<>();
    static final Map<String, ModelMappings> loadedMappings = new HashMap<>();
    static final Map<String, Map<String, Map<Integer, Map<String, String>>>> loadedMetadataMaps = new HashMap<>();
    static final List<String> namespaces = new ArrayList<>();

    static boolean initialized = false;

    // ==================== Model Mappings Data Class ====================

    public static class ModelMappings {
        public Map<String, String> blocks;
        public Map<String, String> items;
    }

    // ==================== Utilities ====================

    public static class Utilities {

        public static String resolveTextureName(String texturePath) {
            if (texturePath == null) return null;

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

        @Nullable
        public static Block findBlock(String namespace, String name) {
            Block block = Block.getBlockFromName(name);
            if (block != null) return block;
            block = Block.getBlockFromName(namespace + ":" + name);
            return block;
        }

        @Nullable
        public static Item findItem(String namespace, String name) {
            Item item = (Item) Item.itemRegistry.getObject(namespace + ":" + name);
            if (item != null) return item;
            item = (Item) Item.itemRegistry.getObject(name);
            return item;
        }

        @Nullable
        public static IMetadataMapper findMetadataMapEntry(String namespace, String blockName) {
            Map<Integer, Map<String, String>> metaMap = null;
            Map<String, Map<Integer, Map<String, String>>> nsData = loadedMetadataMaps.get(namespace);
            if (nsData != null) {
                metaMap = nsData.get(blockName);
            }
            if (metaMap == null) {
                for (Map.Entry<String, Map<String, Map<Integer, Map<String, String>>>> e : loadedMetadataMaps.entrySet()) {
                    metaMap = e.getValue().get(blockName);
                    if (metaMap != null) break;
                }
            }
            if (metaMap == null) return null;

            final Map<Integer, Map<String, String>> finalMap = metaMap;
            return metadata -> finalMap.getOrDefault(metadata, Collections.emptyMap());
        }
    }
}
