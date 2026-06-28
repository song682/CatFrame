package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.item.Item;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Manages vanilla block/item model overrides using JSON model files.
 * <p>
 * Since the module split (E3), this class is a <b>pure facade</b> — it holds no fields.
 * All state has been moved to the respective owning classes:
 * <ul>
 *   <li>{@link VMMDataLoader} — namespace discovery, blockstate/mappings loading, IItemState discovery</li>
 *   <li>{@link VanillaTextureTracker} — texture collection, stitch callbacks</li>
 *   <li>{@link VanillaModelRegistry} — model/blockstate registration API</li>
 *   <li>{@link VMMModelBaking} — baking pipeline, caches</li>
 *   <li>{@link VanillaRenderDispatcher} — rendering dispatch</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class VanillaModelManager {

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
            Map<String, Map<Integer, Map<String, String>>> nsData = VMMDataLoader.loadedMetadataMaps.get(namespace);
            if (nsData != null) {
                metaMap = nsData.get(blockName);
            }
            if (metaMap == null) {
                for (Map.Entry<String, Map<String, Map<Integer, Map<String, String>>>> e : VMMDataLoader.loadedMetadataMaps.entrySet()) {
                    metaMap = e.getValue().get(blockName);
                    if (metaMap != null) break;
                }
            }
            if (metaMap == null) return null;

            final Map<Integer, Map<String, String>> finalMap = metaMap;
            return metadata -> finalMap.getOrDefault(metadata, Collections.emptyMap());
        }
    }

    // ==================== 向后兼容内层类 (模块拆分 E3 后作为委托) ====================
    /**
     * Backward-compatible delegate to {@link VMMDataLoader}.
     */
    public static class DataLoading {
        public static void init() { VMMDataLoader.init(); }
        public static void registerNamespace(String namespace) { VMMDataLoader.registerNamespace(namespace); }
        public static void registerMetadataMapping(Block block, IMetadataMapper mapper) { VMMDataLoader.registerMetadataMapping(block, mapper); }
        public static void registerBlockstateRedirect(Block block, IMetadataBlockstateRedirect redirect) { VMMDataLoader.registerBlockstateRedirect(block, redirect); }
    }

    /**
     * Backward-compatible delegate to {@link VanillaModelRegistry}.
     */
    public static class ModelRegistration {
        public static boolean hasModel(Block block) { return VanillaModelRegistry.hasModel(block); }
        public static boolean hasItemModel(Item item) { return VanillaModelRegistry.hasItemModel(item); }
        public static BlockStateModel getBlockModel(Block block) { return VanillaModelRegistry.getBlockModel(block); }
        public static ItemModel getRegisteredItemModel(Item item) { return VanillaModelRegistry.getRegisteredItemModel(item); }
        public static void registerBlockModel(Block block, BlockStateModel model) { VanillaModelRegistry.registerBlockModel(block, model); }
        public static void registerItemModel(Item item, ItemModel model) { VanillaModelRegistry.registerItemModel(item, model); }
        public static void registerBlockRotation(Block block, int metadata, int rotationDeg) { VanillaModelRegistry.registerBlockRotation(block, metadata, rotationDeg); }
        public static void markRandomRotation(Block block) { VanillaModelRegistry.markRandomRotation(block); }
        public static void markAutoOverlay(Block block) { VanillaModelRegistry.markAutoOverlay(block); }
    }

    /**
     * Backward-compatible delegate to {@link VanillaRenderDispatcher}.
     */
    public static class PublicRenderAPI {
        public static boolean renderBlock(IBlockAccess world, int x, int y, int z, Block block, RenderBlocks renderer) {
            return VanillaRenderDispatcher.renderBlock(world, x, y, z, block, renderer);
        }
    }
}
