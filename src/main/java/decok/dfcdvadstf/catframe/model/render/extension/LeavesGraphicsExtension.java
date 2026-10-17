package decok.dfcdvadstf.catframe.model.render.extension;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.IIcon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Rendering extension: when Minecraft graphics are set to "Fast", replace leaf block textures
 * with the {@code _opaque} variant to eliminate transparent edges, matching vanilla
 * {@code BlockLeaves#isOpaqueCube} behavior.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Register all {@code _opaque} textures in the atlas during {@code TextureStitchEvent.Pre};</li>
 *   <li>Resolve and cache {@link IIcon}s during {@code TextureStitchEvent.Post};</li>
 *   <li>During rendering, if {@code !Minecraft.isFancyGraphicsEnabled()}, set
 *       {@link RenderContext#iconOverride} to the cached _opaque {@link IIcon}.</li>
 * </ol>
 */
@SideOnly(Side.CLIENT)
public final class LeavesGraphicsExtension implements IModelRenderExtension {

    // ==================== Texture mapping: leaf block -> _opaque texture paths ====================
    // key: normal texture path (as in model JSON), value: _opaque texture path
    private static final Map<String, String> TEXTURE_MAP = new ConcurrentHashMap<>();

    // Cached resolved _opaque IIcons: normalTexturePath -> opaqueIcon
    private static final Map<String, IIcon> OPAQUE_ICONS = new ConcurrentHashMap<>();

    // List of leaf blocks (extensible)
    private static final List<Block> LEAF_BLOCKS = new CopyOnWriteArrayList<>();

    // Registration flag
    private static boolean texturesRegistered = false;

    // [S5] Texture mapping for modded custom leaf blocks: Block -> (normalPath -> opaquePath)
    private static final Map<Block, Map<String, String>> CUSTOM_LEAF_TEXTURES = new ConcurrentHashMap<>();

    static {
        // ——— Texture mapping definitions ———
        TEXTURE_MAP.put("minecraft:blocks/leaves_oak", "minecraft:blocks/leaves_oak_opaque");
        TEXTURE_MAP.put("minecraft:blocks/leaves_spruce", "minecraft:blocks/leaves_spruce_opaque");
        TEXTURE_MAP.put("minecraft:blocks/leaves_birch", "minecraft:blocks/leaves_birch_opaque");
        TEXTURE_MAP.put("minecraft:blocks/leaves_jungle", "minecraft:blocks/leaves_jungle_opaque");
        TEXTURE_MAP.put("minecraft:blocks/leaves_acacia", "minecraft:blocks/leaves_acacia_opaque");
        TEXTURE_MAP.put("minecraft:blocks/leaves_big_oak", "minecraft:blocks/leaves_big_oak_opaque");

        // ——— Leaf block registration ———
        // Blocks.leaves (1.7.10 vanilla): oak=0, spruce=1, birch=2, jungle=3
        if (Blocks.leaves != null) {
            LEAF_BLOCKS.add(Blocks.leaves);
        }
        // leaves2 (acacia=0, big_oak=1)
        Block leaves2 = getLeaves2Block();
        if (leaves2 != null) {
            LEAF_BLOCKS.add(leaves2);
        }
    }

    public LeavesGraphicsExtension() {
    }

    // ==================== Modded leaf API [S5] ====================

    /**
     * Register _opaque texture mapping for a custom leaf block.
     * <p>
     * Mods may call this during initialization to register custom leaf blocks for Fast graphics
     * handling. After registration, when graphics are set to Fast, the leaf block's textures
     * are replaced with their _opaque variants.
     *
     * @param block texture block instance
     * @param textureMapping mapping from normal texture path to _opaque texture path
     *                       (e.g. {@code "mymod:blocks/custom_leaves" → "mymod:blocks/custom_leaves_opaque"})
     */
    public static void registerLeafBlock(Block block, Map<String, String> textureMapping) {
        if (block == null || textureMapping == null) return;
        if (!LEAF_BLOCKS.contains(block)) {
            LEAF_BLOCKS.add(block);
        }
        CUSTOM_LEAF_TEXTURES.put(block, new HashMap<>(textureMapping));
        TEXTURE_MAP.putAll(textureMapping);
        // If textures were already registered, re-register new textures
        if (texturesRegistered) {
            texturesRegistered = false;
        }
    }

    // ==================== Texture lifecycle ====================

    /**
     * Called in TextureStitchEvent.Pre to register all _opaque textures.
     */
    public static void registerTextures(TextureMap map) {
        if (texturesRegistered) return;
        texturesRegistered = true;
        for (String opaquePath : TEXTURE_MAP.values()) {
            String iconName = VanillaTextureTracker.toVanillaKey(opaquePath, false);
            if (iconName != null && !iconName.isEmpty()) {
                map.registerIcon(iconName);
            }
        }
    }

    /**
     * Called in TextureStitchEvent.Post to resolve and cache _opaque IIcons.
     */
    public static void onTextureStitchPost(TextureMap map) {
        OPAQUE_ICONS.clear();
        for (Map.Entry<String, String> entry : TEXTURE_MAP.entrySet()) {
            String normalPath = entry.getKey();
            String iconName = VanillaTextureTracker.toVanillaKey(entry.getValue(), false);
            if (iconName != null) {
                IIcon icon = map.getAtlasSprite(iconName);
                if (icon != null) {
                    OPAQUE_ICONS.put(normalPath, icon);
                }
            }
        }
    }

    /**
     * Check whether to use _opaque textures for current graphics setting.
     */
    private static boolean shouldUseOpaque() {
        return !Minecraft.getMinecraft().gameSettings.fancyGraphics;
    }

    /**
     * Get the corresponding _opaque IIcon for the given leaf block and metadata.
     */
    private static IIcon getOpaqueIcon(Block block, int metadata) {
        if (OPAQUE_ICONS.isEmpty()) return null;

        // Get normal texture path for this leaf variant
        String normalTex = getNormalTexture(block, metadata);
        if (normalTex == null) return null;

        return OPAQUE_ICONS.get(normalTex);
    }

    /**
     * Determine normal texture path based on block and metadata.
     * Prioritizes modded custom leaf mappings, falls back to vanilla leaves/leaves2.
     */
    private static String getNormalTexture(Block block, int metadata) {
        int meta = metadata & 3;
        // [S5] Check modded custom leaf mappings
        Map<String, String> customMapping = CUSTOM_LEAF_TEXTURES.get(block);
        if (customMapping != null && !customMapping.isEmpty()) {
            // Return the first normal texture path (custom leaves usually have single variant)
            return customMapping.keySet().iterator().next();
        }
        if (block == Blocks.leaves) {
            switch (meta) {
                case 0:
                    return "minecraft:blocks/leaves_oak";
                case 1:
                    return "minecraft:blocks/leaves_spruce";
                case 2:
                    return "minecraft:blocks/leaves_birch";
                case 3:
                    return "minecraft:blocks/leaves_jungle";
                default:
                    return "minecraft:blocks/leaves_oak";
            }
        }
        // Compatible with leaves2 (1.8+ / added by mods)
        Block leaves2 = getLeaves2Block();
        if (leaves2 != null && block == leaves2) {
            switch (meta) {
                case 0:
                    return "minecraft:blocks/leaves_acacia";
                case 1:
                    return "minecraft:blocks/leaves_big_oak";
                default:
                    return "minecraft:blocks/leaves_acacia";
            }
        }
        return null;
    }

    /**
     * Safely get leaves2 block (may not exist in 1.7.10; returns null).
     */
    private static Block getLeaves2Block() {
        try {
            return (Block) Block.blockRegistry.getObject("leaves2");
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== IModelRenderExtension ====================

    @Override
    public void apply(RenderContext ctx) {
            // Only switch textures in Fast graphics mode
            if (!shouldUseOpaque()) return;

            Block leafBlock = null;
            int metadata = 0;

            switch (ctx.phase) {
                case BLOCK_WORLD:
                    if (ctx.block == null || ctx.world == null) return;
                    leafBlock = ctx.block;
                    metadata = ctx.world.getBlockMetadata(ctx.x, ctx.y, ctx.z);
                    break;

                case ITEM_GUI:
                case ITEM_HAND_FIRST_PERSON:
                case ITEM_HAND_THIRD_PERSON:
                case DROPPED_ITEM_GROUND:
                case DROPPED_BLOCK_GROUND:
                case ITEM_FIXED:
                    // Item phase: derive block and metadata from ItemStack
                    if (ctx.stack == null || !(ctx.stack.getItem() instanceof ItemBlock)) return;
                    leafBlock = ((ItemBlock) ctx.stack.getItem()).field_150939_a;
                    metadata = ctx.stack.getItemDamage();
                    break;

                default:
                    return;
            }

            // Check if it is a leaf block
            if (leafBlock == null || !LEAF_BLOCKS.contains(leafBlock)) return;

            // Get _opaque IIcon
            IIcon opaqueIcon = getOpaqueIcon(leafBlock, metadata);
            if (opaqueIcon == null) return;

            if (ctx.phase == RenderPhase.BLOCK_WORLD) {
                // Block world phase: bind to vanilla atlas; vanilla _opaque sprite is directly usable (eliminates transparent edges).
                ctx.iconOverride = opaqueIcon;
            }
            // CatAtlas retired; findSprite contract removed. For item phases there is no CatSprite to rebind,
            // so we fall back to the normal model texture in vanilla atlas UV space (consistent with flushBatched).
    }
}
