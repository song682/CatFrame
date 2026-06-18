package decok.dfcdvadstf.catframe.model.render.extension;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 渲染扩展：当 Minecraft 画质设为"流畅"时，将树叶方块的纹理替换为 {@code _opaque} 版本，
 * 从而消除透明边缘，与原版 BlockLeaves#isOpaqueCube 行为保持一致。
 *
 * <h3>工作方式</h3>
 * <ol>
 *   <li>在 {@code TextureStitchEvent.Pre} 中注册所有 {@code _opaque} 纹理至图集；</li>
 *   <li>在 {@code TextureStitchEvent.Post} 中解析并缓存 IIcon；</li>
 *   <li>渲染时检测 {@code !Minecraft.isFancyGraphicsEnabled()}，若匹配则设置
 *       {@link RenderContext#iconOverride} 为缓存的 _opaque IIcon。</li>
 * </ol>
 */
@SideOnly(Side.CLIENT)
public final class LeavesGraphicsExtension implements IModelRenderExtension {

    // ==================== 纹理映射：树叶方块 -> _opaque 纹理路径 ====================
    // key: 正常纹理路径（与模型 JSON 中一致），value: _opaque 纹理路径
    private static final Map<String, String> TEXTURE_MAP = new HashMap<>();

    // 已解析的 _opaque IIcon 缓存：normalTexturePath -> opaqueIicon
    private static final Map<String, IIcon> OPAQUE_ICONS = new HashMap<>();

    // 叶子方块集合（可动态扩展）
    private static final List<Block> LEAF_BLOCKS = new ArrayList<>();

    // 已注册标记
    private static boolean texturesRegistered = false;

    // [S5] 模组自定义树叶方块的纹理映射：Block -> (normalPath -> opaquePath)
    private static final Map<Block, Map<String, String>> CUSTOM_LEAF_TEXTURES = new HashMap<>();

    static {
        // ——— 纹理映射定义 ———
        TEXTURE_MAP.put("minecraft:blocks/leaves_oak", "minecraft:blocks/leaves_oak_opaque");
        TEXTURE_MAP.put("minecraft:blocks/leaves_spruce", "minecraft:blocks/leaves_spruce_opaque");
        TEXTURE_MAP.put("minecraft:blocks/leaves_birch", "minecraft:blocks/leaves_birch_opaque");
        TEXTURE_MAP.put("minecraft:blocks/leaves_jungle", "minecraft:blocks/leaves_jungle_opaque");
        TEXTURE_MAP.put("minecraft:blocks/leaves_acacia", "minecraft:blocks/leaves_acacia_opaque");
        TEXTURE_MAP.put("minecraft:blocks/leaves_big_oak", "minecraft:blocks/leaves_big_oak_opaque");

        // ——— 叶子方块注册 ———
        // Blocks.leaves (1.7.10 vanilla)：oak=0, spruce=1, birch=2, jungle=3
        if (Blocks.leaves != null) {
            LEAF_BLOCKS.add(Blocks.leaves);
        }
        // leaves2（acacia=0, big_oak=1）
        Block leaves2 = getLeaves2Block();
        if (leaves2 != null) {
            LEAF_BLOCKS.add(leaves2);
        }
    }

    public LeavesGraphicsExtension() {
    }

    // ==================== 模组树叶 API [S5] ====================

    /**
     * 注册自定义树叶方块的 _opaque 纹理映射。
     * <p>
     * 模组可在初始化阶段调用此方法，将自定义树叶方块注册到流畅画质处理系统中。
     * 注册后，当画质设为“流畅”时，该树叶方块的纹理将被替换为 _opaque 版本。
     *
     * @param block         树叶方块实例
     * @param textureMapping normal 纹理路径 → _opaque 纹理路径的映射
     *                       （如 {@code "mymod:blocks/custom_leaves" → "mymod:blocks/custom_leaves_opaque"}）
     */
    public static void registerLeafBlock(Block block, Map<String, String> textureMapping) {
        if (block == null || textureMapping == null) return;
        if (!LEAF_BLOCKS.contains(block)) {
            LEAF_BLOCKS.add(block);
        }
        CUSTOM_LEAF_TEXTURES.put(block, new HashMap<>(textureMapping));
        TEXTURE_MAP.putAll(textureMapping);
        // 如果纹理已经注册过，需要重新注册新纹理
        if (texturesRegistered) {
            texturesRegistered = false;
        }
    }

    // ==================== 纹理生命周期 ====================

    /**
     * 在 TextureStitchEvent.Pre 中调用，注册所有 _opaque 纹理。
     */
    public static void registerTextures(TextureMap map) {
        if (texturesRegistered) return;
        texturesRegistered = true;
        for (String opaquePath : TEXTURE_MAP.values()) {
            String iconName = resolveOpaqueName(opaquePath);
            if (iconName != null && !iconName.isEmpty()) {
                map.registerIcon(iconName);
            }
        }
    }

    /**
     * 在 TextureStitchEvent.Post 中调用，解析并缓存 _opaque IIcon。
     */
    public static void onTextureStitchPost(TextureMap map) {
        OPAQUE_ICONS.clear();
        for (Map.Entry<String, String> entry : TEXTURE_MAP.entrySet()) {
            String normalPath = entry.getKey();
            String iconName = resolveOpaqueName(entry.getValue());
            if (iconName != null) {
                IIcon icon = map.getAtlasSprite(iconName);
                if (icon != null) {
                    OPAQUE_ICONS.put(normalPath, icon);
                }
            }
        }
    }

    /**
     * 检查当前画质是否应使用 _opaque 纹理。
     */
    private static boolean shouldUseOpaque() {
        return !Minecraft.getMinecraft().gameSettings.fancyGraphics;
    }

    /**
     * 根据叶子方块和 metadata 获取对应的 _opaque IIcon。
     */
    private static IIcon getOpaqueIcon(Block block, int metadata) {
        if (OPAQUE_ICONS.isEmpty()) return null;

        // 获取该叶子变体的正常纹理路径
        String normalTex = getNormalTexture(block, metadata);
        if (normalTex == null) return null;

        return OPAQUE_ICONS.get(normalTex);
    }

    /**
     * 将纹理路径转换为在 TextureMap 中注册的图标名。
     * 逻辑与 {@code VanillaModelManager.resolveTextureName} 一致。
     */
    private static String resolveOpaqueName(String texturePath) {
        if (texturePath == null) return null;
        String pathPart = texturePath;
        if (texturePath.contains(":")) {
            String namespace = texturePath.substring(0, texturePath.indexOf(':'));
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
        return pathPart;
    }

    /**
     * 根据方块和 metadata 确定正常纹理路径。
     * 优先检查模组自定义树叶映射，再回退到原版 leaves/leaves2。
     */
    private static String getNormalTexture(Block block, int metadata) {
        int meta = metadata & 3;
        // [S5] 检查模组自定义树叶映射
        Map<String, String> customMapping = CUSTOM_LEAF_TEXTURES.get(block);
        if (customMapping != null && !customMapping.isEmpty()) {
            // 返回第一个 normal 纹理路径（自定义树叶通常只有一个变体纹理）
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
        // 兼容 leaves2（1.8+ / 模组添加）
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
     * 安全获取 leaves2 方块（1.7.10 可能不存在，返回 null）。
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
        // 仅在世界渲染阶段生效
        if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
        if (ctx.block == null || ctx.world == null) return;

        // 仅在流畅画质下切换纹理
        if (!shouldUseOpaque()) return;

        // 检查是否是叶子方块
        boolean isLeaf = LEAF_BLOCKS.contains(ctx.block);
        if (!isLeaf) return;

        // 获取 _opaque IIcon
        int metadata = ctx.world.getBlockMetadata(ctx.x, ctx.y, ctx.z);
        IIcon opaqueIcon = getOpaqueIcon(ctx.block, metadata);
        if (opaqueIcon != null) {
            ctx.iconOverride = opaqueIcon;
        }
    }
}
