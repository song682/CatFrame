package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.async.AsyncBakePipeline;
import decok.dfcdvadstf.catframe.model.lazy.LazySingleBlockModel;
import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.render.extension.tint.TintRegistry;
import decok.dfcdvadstf.catframe.model.state.*;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import decok.dfcdvadstf.catframe.model.state.item.tint.ItemStateTintBridge;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStairs;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages vanilla block/item model overrides using JSON model files.
 * <p>
 * Since the module split (E3), this class is a <b>pure facade</b> — it holds no fields.
 * All state has been moved to the respective owning classes:
 * <ul>
 *   <li>{@link ModelManagerDataLoader} — namespace discovery, blockstate/mappings loading, IItemState discovery</li>
 *   <li>{@link VanillaTextureTracker} — texture collection, stitch callbacks</li>
 *   <li>{@link ModelRegistry} — model/blockstate registration API</li>
 *   <li>{@link Baking} — baking pipeline, caches</li>
 *   <li>{@link RenderDispatcher} — rendering dispatch</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class VanillaModelManager {

    // ==================== Model Mappings Data Class ====================

    public static class ModelMappings {
        /**
         * State-mapping mode switch (new-era format). Defaults to {@code false},
         * preserving the legacy key → model-path semantics for backward compatibility.
         * <p>
         * When {@code true}, the blocks/items values are no longer model paths but
         * (Block/Item)State names:
         * <ul>
         *   <li>blocks value → {@code blockstates/{value}.json} (may carry an "ns:" prefix)</li>
         *   <li>items value → {@code items/{value}.json} decision tree (may carry an "ns:" prefix)</li>
         * </ul>
         * After that, the regular data-driven state → model pipeline is used. Intended for
         * legacy mods that do not implement JSON loading: wire old IDs directly to existing
         * state definitions without laying out a separate model file for each old ID.
         */
        public boolean state_mapping = false;
        public Map<String, String> blocks;
        public Map<String, String> items;
    }

    // ==================== Utilities ====================

    public static class Utilities {

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
    }

    // ==================== Backward-compatible inner classes (delegates after the E3 module split) ====================

    /**
     * Backward-compatible delegate to {@link ModelManagerDataLoader}.
     */
    public static class DataLoading {
        public static void init() {
            ModelManagerDataLoader.init();
        }

        public static void registerNamespace(String namespace) {
            ModelManagerDataLoader.registerNamespace(namespace);
        }

        public static void registerBlockstateRedirect(Block block, IMetadataBlockstateRedirect redirect) {
            ModelManagerDataLoader.registerBlockstateRedirect(block, redirect);
        }
    }

    /**
     * Model registration: the model registration entry under the async baking architecture.
     * <p>
     * No synchronous baking is performed anymore. All baking is handled by
     * {@link BakedModelCache} (lazy baking) and {@link AsyncBakePipeline} (async pre-baking).
     * <p>
     * Responsibilities of this class:
     * <ul>
     *   <li>materialize the declarative Models spec (typed {@link BlockStateDefinition} resident table)</li>
     *   <li>create resident models for blockstates ({@link ResidentStateModel})</li>
     *   <li>create lazy models for model_mappings ({@link LazySingleBlockModel}, {@link ItemStateModel})</li>
     *   <li>handle BlockPane special registration</li>
     *   <li>register the Forge IItemRenderer</li>
     * </ul>
     */
    public static class Baking {

        // ==================== Model registration entry ====================

        /**
         * Registers all models (does not bake).
         * <p>
         * Called from {@link VanillaTextureTracker#onTextureStitchPost}, when textures are
         * already stitched and iconMap has been passed through parameters to the cache and
         * baking pipeline, so lazy baking can resolve textures correctly.
         * <p>
         * Replaces the old system's {@code bakeAllModels()}.
         */
        public static void registerAllModels() {
            // Clear the registry (keeping persistent entries)
            ModelRegistry.registeredBlockModels.clear();
            ModelRegistry.registeredBlockRotations.clear();
            ModelRegistry.registeredItemModels.keySet()
                    .removeIf(item -> !ModelRegistry.persistentItemModels.contains(item));
            // oversized flags are fully derived from loadedOversizedItems and rebuilt below —
            // clear to avoid stale entries surviving re-registration (e.g. after pack removal)
            ModelRegistry.oversizedItems.clear();

            // Step 0: materialize the declarative Models spec (typed resident table + itemFromBlockstate)
            Models.materialize();

            // Step 2: process blockstates — register lazy BlockStateModel
            for (Map.Entry<String, Map<String, BlockstateJson>> nsEntry :
                    new ArrayList<>(ModelManagerDataLoader.loadedBlockstates.entrySet())) {
                String namespace = nsEntry.getKey();
                for (Map.Entry<String, BlockstateJson> bsEntry :
                        new ArrayList<>(nsEntry.getValue().entrySet())) {
                    String blockName = bsEntry.getKey();
                    BlockstateJson bs = bsEntry.getValue();
                    Block block = Utilities.findBlock(namespace, blockName);
                    if (block == null) continue;
                    // Skip blocks already materialized by Models or handled in an earlier step
                    if (ModelRegistry.registeredBlockModels.containsKey(block)) continue;

                    registerResidentBlockModel(block, bs);
                }
            }

            // Step 3: process blocks in model_mappings (blocks without a blockstate)
            for (Map.Entry<String, ModelMappings> entry :
                    ModelManagerDataLoader.loadedMappings.entrySet()) {
                String namespace = entry.getKey();
                ModelMappings mappings = entry.getValue();

                if (mappings.blocks != null) {
                    for (Map.Entry<String, String> blockEntry : mappings.blocks.entrySet()) {
                        String key = blockEntry.getKey();
                        String blockName;

                        if (key.contains(":")) {
                            String[] parts = key.split(":", 2);
                            blockName = parts[0];
                        } else {
                            blockName = key;
                        }

                        Block block = Utilities.findBlock(namespace, blockName);
                        if (block == null) continue;
                        // Skip blocks already handled by blockstate
                        if (ModelRegistry.registeredBlockModels.containsKey(block)) continue;

                        if (mappings.state_mapping) {
                            // New-era state mapping: ID → blockstate name, reuse the loaded state definition
                            String[] ref = parseStateRef(namespace, blockEntry.getValue());
                            Map<String, BlockstateJson> nsStates =
                                    ModelManagerDataLoader.loadedBlockstates.get(ref[0]);
                            BlockstateJson bs = nsStates != null ? nsStates.get(ref[1]) : null;
                            if (bs == null) {
                                CatFrame.logger.warn("[VMM] state_mapping block '{}' -> unknown blockstate '{}:{}'",
                                        key, ref[0], ref[1]);
                                continue;
                            }
                            registerResidentBlockModel(block, bs);
                        } else {
                            // legacy: use a lazy single model — holds the modelPath and fetches from BakedModelCache at render time
                            ModelRegistry.registerBlockModel(block,
                                    new LazySingleBlockModel(blockEntry.getValue()));
                        }
                    }
                }
            }

            // Step 4: register item models

            // 4a: items/ ItemState decision tree (highest priority — includes ItemBlock)
            for (Map.Entry<String, Map<String, ItemStateNode>> nsEntry :
                    new ArrayList<>(ModelManagerDataLoader.loadedItemStates.entrySet())) {
                String namespace = nsEntry.getKey();
                for (Map.Entry<String, ItemStateNode> itemEntry : nsEntry.getValue().entrySet()) {
                    String itemName = itemEntry.getKey();
                    Item item = Utilities.findItem(namespace, itemName);
                    if (item == null) continue;
                    if (ModelRegistry.registeredItemModels.containsKey(item)) continue;

                    ItemStateModel ism = new ItemStateModel(itemEntry.getValue());
                    ModelRegistry.registeredItemModels.put(item, ism);
                    // Bridge: if the tree declares tints, register into the render-side TintRegistry
                    if (ism.hasAnyTint()) {
                        TintRegistry.registerItemTint(item, new ItemStateTintBridge(ism));
                    }
                    Set<String> nsOversized = ModelManagerDataLoader.loadedOversizedItems.get(namespace);
                    if (nsOversized != null && nsOversized.contains(itemName)) {
                        ModelRegistry.oversizedItems.add(item);
                    }
                    CatFrame.logger.debug("[VMM] Registered ItemState: {}:{}", namespace, itemName);
                }
            }

            // 4b: items in model_mappings (skip those already registered by ItemState; dual legacy / state mapping semantics)
            for (Map.Entry<String, ModelMappings> entry :
                    ModelManagerDataLoader.loadedMappings.entrySet()) {
                registerMappingItems(entry.getKey(), entry.getValue());
            }

            // 4c: IItemStateProvider (Tier 3 interface discovery — code-delivered decision trees)
            // Tier 3 interface discovery: only items that deliver a code-built tree are bound;
            // items without a tree rely on their items/{name}.json (4a) or stay on vanilla path.
            for (Map.Entry<Item, IItemStateProvider> entry : ModelManagerDataLoader.interfaceItemStates.entrySet()) {
                Item item = entry.getKey();
                if (ModelRegistry.registeredItemModels.containsKey(item)) continue;

                ItemStateNode root = entry.getValue().getItemStateRoot();
                if (root == null) continue;

                String registryName = Item.itemRegistry.getNameForObject(item);
                if (registryName == null) continue;

                ModelRegistry.registeredItemModels.put(item, new ItemStateModel(root));
                ModelRegistry.persistentItemModels.add(item);
                CatFrame.logger.debug("[VMM] Registered IItemState: {}", registryName);
            }

            // 4d: ITEM_MODEL component override (highest priority — mirrors the vanilla item_model component)
            applyItemModelOverrides(false);

            // Step 5: Forge IItemRenderer registration
            Set<Item> registered = new HashSet<>();
            int forgeRegistered = 0;

            for (Item item : ModelRegistry.registeredItemModels.keySet()) {
                if (registered.add(item)) {
                    MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
                    forgeRegistered++;
                }
            }
            for (Block block : ModelRegistry.registeredBlockModels.keySet()) {
                Item item = Item.getItemFromBlock(block);
                if (item != null && registered.add(item)) {
                    MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
                    forgeRegistered++;
                }
            }

            CatFrame.logger.info("VanillaModelManager: Registered {} block models, {} item models ({} Forge renderers)",
                    ModelRegistry.registeredBlockModels.size(),
                    ModelRegistry.registeredItemModels.size(), forgeRegistered);
        }

        /**
         * Incrementally rebuilds item models (called after the item atlas stitch).
         * <p>
         * Only non-persistent IItemState wrappers are recreated. Since lazy models are used,
         * no actual baking is needed — only the wrapper references in the registry are updated.
         */
        public static void registerItemModels() {
            // Remove non-persistent item models
            ModelRegistry.registeredItemModels.keySet()
                    .removeIf(item -> !ModelRegistry.persistentItemModels.contains(item));
            // Rebuild oversized flags from scratch (same as registerAllModels, drops stale entries)
            ModelRegistry.oversizedItems.clear();

            // Re-register ItemState decision trees (highest priority)
            for (Map.Entry<String, Map<String, ItemStateNode>> nsEntry :
                    ModelManagerDataLoader.loadedItemStates.entrySet()) {
                String namespace = nsEntry.getKey();
                for (Map.Entry<String, ItemStateNode> itemEntry : nsEntry.getValue().entrySet()) {
                    Item item = Utilities.findItem(namespace, itemEntry.getKey());
                    if (item == null) continue;
                    ItemStateModel ism = new ItemStateModel(itemEntry.getValue());
                    ModelRegistry.registeredItemModels.put(item, ism);
                    // Bridge: re-register the tint bridge on incremental rebuild (points to the new instance)
                    if (ism.hasAnyTint()) {
                        TintRegistry.registerItemTint(item, new ItemStateTintBridge(ism));
                    }
                    Set<String> nsOversized = ModelManagerDataLoader.loadedOversizedItems.get(namespace);
                    if (nsOversized != null && nsOversized.contains(itemEntry.getKey())) {
                        ModelRegistry.oversizedItems.add(item);
                    }
                }
            }

            // Re-register model_mappings items (skip those already registered by ItemState; dual legacy / state mapping semantics)
            for (Map.Entry<String, ModelMappings> entry :
                    ModelManagerDataLoader.loadedMappings.entrySet()) {
                registerMappingItems(entry.getKey(), entry.getValue());
            }

            // Re-register interface-discovered items (code-delivered trees only; skip persistent entries)
            for (Map.Entry<Item, IItemStateProvider> entry : ModelManagerDataLoader.interfaceItemStates.entrySet()) {
                Item item = entry.getKey();
                if (ModelRegistry.registeredItemModels.containsKey(item)) continue;

                ItemStateNode root = entry.getValue().getItemStateRoot();
                if (root == null) continue;

                String registryName = Item.itemRegistry.getNameForObject(item);
                if (registryName == null) continue;

                ModelRegistry.registeredItemModels.put(item, new ItemStateModel(root));
                ModelRegistry.persistentItemModels.add(item);
                MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
            }

            // ITEM_MODEL component override (highest priority — the incremental path re-asserts it too, preventing an items/ convention match from overriding it)
            applyItemModelOverrides(true);

            CatFrame.logger.info("VanillaModelManager: Re-registered {} item models (incremental, lazy)",
                    ModelRegistry.registeredItemModels.size());
        }

        /**
         * Registers a resident state model for a single block (Pane / Stairs / redirect /
         * regular variants-multipart branches).
         * <p>
         * Extracted from {@link #registerAllModels} Step 2 so the blockstate traversal and
         * the {@code state_mapping} model_mappings share the same registration logic.
         */
        private static void registerResidentBlockModel(Block block, BlockstateJson bs) {
            String blockId = Block.blockRegistry.getNameForObject(block);
            String ns = (blockId != null && blockId.contains(":"))
                    ? blockId.substring(0, blockId.indexOf(':')) : "minecraft";
            // Inheritance as declaration: for IBlockStateProvider blocks, base-class
            // fragments along the class hierarchy are materialized into a typed
            // definition here (idempotent, retried every rebuild so late registerBase
            // calls still land). No fragments → previous no-typed behavior. Runs before
            // the branch dispatch below — the plain branch reads this table.
            if (block instanceof IBlockStateProvider) {
                BlockStateInheritance.resolveAndRegister(block);
            }
            // Rotation angle validation is definition-independent and must run here too:
            // blocks without a typed BlockStateDefinition never reach
            // BlockstateKeyValidator.validate() (no-op when def == null), and this method
            // is the single choke point of every resident blockstate registration.
            BlockstateKeyValidator.validateRotations(bs, blockId);
            IMetadataBlockstateRedirect redirect = ModelManagerDataLoader.blockstateRedirects.get(block);

            // BlockPane: runtime connection multipart (per-face merge + AtlasGuard)
            if (block instanceof BlockPane) {
                ResidentStateModel.Builder pb = ResidentStateModel.builder(block)
                        .connectionMultipart()
                        .dynamic(VanillaBlockResolvers.PANE);
                if (redirect != null) {
                    pb.redirect(redirect, ns);
                } else {
                    pb.blockstate(bs);
                }
                ModelRegistry.registerBlockModel(block, pb.build());
                return;
            }

            // BlockStairs: runtime corner detection (facing/half/shape built by the resolver)
            if (block instanceof BlockStairs) {
                ModelRegistry.registerBlockModel(block, ResidentStateModel.builder(block)
                        .blockstate(bs)
                        .dynamic(VanillaBlockResolvers.STAIRS)
                        .build());
                return;
            }

            // Blockstate redirect: per-meta redirect to another blockstate
            if (redirect != null) {
                ResidentStateModel.Builder rb = ResidentStateModel.builder(block)
                        .redirect(redirect, ns);
                ModelRegistry.registerBlockModel(block, rb.build());
                return;
            }

            // Plain variants/multipart — re-apply the registered typed definition so resident
            // rebuilds keep property-driven matching instead of degrading to the
            // attribute-less fallback chain. null is a no-op (unchanged behavior).
            ResidentStateModel.Builder nb = ResidentStateModel.builder(block).blockstate(bs)
                    .stateDefinition(ModelRegistry.getStateDefinition(block));
            ModelRegistry.registerBlockModel(block, nb.build());
        }

        /**
         * Parses a state reference string {@code "ns:name"} or {@code "name"} (defaults to
         * the current namespace when no prefix is present).
         *
         * @return {@code [namespace, name]}
         */
        private static String[] parseStateRef(String defaultNs, String ref) {
            int sep = ref.indexOf(':');
            return sep >= 0
                    ? new String[]{ref.substring(0, sep), ref.substring(sep + 1)}
                    : new String[]{defaultNs, ref};
        }

        /**
         * Registers model_mappings.items with dual semantics, shared by the full and
         * incremental paths:
         * <ul>
         *   <li>{@code state_mapping=false} (legacy): the value is a model path → wrapped as a
         *       single-model {@link ItemStateModel}</li>
         *   <li>{@code state_mapping=true}: the value is an ItemState decision tree name →
         *       reuses the loaded tree; the tint bridge and {@code oversized_in_gui} flag take
         *       effect together with the referenced state</li>
         * </ul>
         * Items already registered by the items/ convention match are not overridden
         * (ItemState has higher priority).
         */
        private static void registerMappingItems(String namespace, ModelMappings mappings) {
            if (mappings.items == null) return;

            for (Map.Entry<String, String> itemEntry : mappings.items.entrySet()) {
                String key = itemEntry.getKey();
                String itemName;

                if (key.contains(":")) {
                    String[] parts = key.split(":", 2);
                    itemName = parts[0];
                } else {
                    itemName = key;
                }

                Item item = Utilities.findItem(namespace, itemName);
                if (item == null) continue;
                // Skip those already registered by ItemState
                if (ModelRegistry.registeredItemModels.containsKey(item)) continue;

                if (mappings.state_mapping) {
                    // New-era state mapping: ID → ItemState tree name, reuse the loaded definition
                    String[] ref = parseStateRef(namespace, itemEntry.getValue());
                    Map<String, ItemStateNode> nsStates = ModelManagerDataLoader.loadedItemStates.get(ref[0]);
                    ItemStateNode node = nsStates != null ? nsStates.get(ref[1]) : null;
                    if (node == null) {
                        CatFrame.logger.warn("[VMM] state_mapping item '{}' -> unknown item state '{}:{}'",
                                key, ref[0], ref[1]);
                        continue;
                    }
                    ItemStateModel ism = new ItemStateModel(node);
                    ModelRegistry.registeredItemModels.put(item, ism);
                    // Tint bridge and oversized flag follow the referenced state
                    if (ism.hasAnyTint()) {
                        TintRegistry.registerItemTint(item, new ItemStateTintBridge(ism));
                    }
                    Set<String> nsOversized = ModelManagerDataLoader.loadedOversizedItems.get(ref[0]);
                    if (nsOversized != null && nsOversized.contains(ref[1])) {
                        ModelRegistry.oversizedItems.add(item);
                    }
                } else {
                    // legacy: the value is a model path, wrapped as a single-model wrapper
                    ModelRegistry.registeredItemModels.put(item,
                            new ItemStateModel(itemEntry.getValue()));
                }
            }
        }

        /**
         * Applies the {@code ITEM_MODEL} component override (mirrors the vanilla item_model
         * component, highest priority).
         * <p>
         * Iterates all registered items; if an item carries the {@code ITEM_MODEL} default
         * component, the model mapping it points to takes precedence, overriding the
         * {@code items/{name}.json} matched by registry ID convention, and is marked
         * persistent. When a value exists but cannot be resolved, {@code builtin/missing} is
         * used (see {@link ModelRegistry#resolveItemModelOverride}).
         *
         * @param registerRenderer whether to also register the Forge {@code IItemRenderer}
         */
        private static void applyItemModelOverrides(boolean registerRenderer) {
            for (Object obj : Item.itemRegistry) {
                Item item = (Item) obj;
                ItemStateModel override = ModelRegistry.resolveItemModelOverride(item);
                if (override == null) continue;
                ModelRegistry.registeredItemModels.put(item, override);
                ModelRegistry.persistentItemModels.add(item);
                if (registerRenderer) {
                    MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
                }
                CatFrame.logger.debug("[VMM] Applied ITEM_MODEL override: {} -> {}",
                        Item.itemRegistry.getNameForObject(item), ModelRegistry.getItemModelOverride(item));
            }
        }

    }
}
