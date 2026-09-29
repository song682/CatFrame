package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.AtlasPixelCache;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.core.async.AsyncBakePipeline;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Texture tracking and management extracted from {@link VanillaModelManager}.
 * <p>
 * Responsible for collecting texture paths from models/blockstates during loading,
 * registering them with the appropriate texture atlas, and stitching post-processing.
 */
@SideOnly(Side.CLIENT)
public class VanillaTextureTracker {

    // ==================== Texture tracking registry ====================

    /**
     * Vanilla atlas registration/lookup key: plain 1.7.10 base-path key transform
     * (strips namespace and {@code blocks/}/{@code items/} singular prefixes).
     * <p>
     * Only serves vanilla atlas (TextureMap) paths — 1.7.10 vanilla key semantics
     * is basePath (e.g. {@code minecraft:blocks/ladder} → {@code ladder}); CatFrame
     * data-driven atlas keys are full texture paths, managed independently by
     * {@code CatAtlasManager}, unrelated to this method.
     *
     * <p>[Hot Update rollback] Key strategy convergence: namespace stripping only for minecraft
     * (flat keys stay idempotent with {@code block.registerBlockIcons()} and OptiFine CTM
     * name matching); other namespaces keep {@code modid:name} (via {@code ResourceLocation}
     * domain parsing, resolves to {@code modid:textures/blocks/<name>.png}), avoiding
     * cross-mod collisions.
     *
     * @param itemAtlas true = items atlas (textures/items/), false = blocks atlas
     */
    public static String toVanillaKey(String texturePath, boolean itemAtlas) {
        if (texturePath == null) return null;
        int colon = texturePath.indexOf(':');
        String namespace = colon >= 0 ? texturePath.substring(0, colon) : "minecraft";
        String pathPart = colon >= 0 ? texturePath.substring(colon + 1) : texturePath;
        if (pathPart.startsWith("blocks/")) {
            pathPart = pathPart.substring("blocks/".length());
        } else if (pathPart.startsWith("items/")) {
            pathPart = pathPart.substring("items/".length());
        } else if (pathPart.startsWith("block/")) {
            pathPart = pathPart.substring("block/".length());
        } else if (pathPart.startsWith("item/")) {
            pathPart = pathPart.substring("item/".length());
        }
        // minecraft → flat key; other namespaces → "modid:name" (cross-mod collision-free)
        // minecraft → flat key; other namespaces → "modid:name" (collision-free)
        return "minecraft".equals(namespace) ? pathPart : namespace + ":" + pathPart;
    }

    static final Set<String> pendingTextures = new LinkedHashSet<>();
    static final Set<String> pendingItemTextures = new LinkedHashSet<>();
    public static final Map<String, IIcon> textureIcons = new ConcurrentHashMap<>();
    // [Hot Update rollback] Vanilla backend diagnostic compensation: keys CatFrame registered
    // TextureMap keys (model-driven + definition-driven), compared with upload
    // Diagnostics reclaimed: vanilla stitch silently copyFrom(missingImage) on failure,
    // Registered-key tracking for the vanilla backend: compared against the
    // upload results at Post so missing textures are reported explicitly.
    static final Set<String> registeredBlockKeys = new LinkedHashSet<>();
    static final Set<String> registeredItemKeys = new LinkedHashSet<>();
    /**
     * Model-driven block texture set (cross-package access entry for CatAtlasManager).
     */
    public static Set<String> getPendingTextures() {
        return pendingTextures;
    }

    /**
     * Model-driven item texture set (cross-package access entry for CatAtlasManager).
     */
    public static Set<String> getPendingItemTextures() {
        return pendingItemTextures;
    }

    static void collectTexturesFromBlockstate(@Nonnull BlockstateJson bs) {
        if (bs.variants != null) {
            for (BlockstateJson.VariantEntry entry : bs.variants.values()) {
                if (entry.isArray()) {
                    for (BlockstateJson.Variant v : entry.list) {
                        collectTexturesFromModel(v.model, false);
                    }
                } else if (entry.single != null) {
                    collectTexturesFromModel(entry.single.model, false);
                }
            }
        }
        if (bs.multipart != null) {
            for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                if (mpc.apply != null) {
                    if (mpc.apply.isArray()) {
                        for (BlockstateJson.Variant v : mpc.apply.list) {
                            collectTexturesFromModel(v.model, false);
                        }
                    } else if (mpc.apply.single != null) {
                        collectTexturesFromModel(mpc.apply.single.model, false);
                    }
                }
            }
        }
    }

    /**
     * Internal: resolve a model and collect its textures into the appropriate
     * pending set based on model type.
     *
     * @param modelPath   model resource path
     * @param isItemModel true → item atlas, false → block atlas
     */
    static void collectTexturesFromModel(String modelPath, boolean isItemModel) {
        if (modelPath == null) return;
        ModelJson resolved = ModelResolver.resolve(modelPath);
        if (resolved != null) {
            Set<String> textures = ModelResolver.collectTextures(resolved);
            for (String tex : textures) {
                // Extract path after namespace for prefix checking
                // e.g., "minecraft:block/sapling_oak" → pathPart = "block/sapling_oak"
                String pathPart = tex;
                int colon = tex.indexOf(':');
                if (colon >= 0) {
                    pathPart = tex.substring(colon + 1);
                }
                // Block textures belong to block atlas regardless of calling model type
                // Item models (like items/oak_sapling.json) may reference block textures (layer0: "minecraft:block/sapling_oak")
                if (pathPart.startsWith("block/") || pathPart.startsWith("blocks/")) {
                    pendingTextures.add(tex);
                } else if (pathPart.startsWith("item/") || pathPart.startsWith("items/")) {
                    pendingItemTextures.add(tex);
                } else if (isItemModel) {
                    pendingItemTextures.add(tex);
                } else {
                    pendingTextures.add(tex);
                }
            }
        }
    }

    // ==================== Texture Registration ====================

    /**
     * Register all block textures with the block texture map (type 0).
     * Call during TextureStitchEvent.Pre when getTextureType() == 0.
     */
    public static void registerTextures(TextureMap map) {
        registeredBlockKeys.clear(); // Rebuild diagnostic key set each stitch cycle
        for (String texturePath : pendingTextures) {
            String iconName = toVanillaKey(texturePath, false);
            if (iconName != null && !iconName.isEmpty()) {
                map.registerIcon(iconName);
                registeredBlockKeys.add(iconName);
            }
        }
    }

    /**
     * Register all item textures with the item texture map (type 1).
     * Call during TextureStitchEvent.Pre when getTextureType() == 1.
     */
    public static void registerItemTextures(TextureMap map) {
        registeredItemKeys.clear(); // Rebuild diagnostic key set each stitch cycle
        for (String texturePath : pendingItemTextures) {
            String iconName = toVanillaKey(texturePath, true);
            if (iconName != null && !iconName.isEmpty()) {
                map.registerIcon(iconName);
                registeredItemKeys.add(iconName);
            }
        }
    }

    /**
     * [Hot Update rollback] Register a key that was registered to the vanilla atlas
     * (for Post diagnostic comparison). Written by both {@code CatAtlasManager.registerDefinedSprites}
     * (definition-driven output) and {@link #registerTextures}/{@link #registerItemTextures}
     * (model-driven).
     */
    public static void trackRegisteredKey(String key, boolean itemAtlas) {
        if (key == null || key.isEmpty()) return;
        (itemAtlas ? registeredItemKeys : registeredBlockKeys).add(key);
    }

/**
     * [Hot Update rollback] Diagnostic compensation: compare this cycle's registered keys
     * with atlas upload results; warn on each key that failed to upload (fell back to
     * missingImage instance). Vanilla stitch silently copyFrom(missingImage) on failure;
     * this method recovers Stage 5 lookup diagnosability on the vanilla backend.
     */
    static void verifyUploaded(TextureMap map, Set<String> keys, String what) {
        if (keys.isEmpty()) return;
        net.minecraft.client.renderer.texture.TextureAtlasSprite missing = map.getAtlasSprite("missingno");
        int missed = 0;
        for (String key : keys) {
            if (map.getAtlasSprite(key) == missing) {
                missed++;
                CatFrame.logger.warn("[VTT-diag] texture error: registered '{}' not uploaded to {} atlas, "
                        + "renders missingno (file missing or decode failed)", key, what);
            }
        }
        if (missed == 0) {
            CatFrame.logger.info("[VTT-diag] {} atlas: all {} registered keys uploaded successfully",
                    what, keys.size());
        }
    }

    /**
     * Collect IIcon references after stitching and bake all models.
     * Call during TextureStitchEvent.Post when getTextureType() == 0.
     */
    public static void onTextureStitchPost(TextureMap map) {
        // Don't clear pendingTextures/pendingItemTextures — matches LegacyPreview behavior.
        // Forge 1.7.10 fires type=0 Post twice at startup (second from refreshResources).
        // Retain data so both passes can recollect IIcon from their fresh atlases, avoiding stale references.
        if (pendingTextures.isEmpty() && pendingItemTextures.isEmpty()) {
            CatFrame.logger.info("[VTT-diag] onStitchPost: pending sets empty, skip");
            return;
        }
        textureIcons.clear();
        AtlasPixelCache.clear(); // Clear previous cycle's pixel readback cache on resource reload

        int blockCollected = 0, blockMissed = 0;
        java.util.List<IIcon> blockIcons = new java.util.ArrayList<>();
        // Block atlas icons
        for (String texturePath : pendingTextures) {
            String iconName = toVanillaKey(texturePath, false);
            if (iconName != null) {
                IIcon icon = map.getAtlasSprite(iconName);
                if (icon != null) {
                    textureIcons.put(texturePath, icon);
                    blockIcons.add(icon);
                    blockCollected++;
                } else {
                    blockMissed++;
                    CatFrame.logger.warn("[VTT-diag] block IIcon miss: texturePath='{}' → iconName='{}'", texturePath, iconName);
                }
            }
        }
        // Item atlas icons
        int itemCollected = 0, itemMissed = 0;
        java.util.List<IIcon> itemIcons = new java.util.ArrayList<>();
        net.minecraft.client.renderer.texture.TextureMap itemMap =
                (net.minecraft.client.renderer.texture.TextureMap) Minecraft.getMinecraft().getTextureManager()
                        .getTexture(TextureMap.locationItemsTexture);
        if (itemMap != null) {
            for (String texturePath : pendingItemTextures) {
                String iconName = toVanillaKey(texturePath, true);
                if (iconName != null) {
                    IIcon icon = itemMap.getAtlasSprite(iconName);
                    if (icon != null) {
                        textureIcons.put(texturePath, icon);
                        itemIcons.add(icon);
                        itemCollected++;
                    } else {
                        itemMissed++;
                    }
                }
            }
        }
        CatFrame.logger.info("[VTT-diag] onStitchPost: pendingBlk={} pendingItm={} | collected blk={} miss={} itm={} miss={} | textureIcons.size={}",
                pendingTextures.size(), pendingItemTextures.size(),
                blockCollected, blockMissed, itemCollected, itemMissed,
                textureIcons.size());

        // [Hot Update rollback] Diagnostic compensation: registered keys vs upload results (blocks atlas)
        verifyUploaded(map, registeredBlockKeys, "blocks");

        // Publish CatAtlas sprites AFTER the vanilla collection loops (they would
        // overwrite Pre-stage results) and BEFORE the bake barrier below.
        // [Render three-domain architecture] CatAtlas custom stitching retired; no custom sprites to publish.

        // GPU readback: read atlas pixels on main thread once, for async bake threads to use pure CPU
        AtlasPixelCache.readAtlas(map, blockIcons);
        if (itemMap != null && !itemIcons.isEmpty()) {
            AtlasPixelCache.readAtlas(itemMap, itemIcons);
        }

        // Don't clear pendingTextures — retain data for second stitch after Forge refreshResources re-collection
        // Mirrors modern MaterialBaker closure pattern: iconMap passed as parameter to cache and bake pipeline
        BakedModelCache.INSTANCE.clear(textureIcons);
        ModelResolver.clearCache();
        // Particle texture cache invalidated same cycle as iconMap (avoids stale IIcon from previous cycle)
        ParticleIconResolver.clear();

        CatFrame.logger.info("[VTT-diag] BakedModelCache.clear(iconMap) called | textureIcons.size={}",
                textureIcons.size());
        // Register lazy models (no sync bake; bake handled by AsyncBakePipeline barrier; lazy bake as safety net only)
        VanillaModelManager.Baking.registerAllModels();
        // Async prepare, sync switch: parallel pre-bake all common models and block until done, cache ready on return (mirrors vanilla reload barrier)
        AsyncBakePipeline.triggerBakeBlocking(textureIcons);
    }

/**
     * Update item texture IIcon refs and rebake after item atlas (type 1) stitch completes.
     * <p>
     * In 1.7.10, block atlas (type 0) {@link net.minecraftforge.client.event.TextureStitchEvent.Post}
     * fires before item atlas (type 1) Post. So at type 0 Post, item atlas may not be fully
     * stitched yet, {@link TextureMap#getAtlasSprite(String)} may return pre-stitch placeholder
     * sprite or even missingno.
     * This method is called at type 1 Post, when item atlas is fully stitched and correct
     * sprite UV coords are available.
     */
    public static void onTextureStitchPostItem(TextureMap itemMap) {
        // Retain pendingItemTextures — matches LegacyPreview behavior, supports multiple stitches after refreshResources
        if (pendingItemTextures.isEmpty()) {
            CatFrame.logger.info("[VTT-diag] onStitchPostItem: pending empty, skip");
            return;
        }
        // Update item texture IIcon refs (item atlas is fully stitched at this point)
        java.util.List<IIcon> itemIcons = new java.util.ArrayList<>();
        for (String texturePath : pendingItemTextures) {
            String iconName = toVanillaKey(texturePath, true);
            if (iconName != null) {
                IIcon icon = itemMap.getAtlasSprite(iconName);
                if (icon != null) {
                    textureIcons.put(texturePath, icon);
                    itemIcons.add(icon);
                }
            }
        }
        // [Hot Update rollback] Diagnostic compensation: registered keys vs upload results (items atlas)
        verifyUploaded(itemMap, registeredItemKeys, "items");
        // [Render three-domain architecture] CatAtlas custom stitching retired; no custom sprites to republish.
        // GPU readback of item atlas (UVs are now final, overwriting early data from onTextureStitchPost)
        AtlasPixelCache.readAtlas(itemMap, itemIcons);
        // Don't clear pendingItemTextures — retain data for multiple stitch re-collection
        // item iconMap updated to cache (for lazy baking)
        BakedModelCache.INSTANCE.clear(textureIcons);
        // Particle texture cache invalidated same cycle as iconMap
        ParticleIconResolver.clear();
        // [W2 fix] Incremental item model registration only (lazy models, no actual baking)
        VanillaModelManager.Baking.registerItemModels();
        // After item atlas ready, parallel pre-bake again and block until done (ensures item models ready on return, zero on-demand baking)
        AsyncBakePipeline.triggerBakeBlocking(textureIcons);
    }
}
