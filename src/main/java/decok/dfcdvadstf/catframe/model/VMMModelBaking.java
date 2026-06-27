package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.ModernItem;
import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.MetadataBlockModel;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 模型烘焙：烘焙管线、烘焙缓存管理。
 * <p>
 * 从 {@link VanillaModelManager.ModelBaking} 提取，职责不变。
 */
public class VMMModelBaking {

    // ==================== 烘焙缓存 ====================

    /**
     * modelPath -> display transforms (from ModelJson.display).
     */
    private static final Map<String, Map<String, ModelJson.DisplayTransform>> modelDisplayCache = new HashMap<>();

    /**
     * Item -> display transforms, tracked during item baking.
     */
    private static final Map<Item, Map<String, ModelJson.DisplayTransform>> itemDisplayTransforms = new HashMap<>();

    /**
     * Block -> first model path, tracked during block baking for display transform lookup.
     */
    private static final Map<Block, String> blockModelPaths = new HashMap<>();

    /**
     * Redirect target blockstate cache (separate from loadedBlockstates to avoid concurrent modification).
     */
    private static final Map<String, BlockstateJson> redirectCache = new HashMap<>();

    // ==================== 批量烘焙入口 ====================

    /**
     * Bake all models from loaded blockstates and mappings.
     */
    public static void bakeAllModels() {
        VanillaModelManager.bakedBlockModels.clear();
        VanillaModelManager.bakedItemModels.clear();
        VanillaModelManager.blockRotations.clear();
        VanillaModelManager.bakedModelCache.clear();
        blockModelPaths.clear();

        // Bake from blockstates (snapshot iteration to avoid CME from lazy loading)
        for (Map.Entry<String, Map<String, BlockstateJson>> nsEntry :
                new ArrayList<>(VanillaModelManager.loadedBlockstates.entrySet())) {
            String namespace = nsEntry.getKey();
            for (Map.Entry<String, BlockstateJson> bsEntry :
                    new ArrayList<>(nsEntry.getValue().entrySet())) {
                String blockName = bsEntry.getKey();
                BlockstateJson bs = bsEntry.getValue();
                Block block = VanillaModelManager.Utilities.findBlock(namespace, blockName);
                if (block != null) {
                    // Auto-register mapper from metadata_map.json if no function-based mapper exists
                    if (!VanillaModelManager.metadataMappers.containsKey(block)) {
                        IMetadataMapper jsonMapper = VanillaModelManager.Utilities.findMetadataMapEntry(namespace, blockName);
                        if (jsonMapper != null) {
                            VanillaModelManager.metadataMappers.put(block, jsonMapper);
                            CatFrame.logger.debug("Auto-registered metadata mapper from metadata_map.json for {}:{}", namespace, blockName);
                        }
                    }
                    bakeBlockstateForBlock(block, bs);
                }
            }
        }

        // Bake from model_mappings (blocks that don't have blockstates)
        for (Map.Entry<String, VanillaModelManager.ModelMappings> entry : VanillaModelManager.loadedMappings.entrySet()) {
            String namespace = entry.getKey();
            VanillaModelManager.ModelMappings mappings = entry.getValue();

            if (mappings.blocks != null) {
                for (Map.Entry<String, String> blockEntry : mappings.blocks.entrySet()) {
                    String key = blockEntry.getKey();
                    String blockName;
                    int meta = -1; // -1 means "all metadata" (default slot 0)

                    if (key.contains(":")) {
                        String[] parts = key.split(":", 2);
                        blockName = parts[0];
                        try {
                            meta = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            blockName = key;
                        }
                    } else {
                        blockName = key;
                    }

                    Block block = VanillaModelManager.Utilities.findBlock(namespace, blockName);
                    if (block == null) continue;

                    // Skip if blockstate already handles this block entirely
                    if (meta == -1 && VanillaModelManager.bakedBlockModels.containsKey(block)) continue;

                    List<BakedQuad> quads = bakeModel(blockEntry.getValue(), 0);
                    if (quads == null) continue;

                    // Track model path for display transform lookup
                    if (!blockModelPaths.containsKey(block)) {
                        blockModelPaths.put(block, blockEntry.getValue());
                    }

                    Map<Integer, List<BakedQuad>> metaMap = VanillaModelManager.bakedBlockModels.get(block);
                    if (metaMap == null) {
                        metaMap = new HashMap<>();
                        VanillaModelManager.bakedBlockModels.put(block, metaMap);
                    }

                    int targetMeta = (meta == -1) ? 0 : meta;
                    if (!metaMap.containsKey(targetMeta)) {
                        metaMap.put(targetMeta, quads);
                        CatFrame.logger.debug("Baked block from mappings: {} meta={}", blockName, targetMeta);
                    }
                }
            }

            if (mappings.items != null) {
                for (Map.Entry<String, String> itemEntry : mappings.items.entrySet()) {
                    String key = itemEntry.getKey();
                    String itemName;
                    int damage = -1;

                    if (key.contains(":")) {
                        String[] parts = key.split(":", 2);
                        itemName = parts[0];
                        try {
                            damage = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            itemName = key;
                        }
                    } else {
                        itemName = key;
                    }

                    Item item = VanillaModelManager.Utilities.findItem(namespace, itemName);
                    if (item == null) {
                        CatFrame.logger.debug("[VMM] bakeAllModels: item not found: {}:{}", namespace, itemName);
                        continue;
                    }

                    List<BakedQuad> quads = bakeModel(itemEntry.getValue(), 0);
                    if (quads == null) {
                        CatFrame.logger.warn("[VMM] bakeAllModels: failed to bake model '{}' for item '{}'",
                                itemEntry.getValue(), itemName);
                        continue;
                    }

                    // Track display transforms for this item
                    Map<String, ModelJson.DisplayTransform> itemDisplay = modelDisplayCache.get(itemEntry.getValue());
                    if (itemDisplay != null) {
                        itemDisplayTransforms.put(item, itemDisplay);
                    }

                    Map<Integer, List<BakedQuad>> damageMap = VanillaModelManager.bakedItemModels.get(item);
                    if (damageMap == null) {
                        damageMap = new HashMap<>();
                        VanillaModelManager.bakedItemModels.put(item, damageMap);
                    }

                    int targetDamage = (damage == -1) ? 0 : damage;
                    if (!damageMap.containsKey(targetDamage)) {
                        damageMap.put(targetDamage, quads);
                        CatFrame.logger.debug("Baked item from mappings: {} damage={}", itemName, targetDamage);
                    }
                }
            }
        }

        // Auto-register BlockStateModel wrappers from the old baked data (transition path)
        for (Map.Entry<Block, Map<Integer, List<BakedQuad>>> entry : VanillaModelManager.bakedBlockModels.entrySet()) {
            Block block = entry.getKey();
            Map<Integer, List<BakedQuad>> metaMap = entry.getValue();
            Map<Integer, BlockStateModelPart> partMap = new HashMap<>();
            for (Map.Entry<Integer, List<BakedQuad>> me : metaMap.entrySet()) {
                partMap.put(me.getKey(), BlockStateModelPart.fromQuads(me.getValue()));
            }
            BlockStateModelPart fallback = partMap.get(0);
            if (fallback == null) {
                fallback = partMap.values().stream().findFirst().orElse(BlockStateModelPart.empty());
            }
            VanillaModelManager.registeredBlockModels.put(block, new MetadataBlockModel(partMap, fallback));

            // Also copy rotations
            Map<Integer, Integer> rotMap = VanillaModelManager.blockRotations.get(block);
            if (rotMap != null) {
                Map<Integer, Integer> newRot = new HashMap<>(rotMap);
                VanillaModelManager.registeredBlockRotations.put(block, newRot);
            }
        }

        // Auto-register ItemModel wrappers from old baked data (item-only models, non-block)
        for (Map.Entry<Item, Map<Integer, List<BakedQuad>>> entry : VanillaModelManager.bakedItemModels.entrySet()) {
            Item item = entry.getKey();
            Map<Integer, List<BakedQuad>> damageMap = entry.getValue();
            Map<Integer, BlockStateModelPart> partMap = new HashMap<>();
            for (Map.Entry<Integer, List<BakedQuad>> de : damageMap.entrySet()) {
                partMap.put(de.getKey(), BlockStateModelPart.fromQuads(de.getValue()));
            }
            BlockStateModelPart fallback = partMap.get(0);
            if (fallback == null) {
                fallback = partMap.values().stream().findFirst().orElse(BlockStateModelPart.empty());
            }
            MetadataBlockModel metaModel = new MetadataBlockModel(partMap, fallback);
            Map<String, ModelJson.DisplayTransform> display = itemDisplayTransforms.get(item);
            VanillaModelManager.registeredItemModels.put(item, new decok.dfcdvadstf.catframe.model.ItemModelWrapper(metaModel, display));
        }

        // Bake interface-discovered item models (Tier 2: IItemJsonModel)
        for (Map.Entry<Item, IItemJsonModel> entry : VanillaModelManager.interfaceItemModels.entrySet()) {
            Item item = entry.getKey();
            IItemJsonModel ijm = entry.getValue();
            if (VanillaModelManager.registeredItemModels.containsKey(item)) continue;

            String modelPath = ijm.getModelPath();
            List<BakedQuad> quads = bakeModel(modelPath, 0);
            if (quads == null || quads.isEmpty()) {
                CatFrame.logger.warn("[VMM] IItemJsonModel bake failed for item {} (model: {})",
                        Item.itemRegistry.getNameForObject(item), modelPath);
                continue;
            }

            Map<String, ModelJson.DisplayTransform> display = itemDisplayTransforms.get(item);
            if (display == null) {
                ModelJson resolved = ModelResolver.resolve(modelPath);
                if (resolved != null) display = resolved.display;
            }

            decok.dfcdvadstf.catframe.model.ItemModelWrapper wrapper = new decok.dfcdvadstf.catframe.model.ItemModelWrapper(
                    BlockStateModelPart.fromQuads(quads), display, ijm::handles);
            VanillaModelManager.registeredItemModels.put(item, wrapper);
            VanillaModelManager.persistentItemModels.add(item);
            CatFrame.logger.debug("[VMM] Baked IItemJsonModel: {} -> {}",
                    Item.itemRegistry.getNameForObject(item), modelPath);
        }

        CatFrame.logger.info("VanillaModelManager: Baked {} block models, {} item models, registered {} BlockStateModels, {} ItemModels",
                VanillaModelManager.bakedBlockModels.size(), VanillaModelManager.bakedItemModels.size(),
                VanillaModelManager.registeredBlockModels.size(), VanillaModelManager.registeredItemModels.size());

        // GTNHLib-style Forge IItemRenderer registration
        java.util.Set<Item> registered = new java.util.HashSet<>();
        int forgeRegistered = 0;

        for (Item item : VanillaModelManager.registeredItemModels.keySet()) {
            if (registered.add(item)) {
                MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
                forgeRegistered++;
            }
        }
        for (Block block : VanillaModelManager.registeredBlockModels.keySet()) {
            Item item = Item.getItemFromBlock(block);
            if (item != null && registered.add(item)) {
                MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
                forgeRegistered++;
            }
        }
        for (Block block : VanillaModelManager.bakedBlockModels.keySet()) {
            if (!VanillaModelManager.registeredBlockModels.containsKey(block)) {
                Item item = Item.getItemFromBlock(block);
                if (item != null && registered.add(item)) {
                    MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
                    forgeRegistered++;
                }
            }
        }
        CatFrame.logger.info("VanillaModelManager: Registered {} entries with Forge IItemRenderer",
                forgeRegistered);
    }

    /**
     * Incrementally rebuild only item models (after item atlas stitch).
     */
    public static void rebuildItemModels() {
        VanillaModelManager.bakedModelCache.clear();
        VanillaModelManager.bakedItemModels.clear();
        itemDisplayTransforms.clear();

        // Remove non-persistent ItemModelWrappers, keep manually registered models
        VanillaModelManager.registeredItemModels.keySet().removeIf(item -> !VanillaModelManager.persistentItemModels.contains(item));

        // Re-bake item models (from loadedMappings items only)
        for (Map.Entry<String, VanillaModelManager.ModelMappings> entry : VanillaModelManager.loadedMappings.entrySet()) {
            String namespace = entry.getKey();
            VanillaModelManager.ModelMappings mappings = entry.getValue();

            if (mappings.items == null) continue;

            for (Map.Entry<String, String> itemEntry : mappings.items.entrySet()) {
                String key = itemEntry.getKey();
                String itemName;
                int damage = -1;

                if (key.contains(":")) {
                    String[] parts = key.split(":", 2);
                    itemName = parts[0];
                    try {
                        damage = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        itemName = key;
                    }
                } else {
                    itemName = key;
                }

                Item item = VanillaModelManager.Utilities.findItem(namespace, itemName);
                if (item == null) continue;

                List<BakedQuad> quads = bakeModel(itemEntry.getValue(), 0);
                if (quads == null) continue;

                Map<String, ModelJson.DisplayTransform> itemDisplay = modelDisplayCache.get(itemEntry.getValue());
                if (itemDisplay != null) {
                    itemDisplayTransforms.put(item, itemDisplay);
                }

                Map<Integer, List<BakedQuad>> damageMap = VanillaModelManager.bakedItemModels.get(item);
                if (damageMap == null) {
                    damageMap = new HashMap<>();
                    VanillaModelManager.bakedItemModels.put(item, damageMap);
                }
                int targetDamage = (damage == -1) ? 0 : damage;
                damageMap.put(targetDamage, quads);
            }
        }

        // Re-register ItemModel wrappers
        for (Map.Entry<Item, Map<Integer, List<BakedQuad>>> entry : VanillaModelManager.bakedItemModels.entrySet()) {
            Item item = entry.getKey();
            Map<Integer, List<BakedQuad>> damageMap = entry.getValue();
            Map<Integer, BlockStateModelPart> partMap = new HashMap<>();
            for (Map.Entry<Integer, List<BakedQuad>> de : damageMap.entrySet()) {
                partMap.put(de.getKey(), BlockStateModelPart.fromQuads(de.getValue()));
            }
            BlockStateModelPart fallback = partMap.get(0);
            if (fallback == null) {
                fallback = partMap.values().stream().findFirst().orElse(BlockStateModelPart.empty());
            }
            MetadataBlockModel metaModel = new MetadataBlockModel(partMap, fallback);
            Map<String, ModelJson.DisplayTransform> display = itemDisplayTransforms.get(item);
            VanillaModelManager.registeredItemModels.put(item, new decok.dfcdvadstf.catframe.model.ItemModelWrapper(metaModel, display));
            MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
        }

        CatFrame.logger.info("VanillaModelManager: Rebuilt {} item models (incremental)", VanillaModelManager.bakedItemModels.size());
    }

    // ==================== Blockstate 烘焙 ====================

    private static void bakeBlockstateForBlock(Block block, BlockstateJson bs) {
        Map<Integer, List<BakedQuad>> metaMap = new HashMap<>();
        Map<Integer, Integer> rotMap = new HashMap<>();

        // --- Track first model path for display transform lookup ---
        if (!blockModelPaths.containsKey(block)) {
            String path = findFirstModelPath(bs);
            if (path != null) {
                blockModelPaths.put(block, path);
            }
        }

        // --- Get the effective mapper (function-based takes priority) ---
        IMetadataMapper mapper = VanillaModelManager.metadataMappers.get(block);

        // --- BlockPane: metadata does NOT encode connections in 1.7.10 → use runtime model ---
        if (block instanceof BlockPane) {
            IMetadataBlockstateRedirect redirect = VanillaModelManager.blockstateRedirects.get(block);
            String blockId = Block.blockRegistry.getNameForObject(block);
            String ns = blockId.contains(":") ? blockId.substring(0, blockId.indexOf(':')) : "minecraft";

            if (redirect != null) {
                // Redirect 模式（染色玻璃板等）
                VanillaModelRegistry.registerBlockModel(block,
                        new PaneMultipartRedirectModel(block, redirect, ns, false));
            } else {
                // 直接模式（无色玻璃板等）
                VanillaModelRegistry.registerBlockModel(block,
                        new PaneMultipartRedirectModel(block, bs, false));
            }
            return;
        }

        // --- Redirect check: delegate per-metadata to separate blockstate files ---
        IMetadataBlockstateRedirect redirect = VanillaModelManager.blockstateRedirects.get(block);
        if (redirect != null) {
            String blockId = Block.blockRegistry.getNameForObject(block);
            String ns = blockId.contains(":") ? blockId.substring(0, blockId.indexOf(':')) : "minecraft";

            for (int meta = 0; meta < 16; meta++) {
                String targetName = redirect.redirect(meta);
                if (targetName == null) continue;

                // Load target blockstate (lazy-load if needed)
                Map<String, BlockstateJson> nsMap = VanillaModelManager.loadedBlockstates.get(ns);
                BlockstateJson targetBs = (nsMap != null) ? nsMap.get(targetName) : null;
                if (targetBs == null) {
                    String cacheKey = ns + ":" + targetName;
                    targetBs = redirectCache.get(cacheKey);
                    if (targetBs == null) {
                        targetBs = VMMDataLoader.loadSingleBlockstate(ns, targetName);
                        if (targetBs != null) {
                            redirectCache.put(cacheKey, targetBs);
                        }
                    }
                }
                if (targetBs == null) continue;

                // Use the original block's mapper to get property values for multipart matching
                if (mapper != null && targetBs.multipart != null) {
                    Map<String, String> props = mapper.map(meta);
                    List<BakedQuad> allQuads = new ArrayList<>();
                    for (BlockstateJson.MultipartCase mpc : targetBs.multipart) {
                        boolean applies = (mpc.when == null) || mpc.when.matches(props);
                        if (applies && mpc.apply != null) {
                            List<BakedQuad> partQuads = bakeModel(mpc.apply.model, mpc.apply.x, mpc.apply.y);
                            if (partQuads != null) allQuads.addAll(partQuads);
                        }
                    }
                    if (!allQuads.isEmpty()) {
                        metaMap.put(meta, allQuads);
                        rotMap.put(meta, 0);
                    }
                }
            }

            if (!metaMap.isEmpty()) {
                VanillaModelManager.bakedBlockModels.put(block, metaMap);
                VanillaModelManager.blockRotations.put(block, rotMap);
            }
            return; // EARLY RETURN — redirect handled
        }

        if (bs.variants != null) {
            if (mapper != null) {
                // 1.16.5 path: enumerate metadata, convert via mapper, match property keys
                for (int meta = 0; meta < 16; meta++) {
                    Map<String, String> props = mapper.map(meta);
                    String variantKey = VanillaRenderDispatcher.buildVariantKey(props);
                    BlockstateJson.VariantEntry varEntry = bs.variants.get(variantKey);
                    if (varEntry == null) {
                        varEntry = bs.variants.get("normal");
                    }
                    if (varEntry == null) continue;

                    int seed = meta * 31;
                    BlockstateJson.Variant variant = varEntry.getVariant(seed);
                    if (variant == null || variant.model == null) continue;

                    List<BakedQuad> quads = bakeModel(variant.model, variant.x, variant.y);
                    if (quads != null && !quads.isEmpty()) {
                        metaMap.put(meta, quads);
                        rotMap.put(meta, 0);
                    }
                }
            } else if (hasMetaVariantKeys(bs.variants)) {
                // "meta=N" fallback: direct metadata-number to model mapping
                // Used when no IMetadataMapper is registered but blockstate defines meta=N keys
                for (int meta = 0; meta < 16; meta++) {
                    BlockstateJson.VariantEntry varEntry = bs.variants.get("meta=" + meta);
                    if (varEntry == null) continue;

                    BlockstateJson.Variant variant = varEntry.getVariant(0);
                    if (variant == null || variant.model == null) continue;

                    List<BakedQuad> quads = bakeModel(variant.model, variant.x, variant.y);
                    if (quads != null) {
                        metaMap.put(meta, quads);
                        rotMap.put(meta, 0);
                    }
                }
            } else {
                // Compat path: iterate variant entries directly, parse metadata from bare number key
                boolean hasNumberKeys = false;
                for (Map.Entry<String, BlockstateJson.VariantEntry> entry : bs.variants.entrySet()) {
                    String key = entry.getKey();
                    BlockstateJson.VariantEntry varEntry = entry.getValue();

                    int meta = parseMetadataFromKey(key);
                    if (isMetadataNumberKey(key)) {
                        hasNumberKeys = true;
                    }

                    BlockstateJson.Variant variant = varEntry.getVariant(0);
                    if (variant == null || variant.model == null) continue;

                    List<BakedQuad> quads = bakeModel(variant.model, variant.x, variant.y);
                    if (quads != null) {
                        metaMap.put(meta, quads);
                        rotMap.put(meta, 0);
                    }
                }
                if (hasNumberKeys) {
                    CatFrame.logger.warn(
                            "VanillaModelManager: blockstate for '{}' uses deprecated metadata-number variant keys. "
                                    + "Consider registering an IMetadataMapper and switching to property keys.",
                            Block.blockRegistry.getNameForObject(block));
                }
            }
        }

        if (bs.multipart != null) {
            if (mapper != null) {
                for (int meta = 0; meta < 16; meta++) {
                    Map<String, String> props = mapper.map(meta);
                    List<BakedQuad> allQuads = new ArrayList<>();

                    for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                        boolean applies = (mpc.when == null) || mpc.when.matches(props);
                        if (applies && mpc.apply != null) {
                            List<BakedQuad> partQuads = bakeModel(mpc.apply.model, mpc.apply.x, mpc.apply.y);
                            if (partQuads != null) {
                                allQuads.addAll(partQuads);
                            }
                        }
                    }

                    if (!allQuads.isEmpty()) {
                        metaMap.put(meta, allQuads);
                        rotMap.put(meta, 0);
                    }
                }
            } else {
                List<BakedQuad> allQuads = new ArrayList<>();
                for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                    if (mpc.when == null && mpc.apply != null) {
                        List<BakedQuad> partQuads = bakeModel(mpc.apply.model, mpc.apply.x, mpc.apply.y);
                        if (partQuads != null) {
                            allQuads.addAll(partQuads);
                        }
                    }
                }
                if (!allQuads.isEmpty()) {
                    metaMap.put(0, allQuads);
                }
            }
        }

        if (!metaMap.isEmpty()) {
            VanillaModelManager.bakedBlockModels.put(block, metaMap);
            VanillaModelManager.blockRotations.put(block, rotMap);
        }
    }

    // ==================== 单模型烘焙 ====================

    /**
     * Bake a model into quads with the given rotation.
     */
    @Nullable
    public static List<BakedQuad> bakeModel(String modelPath, int rotationY) {
        return bakeModel(modelPath, 0, rotationY);
    }

    /**
     * Bake a model into quads with X and Y rotation.
     */
    @Nullable
    public static List<BakedQuad> bakeModel(String modelPath, int rotationX, int rotationY) {
        if (modelPath == null) return null;

        String cacheKey = modelPath + "@" + rotationX + "@" + rotationY;
        List<BakedQuad> cached = VanillaModelManager.bakedModelCache.get(cacheKey);
        if (cached != null) {
            CatFrame.logger.debug("[VMM] bakeModel: cache HIT for {} | quads={}", cacheKey, cached.size());
            return cached;
        }

        // Resolve to update modelDisplayCache (ModelBaker 不跟踪 display transforms)
        ModelJson resolved = ModelResolver.resolve(modelPath);
        if (resolved == null) {
            CatFrame.logger.warn("[VMM] bakeModel: ModelResolver.resolve({}) returned null", modelPath);
            return null;
        }

        // Cache display transforms (indexed by modelPath, not cacheKey)
        if (resolved.display != null && !modelDisplayCache.containsKey(modelPath)) {
            modelDisplayCache.put(modelPath, resolved.display);
        }

        // Delegate to ModelBaker with resolved model
        BlockStateModelPart part = ModelBaker.bake(resolved, rotationX, rotationY, modelPath);
        if (part == null) return null;

        List<BakedQuad> quads = part.getAllQuads();
        VanillaModelManager.bakedModelCache.put(cacheKey, quads);
        CatFrame.logger.info("[VMM] bakeModel: baked '{}' | quads={}", cacheKey, quads.size());
        return quads;
    }

    // ==================== 辅助方法 ====================

    /**
     * Extract the first model path from a blockstate JSON.
     */
    private static String findFirstModelPath(BlockstateJson bs) {
        if (bs.variants != null) {
            for (BlockstateJson.VariantEntry ve : bs.variants.values()) {
                if (ve.isArray()) {
                    if (ve.list != null && !ve.list.isEmpty() && ve.list.get(0).model != null) {
                        return ve.list.get(0).model;
                    }
                } else if (ve.single != null && ve.single.model != null) {
                    return ve.single.model;
                }
            }
        }
        if (bs.multipart != null) {
            for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                if (mpc.apply != null && mpc.apply.model != null) {
                    return mpc.apply.model;
                }
            }
        }
        return null;
    }

    /**
     * Check if the variants map contains any "meta=N" keys.
     */
    private static boolean hasMetaVariantKeys(Map<String, BlockstateJson.VariantEntry> variants) {
        if (variants == null) return false;
        for (String key : variants.keySet()) {
            if (key != null && key.startsWith("meta=")) {
                try {
                    Integer.parseInt(key.substring(5));
                    return true;
                } catch (NumberFormatException ignored) { }
            }
        }
        return false;
    }

    @Deprecated
    private static int parseMetadataFromKey(String key) {
        if (key.equals("normal") || key.isEmpty()) return 0;
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isMetadataNumberKey(String key) {
        if (key.equals("normal") || key.isEmpty()) return false;
        try {
            Integer.parseInt(key);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 仅供 {@link VanillaModelManager.ModelRegistration#autoDiscoverItemModel} 使用。
     */
    @Nullable
    static Map<String, ModelJson.DisplayTransform> getModelDisplay(String modelPath) {
        return modelDisplayCache.get(modelPath);
    }
}
