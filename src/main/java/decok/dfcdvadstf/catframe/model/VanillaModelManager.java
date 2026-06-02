package decok.dfcdvadstf.catframe.model;

import com.google.gson.Gson;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Manages vanilla block/item model overrides using JSON model files.
 * Supports two mapping approaches:
 * 1. Blockstates: assets/{namespace}/blockstates/{name}.json (1.8+ format with variants/multipart)
 * 2. Model Mappings: assets/{namespace}/model_mappings.json (simple key-value block/item -> model path)
 *
 * Other mods can register their namespace to participate in model loading.
 */
@SideOnly(Side.CLIENT)
public class VanillaModelManager {
  private static final Gson blockstateGson = BlockstateJson.createGson();
  private static final Gson gson = new Gson();

  // ==================== Block Models ====================

  /** Block -> metadata -> list of baked quads */
  private static final Map<Block, Map<Integer, List<BakedQuad>>> bakedBlockModels = new HashMap<>();
  /** Block -> metadata -> Y rotation degrees */
  private static final Map<Block, Map<Integer, Integer>> blockRotations = new HashMap<>();

  // ==================== IBlockStateProvider Registry ====================

  /** Registered mod blocks using IBlockStateProvider */
  private static final List<Block> registeredStateBlocks = new ArrayList<>();
  /** Block -> loaded BlockstateJson */
  private static final Map<Block, BlockstateJson> stateBlockData = new HashMap<>();

  // ==================== Metadata → Properties Mapper Registry ====================

  /** Block -> metadata-to-properties mapper for vanilla blocks */
  private static final Map<Block, IMetadataMapper> metadataMappers = new HashMap<>();

  // ==================== Model Bake Cache ====================

  /** Cache key "modelPath@rotY" -> baked quads. Shared by bake path and dynamic path. */
  private static final Map<String, List<BakedQuad>> bakedModelCache = new HashMap<>();

  // ==================== Item Models ====================

  /** Item -> damage -> list of baked quads */
  private static final Map<Item, Map<Integer, List<BakedQuad>>> bakedItemModels = new HashMap<>();

  // ==================== Texture Tracking ====================

  /** All texture paths that need registration */
  private static final Set<String> pendingTextures = new LinkedHashSet<>();
  /** Texture path -> IIcon after stitching */
  private static final Map<String, IIcon> textureIcons = new HashMap<>();

  // ==================== Loaded Data ====================

  /** namespace -> blockName -> BlockstateJson */
  private static final Map<String, Map<String, BlockstateJson>> loadedBlockstates = new HashMap<>();
  /** namespace -> ModelMappings (blocks + items) */
  private static final Map<String, ModelMappings> loadedMappings = new HashMap<>();
  /** namespace -> blockName -> Map<metadata, Map<property, value>> (from metadata_map.json) */
  private static final Map<String, Map<String, Map<Integer, Map<String, String>>>> loadedMetadataMaps = new HashMap<>();
  /** Registered namespaces */
  private static final List<String> namespaces = new ArrayList<>();

  private static boolean initialized = false;

  // ==================== Model Mappings Data Class ====================

  public static class ModelMappings {
    public Map<String, String> blocks;
    public Map<String, String> items;
  }

  // ==================== Initialization ====================

  /**
   * Initialize the model manager. Call during preInit.
   * Scans all registered namespaces for blockstates and model_mappings.
   */
  public static void init() {
    if (initialized) return;
    initialized = true;

    // Always include minecraft namespace
    registerNamespace("minecraft");

    CatFrame.logger.info("VanillaModelManager: Initializing...");

    for (String namespace : namespaces) {
      loadNamespace(namespace);
    }

    // Load blockstates for all registered IBlockStateProvider blocks
    for (Block block : registeredStateBlocks) {
      loadStateProviderBlock(block);
    }

    CatFrame.logger.info("VanillaModelManager: Loaded {} namespaces, {} state-blocks, {} textures pending",
        namespaces.size(), registeredStateBlocks.size(), pendingTextures.size());
  }

  /**
   * Register a namespace for model loading.
   * Other mods should call this in preInit to participate.
   */
  public static void registerNamespace(String namespace) {
    if (!namespaces.contains(namespace)) {
      namespaces.add(namespace);
      ModelResolver.registerNamespace(namespace);
    }
  }

  /**
   * Register a block that implements IBlockStateProvider for blockstate-driven rendering.
   * Call this during preInit (before init() completes).
   *
   * The block's blockstate JSON will be loaded from:
   *   assets/{namespace}/blockstates/{name}.json
   *
   * On each render, the block's getStateProperties() is called to determine which
   * variant to use.
   *
   * @param block a Block that implements IBlockStateProvider
   * @throws IllegalArgumentException if block does not implement IBlockStateProvider
   */
  public static void registerBlock(Block block) {
    if (!(block instanceof IBlockStateProvider)) {
      throw new IllegalArgumentException("Block must implement IBlockStateProvider: " + block.getClass().getName());
    }
    if (!registeredStateBlocks.contains(block)) {
      registeredStateBlocks.add(block);
      // Ensure the namespace is registered
      String ns = ((IBlockStateProvider) block).getBlockstateNamespace();
      registerNamespace(ns);

      // If already initialized, load immediately
      if (initialized) {
        loadStateProviderBlock(block);
      }
    }
  }

  /**
   * Register a metadata-to-properties mapper for a vanilla block.
   * This allows blockstate JSON files to use 1.16.5-style property keys
   * (e.g. {@code "variant=granite"}) instead of raw metadata numbers.
   *
   * <p>The mapper is called during baking — for each metadata value 0–15,
   * the returned property map is used to look up the variant entry in the
   * blockstate JSON.  Function-based registration always takes priority
   * over {@code metadata_map.json} entries.
   *
   * <p>This has no effect on blocks that implement {@link IBlockStateProvider}.
   *
   * @param block  the vanilla block (must NOT be an {@link IBlockStateProvider})
   * @param mapper function that converts metadata to a property map
   */
  public static void registerMetadataMapping(Block block, IMetadataMapper mapper) {
    if (mapper == null) {
      CatFrame.logger.warn("VanillaModelManager: registerMetadataMapping called with null mapper for {}", block);
      return;
    }
    if (!metadataMappers.containsKey(block)) {
      metadataMappers.put(block, mapper);
      CatFrame.logger.debug("VanillaModelManager: registered metadata mapper for {}", block);
    }
  }

  /**
   * Load blockstate data for a registered IBlockStateProvider block.
   */
  private static void loadStateProviderBlock(Block block) {
    IBlockStateProvider provider = (IBlockStateProvider) block;
    String namespace = provider.getBlockstateNamespace();
    String name = provider.getBlockstateName();

    BlockstateJson bs = loadSingleBlockstate(namespace, name);
    if (bs != null) {
      stateBlockData.put(block, bs);
      CatFrame.logger.info("Loaded blockstate for state-block: {}:{}", namespace, name);
    } else {
      CatFrame.logger.warn("Failed to load blockstate for state-block: {}:{}", namespace, name);
    }
  }

  /**
   * Load all model data from a namespace.
   */
  private static void loadNamespace(String namespace) {
    // Load model_mappings.json
    loadModelMappings(namespace);
    // Load blockstates
    loadBlockstatesFromMappings(namespace);
    // Load metadata_map.json (auxiliary mapping for vanilla blocks)
    loadMetadataMaps(namespace);
  }

  /**
   * Load model_mappings.json for a namespace.
   */
  private static void loadModelMappings(String namespace) {
    String path = "/assets/" + namespace + "/model_mappings.json";
    try (InputStream stream = VanillaModelManager.class.getResourceAsStream(path)) {
      if (stream == null) return;
      InputStreamReader reader = new InputStreamReader(stream);
      ModelMappings mappings = gson.fromJson(reader, ModelMappings.class);
      if (mappings != null) {
        loadedMappings.put(namespace, mappings);
        CatFrame.logger.info("Loaded model_mappings.json for namespace: {}", namespace);

        // Resolve all models and collect textures
        if (mappings.blocks != null) {
          for (String modelPath : mappings.blocks.values()) {
            collectTexturesFromModel(modelPath);
          }
        }
        if (mappings.items != null) {
          for (String modelPath : mappings.items.values()) {
            collectTexturesFromModel(modelPath);
          }
        }
      }
    } catch (Exception e) {
      CatFrame.logger.debug("No model_mappings.json for namespace {}: {}", namespace, e.getMessage());
    }
  }

  /**
   * Load blockstate files referenced from model_mappings, or scan for them.
   */
  private static void loadBlockstatesFromMappings(String namespace) {
    Map<String, BlockstateJson> nsBlockstates = new HashMap<>();

    // Try to load an index file first
    String indexPath = "/assets/" + namespace + "/blockstates/_index.json";
    try (InputStream stream = VanillaModelManager.class.getResourceAsStream(indexPath)) {
      if (stream != null) {
        InputStreamReader reader = new InputStreamReader(stream);
        String[] names = gson.fromJson(reader, String[].class);
        for (String name : names) {
          BlockstateJson bs = loadSingleBlockstate(namespace, name);
          if (bs != null) nsBlockstates.put(name, bs);
        }
      }
    } catch (Exception ignored) {
    }

    // Also try to load blockstates for any blocks in model_mappings
    ModelMappings mappings = loadedMappings.get(namespace);
    if (mappings != null && mappings.blocks != null) {
      for (String blockName : mappings.blocks.keySet()) {
        if (!nsBlockstates.containsKey(blockName)) {
          BlockstateJson bs = loadSingleBlockstate(namespace, blockName);
          if (bs != null) nsBlockstates.put(blockName, bs);
        }
      }
    }

    // Try common vanilla block names if minecraft namespace
    if (namespace.equals("minecraft") && nsBlockstates.isEmpty()) {
      String[] commonBlocks = {
          "stone", "dirt", "grass", "cobblestone", "planks", "sand", "gravel",
          "gold_ore", "iron_ore", "coal_ore", "log", "log2", "leaves", "leaves2", "glass",
          "lapis_ore", "lapis_block", "sandstone", "wool", "gold_block",
          "iron_block", "brick_block", "tnt", "bookshelf", "mossy_cobblestone",
          "obsidian", "diamond_ore", "diamond_block", "crafting_table",
          "furnace", "redstone_ore", "ice", "snow", "clay", "netherrack",
          "soul_sand", "glowstone", "stonebrick", "melon_block", "nether_brick",
          "end_stone", "emerald_ore", "emerald_block", "quartz_block",
          "hardened_clay", "stained_hardened_clay", "hay_block", "coal_block",
          "cobblestone_wall", "stained_glass", "trapdoor",
          "torch", "redstone_torch", "unlit_redstone_torch", "redstone_wire",
          "unpowered_repeater", "powered_repeater",
          "unpowered_comparator", "powered_comparator",
          "redstone_lamp", "lit_redstone_lamp", "redstone_block",
          "cauldron", "double_stone_slab", "stone_slab",
          "double_wooden_slab", "wooden_slab", "cactus", "anvil"
      };
      for (String name : commonBlocks) {
        BlockstateJson bs = loadSingleBlockstate(namespace, name);
        if (bs != null) nsBlockstates.put(name, bs);
      }
    }

    if (!nsBlockstates.isEmpty()) {
      loadedBlockstates.put(namespace, nsBlockstates);
    }
  }

  /**
   * Load metadata_map.json for a namespace as an auxiliary data-driven
   * metadata-to-properties mapping.  Function-based registration via
   * {@link #registerMetadataMapping} always takes priority.
   */
  private static void loadMetadataMaps(String namespace) {
    String path = "/assets/" + namespace + "/metadata_map.json";
    try (InputStream stream = VanillaModelManager.class.getResourceAsStream(path)) {
      if (stream == null) return;
      InputStreamReader reader = new InputStreamReader(stream);

      // Format: { "blockName": { "0": {"prop":"val"}, "1": {...} }, ... }
      @SuppressWarnings("unchecked")
      Map<String, Map<String, Map<String, String>>> data = gson.fromJson(reader, Map.class);
      if (data == null) return;

      Map<String, Map<Integer, Map<String, String>>> nsMap = new HashMap<>();
      for (Map.Entry<String, Map<String, Map<String, String>>> blockEntry : data.entrySet()) {
        String blockName = blockEntry.getKey();
        Map<Integer, Map<String, String>> metaMap = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> metaEntry : blockEntry.getValue().entrySet()) {
          try {
            int meta = Integer.parseInt(metaEntry.getKey());
            metaMap.put(meta, metaEntry.getValue());
          } catch (NumberFormatException e) {
            CatFrame.logger.warn("metadata_map.json [{}] invalid metadata key '{}'", blockName, metaEntry.getKey());
          }
        }
        if (!metaMap.isEmpty()) {
          nsMap.put(blockName, metaMap);
        }
      }

      if (!nsMap.isEmpty()) {
        loadedMetadataMaps.put(namespace, nsMap);
        CatFrame.logger.info("Loaded metadata_map.json for namespace: {} ({} blocks)", namespace, nsMap.size());
      }
    } catch (Exception e) {
      CatFrame.logger.debug("No metadata_map.json for namespace {}: {}", namespace, e.getMessage());
    }
  }

  /**
   * Load a single blockstate JSON file.
   */
  private static BlockstateJson loadSingleBlockstate(String namespace, String blockName) {
    String path = "/assets/" + namespace + "/blockstates/" + blockName + ".json";
    try (InputStream stream = VanillaModelManager.class.getResourceAsStream(path)) {
      if (stream == null) return null;
      InputStreamReader reader = new InputStreamReader(stream);
      BlockstateJson bs = blockstateGson.fromJson(reader, BlockstateJson.class);
      if (bs != null) {
        // Collect textures from all variant models
        collectTexturesFromBlockstate(bs);
        CatFrame.logger.debug("Loaded blockstate: {}/{}", namespace, blockName);
      }
      return bs;
    } catch (Exception e) {
      CatFrame.logger.error("Error loading blockstate {}/{}: {}", namespace, blockName, e.getMessage());
      return null;
    }
  }

  private static void collectTexturesFromBlockstate(BlockstateJson bs) {
    if (bs.variants != null) {
      for (BlockstateJson.VariantEntry entry : bs.variants.values()) {
        if (entry.isArray()) {
          for (BlockstateJson.Variant v : entry.list) {
            collectTexturesFromModel(v.model);
          }
        } else if (entry.single != null) {
          collectTexturesFromModel(entry.single.model);
        }
      }
    }
    if (bs.multipart != null) {
      for (BlockstateJson.MultipartCase mpc : bs.multipart) {
        if (mpc.apply != null) {
          collectTexturesFromModel(mpc.apply.model);
        }
      }
    }
  }

  private static void collectTexturesFromModel(String modelPath) {
    if (modelPath == null) return;
    ModelJson resolved = ModelResolver.resolve(modelPath);
    if (resolved != null) {
      Set<String> textures = ModelResolver.collectTextures(resolved);
      pendingTextures.addAll(textures);
    }
  }

  // ==================== Texture Registration ====================

  /**
   * Register all required textures with the texture map.
   * Call during TextureStitchEvent.Pre.
   */
  public static void registerTextures(TextureMap map) {
    for (String texturePath : pendingTextures) {
      String iconName = resolveTextureName(texturePath);
      if (iconName != null && !iconName.isEmpty()) {
        map.registerIcon(iconName);
      }
    }
  }

  /**
   * Collect IIcon references after stitching and bake all models.
   * Call during TextureStitchEvent.Post.
   */
  public static void onTextureStitchPost(TextureMap map) {
    textureIcons.clear();
    for (String texturePath : pendingTextures) {
      String iconName = resolveTextureName(texturePath);
      if (iconName != null) {
        IIcon icon = map.getAtlasSprite(iconName);
        if (icon != null) {
          textureIcons.put(texturePath, icon);
        }
      }
    }
    bakeAllModels();
  }

  // ==================== Model Baking ====================

  private static void bakeAllModels() {
    bakedBlockModels.clear();
    bakedItemModels.clear();
    blockRotations.clear();
    bakedModelCache.clear();

    // Bake from blockstates
    for (Map.Entry<String, Map<String, BlockstateJson>> nsEntry : loadedBlockstates.entrySet()) {
      String namespace = nsEntry.getKey();
      for (Map.Entry<String, BlockstateJson> bsEntry : nsEntry.getValue().entrySet()) {
        String blockName = bsEntry.getKey();
        BlockstateJson bs = bsEntry.getValue();
        Block block = findBlock(namespace, blockName);
        if (block != null) {
          // Auto-register mapper from metadata_map.json if no function-based mapper exists
          if (!metadataMappers.containsKey(block)) {
            IMetadataMapper jsonMapper = findMetadataMapEntry(namespace, blockName);
            if (jsonMapper != null) {
              metadataMappers.put(block, jsonMapper);
              CatFrame.logger.debug("Auto-registered metadata mapper from metadata_map.json for {}:{}", namespace, blockName);
            }
          }
          bakeBlockstateForBlock(block, bs);
        }
      }
    }

    // Bake from model_mappings (blocks that don't have blockstates)
    for (Map.Entry<String, ModelMappings> entry : loadedMappings.entrySet()) {
      String namespace = entry.getKey();
      ModelMappings mappings = entry.getValue();

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

          Block block = findBlock(namespace, blockName);
          if (block == null) continue;

          // Skip if blockstate already handles this block entirely
          if (meta == -1 && bakedBlockModels.containsKey(block)) continue;

          List<BakedQuad> quads = bakeModel(blockEntry.getValue(), 0);
          if (quads == null) continue;

          Map<Integer, List<BakedQuad>> metaMap = bakedBlockModels.get(block);
          if (metaMap == null) {
            metaMap = new HashMap<>();
            bakedBlockModels.put(block, metaMap);
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

          Item item = findItem(namespace, itemName);
          if (item == null) continue;

          List<BakedQuad> quads = bakeModel(itemEntry.getValue(), 0);
          if (quads == null) continue;

          Map<Integer, List<BakedQuad>> damageMap = bakedItemModels.get(item);
          if (damageMap == null) {
            damageMap = new HashMap<>();
            bakedItemModels.put(item, damageMap);
          }

          int targetDamage = (damage == -1) ? 0 : damage;
          if (!damageMap.containsKey(targetDamage)) {
            damageMap.put(targetDamage, quads);
            CatFrame.logger.debug("Baked item from mappings: {} damage={}", itemName, targetDamage);
          }
        }
      }
    }

    CatFrame.logger.info("VanillaModelManager: Baked {} block models, {} item models",
        bakedBlockModels.size(), bakedItemModels.size());
  }

  private static void bakeBlockstateForBlock(Block block, BlockstateJson bs) {
    Map<Integer, List<BakedQuad>> metaMap = new HashMap<>();
    Map<Integer, Integer> rotMap = new HashMap<>();

    // --- Get the effective mapper (function-based takes priority) ---
    IMetadataMapper mapper = metadataMappers.get(block);

    if (bs.variants != null) {
      if (mapper != null) {
        // 1.16.5 path: enumerate metadata, convert via mapper, match property keys
        for (int meta = 0; meta < 16; meta++) {
          Map<String, String> props = mapper.map(meta);
          String variantKey = buildVariantKey(props);
          BlockstateJson.VariantEntry varEntry = bs.variants.get(variantKey);
          if (varEntry == null) {
            varEntry = bs.variants.get("normal");
          }
          if (varEntry == null) continue;

          int seed = meta * 31;
          BlockstateJson.Variant variant = varEntry.getVariant(seed);
          if (variant == null || variant.model == null) continue;

          List<BakedQuad> quads = bakeModel(variant.model, variant.y);
          if (quads != null && !quads.isEmpty()) {
            metaMap.put(meta, quads);
            rotMap.put(meta, variant.y);
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

          List<BakedQuad> quads = bakeModel(variant.model, variant.y);
          if (quads != null) {
            metaMap.put(meta, quads);
            rotMap.put(meta, variant.y);
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
          int rotation = 0;

          for (BlockstateJson.MultipartCase mpc : bs.multipart) {
            boolean applies = (mpc.when == null) || mpc.when.matches(props);
            if (applies && mpc.apply != null) {
              List<BakedQuad> partQuads = bakeModel(mpc.apply.model, mpc.apply.y);
              if (partQuads != null) {
                allQuads.addAll(partQuads);
                if (mpc.apply.y != 0) rotation = mpc.apply.y;
              }
            }
          }

          if (!allQuads.isEmpty()) {
            metaMap.put(meta, allQuads);
            rotMap.put(meta, rotation);
          }
        }
      } else {
        // Compat: bake all unconditional parts for meta 0
        List<BakedQuad> allQuads = new ArrayList<>();
        for (BlockstateJson.MultipartCase mpc : bs.multipart) {
          if (mpc.when == null && mpc.apply != null) {
            List<BakedQuad> partQuads = bakeModel(mpc.apply.model, mpc.apply.y);
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
      bakedBlockModels.put(block, metaMap);
      blockRotations.put(block, rotMap);
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

  /** Check whether a variant key is a raw metadata number (legacy compat). */
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
   * Bake a model into quads with the given rotation.
   * Results are cached by {@code modelPath@rotationY}.
   */
  private static List<BakedQuad> bakeModel(String modelPath, int rotationY) {
    if (modelPath == null) return null;

    String cacheKey = modelPath + "@" + rotationY;
    List<BakedQuad> cached = bakedModelCache.get(cacheKey);
    if (cached != null) return cached;

    ModelJson resolved = ModelResolver.resolve(modelPath);
    if (resolved == null || resolved.elements == null) return null;

    // Build icon map
    Map<String, IIcon> iconMap = new HashMap<>();
    if (resolved.textures != null) {
      for (Map.Entry<String, String> tex : resolved.textures.entrySet()) {
        String value = tex.getValue();
        if (!value.startsWith("#")) {
          IIcon icon = textureIcons.get(value);
          if (icon == null) {
            String iconName = resolveTextureName(value);
            if (iconName != null) {
              // Try blocks texture map first, then items texture map
              icon = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(iconName);
              if (icon == null) {
                net.minecraft.client.renderer.texture.TextureMap itemMap = 
                    (net.minecraft.client.renderer.texture.TextureMap) Minecraft.getMinecraft().getTextureManager()
                        .getTexture(TextureMap.locationItemsTexture);
                if (itemMap != null) {
                  icon = itemMap.getAtlasSprite(iconName);
                }
              }
            }
          }
          if (icon != null) {
            iconMap.put(tex.getKey(), icon);
          }
        }
      }
    }

    // Bake elements
    List<BakedQuad> quads = new ArrayList<>();
    for (ModelJson.Element element : resolved.elements) {
      quads.addAll(BlockJsonModelBake.bakeElement(element, iconMap));
    }
    bakedModelCache.put(cacheKey, quads);
    return quads;
  }

  // ==================== Texture Resolution ====================

  private static String resolveTextureName(String texturePath) {
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
    }
    
    return namespace + pathPart;
  }

  // ==================== Block/Item Lookup ====================

  private static Block findBlock(String namespace, String name) {
    Block block = Block.getBlockFromName(name);
    if (block != null) return block;
    block = Block.getBlockFromName(namespace + ":" + name);
    return block;
  }

  private static Item findItem(String namespace, String name) {
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
  private static IMetadataMapper findMetadataMapEntry(String namespace, String blockName) {
    // Search the same namespace first, then fallback to all registered namespaces
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

    // Build a mapper lambda from the data
    final Map<Integer, Map<String, String>> finalMap = metaMap;
    return metadata -> finalMap.getOrDefault(metadata, Collections.emptyMap());
  }

  // ==================== Public Render API ====================

  /** Check if a block has a JSON model override (either static bake or dynamic state-provider) */
  public static boolean hasModel(Block block) {
    return bakedBlockModels.containsKey(block) || stateBlockData.containsKey(block);
  }

  /** Check if an item has a JSON model override */
  public static boolean hasItemModel(Item item) {
    return bakedItemModels.containsKey(item);
  }

  /** Render a block using its JSON model */
  public static boolean renderBlock(IBlockAccess world, int x, int y, int z, Block block, RenderBlocks renderer) {
    int metadata = world.getBlockMetadata(x, y, z);

    // Dynamic state-provider path: resolve variant at render time
    if (block instanceof IBlockStateProvider && stateBlockData.containsKey(block)) {
      return renderStateProviderBlock(world, x, y, z, block, metadata);
    }

    // Static baked path: use pre-baked metadata-keyed models
    Map<Integer, List<BakedQuad>> metaMap = bakedBlockModels.get(block);
    if (metaMap == null) return false;

    List<BakedQuad> quads = metaMap.get(metadata);
    if (quads == null) {
      // Fallback to meta 0
      quads = metaMap.get(0);
    }
    if (quads == null || quads.isEmpty()) return false;

    // Get rotation
    Map<Integer, Integer> rotMap = blockRotations.get(block);
    int rotationDeg = 0;
    if (rotMap != null) {
      Integer rot = rotMap.get(metadata);
      if (rot == null) rot = rotMap.get(0);
      if (rot != null) rotationDeg = rot;
    }

    renderQuads(world, x, y, z, block, quads, rotationDeg);
    return true;
  }

  /**
   * Render a block using IBlockStateProvider's dynamic variant resolution.
   * Matches current block properties to blockstate variants or multipart conditions.
   */
  private static boolean renderStateProviderBlock(IBlockAccess world, int x, int y, int z, Block block, int metadata) {
    IBlockStateProvider provider = (IBlockStateProvider) block;
    BlockstateJson bs = stateBlockData.get(block);
    if (bs == null) return false;

    Map<String, String> properties = provider.getStateProperties(world, x, y, z, metadata);
    if (properties == null) properties = Collections.emptyMap();

    if (bs.variants != null) {
      // Build variant key from properties: "key1=val1,key2=val2" (sorted)
      String variantKey = buildVariantKey(properties);
      BlockstateJson.VariantEntry entry = bs.variants.get(variantKey);

      // Fallback: try "normal" if exact match fails
      if (entry == null) entry = bs.variants.get("normal");
      if (entry == null) return false;

      // Use position-based seed for weighted random
      int seed = x * 3129871 ^ z * 116129781 ^ y;
      BlockstateJson.Variant variant = entry.getVariant(seed);
      if (variant == null || variant.model == null) return false;

      List<BakedQuad> quads = bakeModel(variant.model, variant.y);
      if (quads == null || quads.isEmpty()) return false;

      renderQuads(world, x, y, z, block, quads, variant.y);
      return true;

    } else if (bs.multipart != null) {
      // Multipart: combine all matching parts
      List<BakedQuad> allQuads = new ArrayList<>();
      int rotation = 0;

      for (BlockstateJson.MultipartCase mpc : bs.multipart) {
        boolean applies = (mpc.when == null) || mpc.when.matches(properties);
        if (applies && mpc.apply != null) {
          List<BakedQuad> partQuads = bakeModel(mpc.apply.model, mpc.apply.y);
          if (partQuads != null) {
            allQuads.addAll(partQuads);
            if (mpc.apply.y != 0) rotation = mpc.apply.y;
          }
        }
      }

      if (allQuads.isEmpty()) return false;
      renderQuads(world, x, y, z, block, allQuads, rotation);
      return true;
    }

    return false;
  }

  /**
   * Build a variant key string from properties map.
   * Properties are sorted alphabetically and joined as "key1=val1,key2=val2".
   */
  private static String buildVariantKey(Map<String, String> properties) {
    if (properties.isEmpty()) return "normal";
    List<String> keys = new ArrayList<>(properties.keySet());
    Collections.sort(keys);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < keys.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append(keys.get(i)).append('=').append(properties.get(keys.get(i)));
    }
    return sb.toString();
  }

  /**
   * Render a list of baked quads at the given block position.
   * 每个 quad 都会过一遍 {@link ModelRenderRegistry} 扩展链，扩展可改
   * 颜色 / 亮度 / 剔除该面，从而支持 tint、阴影、面剔除等通用渲染特性。
   */
  private static void renderQuads(IBlockAccess world, int x, int y, int z, Block block, List<BakedQuad> quads, int rotationDeg) {
    Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
    Tessellator t = Tessellator.instance;
    double a = Math.toRadians(rotationDeg);
    double cos = Math.cos(a);
    double sin = Math.sin(a);

    for (BakedQuad q : quads) {
      int baseBrightness = getFaceBrightness(world, x, y, z, block, q.face);
      float baseShade = shadeByNormal(q.faceNormal);

      // 走通用扩展链（内建 TintRenderExtension 在链头处理 tintindex）
      RenderContext ctx = new RenderContext(RenderPhase.BLOCK_WORLD, q,
          world, x, y, z, block, null, baseBrightness, baseShade);
      ModelRenderRegistry.apply(ctx);
      if (ctx.skip) continue;

      t.setBrightness(ctx.effectiveBrightness());
      float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade;
      float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade;
      float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade;
      t.setColorOpaque_F(cr, cg, cb);

      IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
      for (int i = 0; i < 4; i++) {
        double vx = q.vx[i], vy = q.vy[i], vz = q.vz[i];

        if (rotationDeg != 0) {
          double px = vx - 0.5;
          double pz = vz - 0.5;
          vx = px * cos - pz * sin + 0.5;
          vz = px * sin + pz * cos + 0.5;
        }

        double U = icon.getInterpolatedU(q.up[i]);
        double V = icon.getInterpolatedV(q.vp[i]);
        t.addVertexWithUV(x + vx, y + vy, z + vz, U, V);
      }
    }
  }

  /**
   * Render an item using its JSON model (GUI / inventory context).
   * 接收 ItemStack 以便扩展能读取 NBT / damage / 附魔等完整上下文。
   */
  public static void renderItem(ItemStack stack) {
    if (stack == null) return;
    Item item = stack.getItem();
    if (item == null) return;
    Map<Integer, List<BakedQuad>> damageMap = bakedItemModels.get(item);
    if (damageMap == null) return;

    int damage = stack.getItemDamage();
    List<BakedQuad> quads = damageMap.get(damage);
    if (quads == null) quads = damageMap.get(0);
    if (quads == null || quads.isEmpty()) return;

    drawItemQuads(quads, stack, RenderPhase.ITEM_GUI, 255, false);
  }

  /** 旧接口兼容薄包装：无 NBT 上下文，扩展仅能看到 item+damage。 */
  public static void renderItem(Item item, int damage) {
    if (item == null) return;
    renderItem(new ItemStack(item, 1, damage));
  }

  /**
   * Render an item's JSON model as a 3D model for in-hand rendering.
   * Unlike {@link #renderItem(ItemStack)} which is designed for 2D GUI
   * context, this method renders the model at the current GL origin with
   * proper 3D centering — the caller ({@code ItemRenderer#renderItem})
   * has already set up the hand position / rotation transforms.
   */
  public static void renderItemInHand(ItemStack stack) {
    if (stack == null) return;
    Item item = stack.getItem();
    if (item == null) return;
    Map<Integer, List<BakedQuad>> damageMap = bakedItemModels.get(item);
    if (damageMap == null) return;

    int damage = stack.getItemDamage();
    List<BakedQuad> quads = damageMap.get(damage);
    if (quads == null) quads = damageMap.get(0);
    if (quads == null || quads.isEmpty()) return;

    drawItemQuads(quads, stack, RenderPhase.ITEM_HAND, 15728880, true);
  }

  /** 旧接口兼容薄包装。 */
  public static void renderItemInHand(Item item, int damage) {
    if (item == null) return;
    renderItemInHand(new ItemStack(item, 1, damage));
  }

  /**
   * 物品端公用绘制逻辑：绑定方块纹理集、过扩展链、提交 Tessellator。
   *
   * @param baseBrightness 默认亮度 (GUI=255, 手持=full bright)
   * @param centered       是否把原点偏移到方块中心 (手持需要)
   */
  private static void drawItemQuads(List<BakedQuad> quads, ItemStack stack,
                                    RenderPhase phase, int baseBrightness, boolean centered) {
    Tessellator t = Tessellator.instance;
    if (!centered) GL11.glPushMatrix();
    Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
    t.startDrawingQuads();

    for (BakedQuad q : quads) {
      float baseShade = shadeByNormal(q.faceNormal);
      RenderContext ctx = new RenderContext(phase, q,
          null, 0, 0, 0, null, stack, baseBrightness, baseShade);
      ModelRenderRegistry.apply(ctx);
      if (ctx.skip) continue;

      // 物品不受周围光照影响，以 baseBrightness 为准，但扩展可覆盖
      t.setBrightness(ctx.effectiveBrightness());
      float cr = ((ctx.color >> 16) & 0xFF) / 255.0f * ctx.shade;
      float cg = ((ctx.color >> 8) & 0xFF) / 255.0f * ctx.shade;
      float cb = (ctx.color & 0xFF) / 255.0f * ctx.shade;
      t.setColorOpaque_F(cr, cg, cb);

      IIcon icon = (ctx.iconOverride != null) ? ctx.iconOverride : q.icon;
      for (int i = 0; i < 4; i++) {
        double U = icon.getInterpolatedU(q.up[i]);
        double V = icon.getInterpolatedV(q.vp[i]);
        double vx = q.vx[i], vy = q.vy[i], vz = q.vz[i];
        if (centered) { vx -= 0.5; vy -= 0.5; vz -= 0.5; }
        t.addVertexWithUV(vx, vy, vz, U, V);
      }
    }

    t.draw();
    if (!centered) GL11.glPopMatrix();
  }

  // ==================== Lighting Helpers ====================

  private static int getFaceBrightness(IBlockAccess w, int x, int y, int z, Block b, EnumFacing face) {
    if (face == null) return b.getMixedBrightnessForBlock(w, x, y, z);
    switch (face) {
      case UP:    return w.getLightBrightnessForSkyBlocks(x, y + 1, z, 0);
      case DOWN:  return w.getLightBrightnessForSkyBlocks(x, y - 1, z, 0);
      case NORTH: return w.getLightBrightnessForSkyBlocks(x, y, z - 1, 0);
      case SOUTH: return w.getLightBrightnessForSkyBlocks(x, y, z + 1, 0);
      case WEST:  return w.getLightBrightnessForSkyBlocks(x - 1, y, z, 0);
      case EAST:  return w.getLightBrightnessForSkyBlocks(x + 1, y, z, 0);
      default:    return b.getMixedBrightnessForBlock(w, x, y, z);
    }
  }

  private static float shadeByNormal(double[] n) {
    if (n == null) return 1.0f;
    double ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
    if (ay >= ax && ay >= az) return n[1] > 0 ? 1.0f : 0.5f;
    if (ax >= ay && ax >= az) return 0.6f;
    return 0.8f;
  }
}
