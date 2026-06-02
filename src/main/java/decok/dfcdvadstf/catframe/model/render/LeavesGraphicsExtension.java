package decok.dfcdvadstf.catframe.model.render;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;

import java.util.HashMap;
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

  // 叶子方块集合
  private static final Block[] LEAF_BLOCKS;

  // 已注册标记
  private static boolean texturesRegistered = false;

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
      LEAF_BLOCKS = new Block[]{Blocks.leaves};
    } else {
      LEAF_BLOCKS = new Block[0];
    }
  }

  public LeavesGraphicsExtension() {}

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
    }
    return pathPart;
  }

  /**
   * 根据方块和 metadata 确定正常纹理路径。
   */
  private static String getNormalTexture(Block block, int metadata) {
    int meta = metadata & 3;
    if (block == Blocks.leaves) {
      switch (meta) {
        case 0:  return "minecraft:blocks/leaves_oak";
        case 1:  return "minecraft:blocks/leaves_spruce";
        case 2:  return "minecraft:blocks/leaves_birch";
        case 3:  return "minecraft:blocks/leaves_jungle";
        default: return "minecraft:blocks/leaves_oak";
      }
    }
    // 兼容 leaves2（1.8+ / 模组添加）
    Block leaves2 = getLeaves2Block();
    if (leaves2 != null && block == leaves2) {
      switch (meta) {
        case 0:  return "minecraft:blocks/leaves_acacia";
        case 1:  return "minecraft:blocks/leaves_big_oak";
        default: return "minecraft:blocks/leaves_acacia";
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
    boolean isLeaf = false;
    for (Block leaf : LEAF_BLOCKS) {
      if (ctx.block == leaf) {
        isLeaf = true;
        break;
      }
    }
    // 也检查 leaves2
    if (!isLeaf) {
      Block leaves2 = getLeaves2Block();
      isLeaf = (leaves2 != null && ctx.block == leaves2);
    }
    if (!isLeaf) return;

    // 获取 _opaque IIcon
    int metadata = ctx.world.getBlockMetadata(ctx.x, ctx.y, ctx.z);
    IIcon opaqueIcon = getOpaqueIcon(ctx.block, metadata);
    if (opaqueIcon != null) {
      ctx.iconOverride = opaqueIcon;
    }
  }
}
