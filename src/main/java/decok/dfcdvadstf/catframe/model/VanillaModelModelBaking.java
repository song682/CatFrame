package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.IMetadataMapper;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import java.util.*;

/**
 * Model baking extracted from {@link VanillaModelManager}.
 * <p>
 * Responsible for orchestrating the baking process: resolving blockstate JSON
 * into metadata-keyed quad lists, caching, and auto-registering BlockStateModels.
 */
@SideOnly(Side.CLIENT)
public class VanillaModelModelBaking {

    /**
     * modelPath -> display transforms (from ModelJson.display).
     * Populated during bakeModel(), used when constructing ItemModelWrapper.
     */
    private static final Map<String, Map<String, ModelJson.DisplayTransform>> modelDisplayCache = new HashMap<>();

    /**
     * Item -> display transforms, tracked during item baking for auto-registration.
     */
    private static final Map<Item, Map<String, ModelJson.DisplayTransform>> itemDisplayTransforms = new HashMap<>();

    /**
     * Block -> first model path, tracked during block baking for display transform lookup.
     * Used when auto-registering ItemModelWrappers for blocks.
     */
    private static final Map<Block, String> blockModelPaths = new HashMap<>();

    static void bakeAllModels() {
        VanillaModelManager.bakedBlockModels.clear();
        VanillaModelManager.bakedItemModels.clear();
        VanillaModelManager.blockRotations.clear();
        VanillaModelManager.bakedModelCache.clear();
        blockModelPaths.clear();

        // Bake from blockstates
        for (Map.Entry<String, Map<String, BlockstateJson>> nsEntry : VanillaModelManager.loadedBlockstates.entrySet()) {
            String namespace = nsEntry.getKey();
            for (Map.Entry<String, BlockstateJson> bsEntry : nsEntry.getValue().entrySet()) {
                String blockName = bsEntry.getKey();
                BlockstateJson bs = bsEntry.getValue();
                Block block = VanillaModelUtil.findBlock(namespace, blockName);
                if (block != null) {
                    // Auto-register mapper from metadata_map.json if no function-based mapper exists
                    if (!VanillaModelManager.metadataMappers.containsKey(block)) {
                        IMetadataMapper jsonMapper = VanillaModelUtil.findMetadataMapEntry(namespace, blockName);
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

                    // Support "name:metadata" format, e.g., "log:1" -> block=log, meta=1
                    if (key.contains(":")) {
                        String[] parts = key.split(":", 2);
                        blockName = parts[0];
                        try {
                            meta = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            blockName = key; // Not a number after ':', treat whole thing as name
                        }
                    } else {
                        blockName = key;
                    }

                    Block block = VanillaModelUtil.findBlock(namespace, blockName);
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

                    // Support "name:damage" format, e.g., "dye:4" -> item=dye, damage=4
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

                    Item item = VanillaModelUtil.findItem(namespace, itemName);
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
            // Construct a simple metadata-based model that wraps damage->quads
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
            VanillaModelManager.registeredItemModels.put(item, new ItemModelWrapper(metaModel, display));
        }

        // Bake interface-discovered item models (Tier 2: IItemJsonModel)
        for (Map.Entry<Item, IItemJsonModel> entry : VanillaModelManager.interfaceItemModels.entrySet()) {
            Item item = entry.getKey();
            IItemJsonModel ijm = entry.getValue();
            // Skip if already registered (model_mappings or manual registration takes priority)
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

            // Create wrapper with handles() delegated to IItemJsonModel
            ItemModelWrapper wrapper = new ItemModelWrapper(
                    BlockStateModelPart.fromQuads(quads), display, ijm::handles);
            VanillaModelManager.registeredItemModels.put(item, wrapper);
            VanillaModelManager.persistentItemModels.add(item);
            CatFrame.logger.debug("[VMM] Baked IItemJsonModel: {} -> {}",
                    Item.itemRegistry.getNameForObject(item), modelPath);
        }

        CatFrame.logger.info("VanillaModelManager: Baked {} block models, {} item models, registered {} BlockStateModels, {} ItemModels",
                VanillaModelManager.bakedBlockModels.size(), VanillaModelManager.bakedItemModels.size(),
                VanillaModelManager.registeredBlockModels.size(), VanillaModelManager.registeredItemModels.size());

        // GTNHLib-style Forge IItemRenderer registration:
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
     * [W2] 增量更新：仅重新烘焙 item 模型，不触碰 block 模型。
     * <p>
     * 在 item atlas 缝合完成后调用，此时 item 纹理的 IIcon 已更新，
     * 需要清除 item 缓存并重新烘焙，但 block 模型不受影响。
     */
    static void rebuildItemModels() {
        // 清除 bakeModel 缓存：第一次 onTextureStitchPost(type=0) 时 item atlas 尚未缝合完成，
        // 缓存的 quads 中的 IIcon UV 数据是过期的。必须清除以强制用正确的 IIcon 重新烘焙。
        VanillaModelManager.bakedModelCache.clear();
        VanillaModelManager.bakedItemModels.clear();
        itemDisplayTransforms.clear();

        // 只清除非持久（自动生成的 ItemModelWrapper）的条目，保留手动注册的模型
        VanillaModelManager.registeredItemModels.keySet().removeIf(item -> !VanillaModelManager.persistentItemModels.contains(item));

        // 重新烘焙 item 模型（仅从 loadedMappings 中的 items 部分）
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

                Item item = VanillaModelUtil.findItem(namespace, itemName);
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

        // 重新注册 ItemModel 包装器
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
            VanillaModelManager.registeredItemModels.put(item, new ItemModelWrapper(metaModel, display));
            MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
        }

        CatFrame.logger.info("VanillaModelManager: Rebuilt {} item models (incremental)", VanillaModelManager.bakedItemModels.size());
    }

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

                    // [C1+W3] 旋转已在 bakeModel 中烘焙到 quad 顶点
                    List<BakedQuad> quads = bakeModel(variant.model, variant.x, variant.y);
                    if (quads != null && !quads.isEmpty()) {
                        metaMap.put(meta, quads);
                        rotMap.put(meta, 0);
                    }
                }
            } else {
                // Compat path: iterate variant entries directly, parse metadata from key
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

                    // [C1+W3] 旋转已在 bakeModel 中烘焙到 quad 顶点
                    List<BakedQuad> quads = bakeModel(variant.model, variant.x, variant.y);
                    if (quads != null) {
                        metaMap.put(meta, quads);
                        rotMap.put(meta, 0);
                    }
                }
                if (hasNumberKeys) {
                    CatFrame.logger.warn(
                            "VanillaModelManager: blockstate for '{}' uses deprecated metadata-number variant keys. "
                                    + "Consider registering an IMetadataMapper and switching to property keys (e.g. \"variant=granite\").",
                            Block.blockRegistry.getNameForObject(block));
                }
            }
        }

        if (bs.multipart != null) {
            if (mapper != null) {
                // Multipart with mapper: enumerate all metadata values, evaluate when conditions
                for (int meta = 0; meta < 16; meta++) {
                    Map<String, String> props = mapper.map(meta);
                    List<BakedQuad> allQuads = new ArrayList<>();

                    for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                        boolean applies = (mpc.when == null) || mpc.when.matches(props);
                        if (applies && mpc.apply != null) {
                            // [C1] 旋转已在 bakeModel 中烘焙，不再原地 applyYRotation
                            List<BakedQuad> partQuads = bakeModel(mpc.apply.model, mpc.apply.x, mpc.apply.y);
                            if (partQuads != null) {
                                allQuads.addAll(partQuads);
                            }
                        }
                    }

                    if (!allQuads.isEmpty()) {
                        metaMap.put(meta, allQuads);
                        // multipart 各部分的旋转已烘焙到顶点中，运行时不需要额外旋转
                        rotMap.put(meta, 0);
                    }
                }
            } else {
                // Compat: bake all unconditional parts for meta 0
                List<BakedQuad> allQuads = new ArrayList<>();
                for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                    if (mpc.when == null && mpc.apply != null) {
                        // [C1] 旋转已在 bakeModel 中烘焙，不再原地 applyYRotation
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

    /**
     * Parse metadata value from a variant key.
     * Supports: "normal" -> 0, "0" -> 0, "1" -> 1, "facing=north" -> property-based
     *
     * @deprecated pure-number variant keys are legacy; use an IMetadataMapper + property keys instead
     */
    @Deprecated
    private static int parseMetadataFromKey(String key) {
        if (key.equals("normal") || key.isEmpty()) return 0;
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            // Property-based key (e.g., "facing=north") - map to metadata based on order
            // For 1.7.10, we use simple metadata mapping
            return 0;
        }
    }

    /**
     * Check whether a variant key is a raw metadata number (legacy compat).
     */
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
     * Extract the first model path from a blockstate JSON, used for display transform lookup.
     * Checks variants first (first entry's model), then multipart (first unconditional apply).
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
     * Bake a model into quads with the given rotation (backward compat, rotationX=0).
     * Results are cached by {@code modelPath@rotationX@rotationY}.
     */
    static List<BakedQuad> bakeModel(String modelPath, int rotationY) {
        return bakeModel(modelPath, 0, rotationY);
    }

    /**
     * Bake a model into quads with X and Y rotation.
     * [C1 修复] 旋转在烘焙阶段应用到 quad 上并缓存，返回深拷贝防止缓存污染。
     * [W3] 新增 rotationX 参数支持 blockstate 中的 x 旋转字段。
     * Results are cached by {@code modelPath@rotationX@rotationY}.
     * <p>
     * Package-private for access by {@link VanillaModelRegistry}.
     */
    static List<BakedQuad> bakeModel(String modelPath, int rotationX, int rotationY) {
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

        // Cache display transforms (indexed by modelPath, not cacheKey — rotation doesn't affect display)
        if (resolved.display != null && !modelDisplayCache.containsKey(modelPath)) {
            modelDisplayCache.put(modelPath, resolved.display);
        }

        // Delegate to ModelBaker with resolved model (avoids double-resolve)
        BlockStateModelPart part = ModelBaker.bake(resolved, rotationX, rotationY, modelPath);
        if (part == null) return null;

        List<BakedQuad> quads = part.getAllQuads();
        VanillaModelManager.bakedModelCache.put(cacheKey, quads);
        CatFrame.logger.info("[VMM] bakeModel: baked '{}' | quads={}", cacheKey, quads.size());
        return quads;
    }
}
