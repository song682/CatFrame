package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.ResourceLocation;

/**
 * 定义驱动源的 sprite 引用（对标 26.1.2 {@code SpriteIdentifier}）。
 * <p>
 * 字段语义：
 * <ul>
 *   <li>{@code spriteId} —— 发布键（textureIcons 键 + CatSprite.texturePath），
 *       如 {@code minecraft:block/stone}；</li>
 *   <li>{@code resource} —— 源纹理路径（缺省 = spriteId），decode 时按 1.7.10
 *       复数纹理目录（textures/blocks|items）查找；</li>
 *   <li>{@code atlasId} —— 目标图集覆盖（可 null，由 spriteId 前缀推断
 *       {@code block/} → blocks 图集、{@code item/} → items 图集）；</li>
 *   <li>{@code transform} —— 可选像素变换（unstitch 裁剪 / paletted_permutations
 *       关键色替换）。</li>
 * </ul>
 *
 * <p>Sprite reference produced by definition-driven sources; carries the
 * publish id, the source texture path, an optional atlas override and an
 * optional pixel transform.
 */
@SideOnly(Side.CLIENT)
public final class SpriteRef {

    private final ResourceLocation spriteId;
    private final ResourceLocation resource;
    private final ResourceLocation atlasId;
    private final PixelTransform transform;

    private SpriteRef(ResourceLocation spriteId, ResourceLocation resource,
                      ResourceLocation atlasId, PixelTransform transform) {
        this.spriteId = spriteId;
        this.resource = resource;
        this.atlasId = atlasId;
        this.transform = transform;
    }

    /** spriteId = resource = 给定路径，无图集覆盖、无变换（模型驱动引用即此形态）。 */
    public static SpriteRef of(ResourceLocation spriteId) {
        return new SpriteRef(spriteId, spriteId, null, null);
    }

    /** spriteId 与 resource 分离（single 源重命名场景）。 */
    public static SpriteRef of(ResourceLocation spriteId, ResourceLocation resource) {
        return new SpriteRef(spriteId, resource, null, null);
    }

    /** 完整形态（unstitch / paletted_permutations：目标图集覆盖 + 像素变换）。 */
    public static SpriteRef of(ResourceLocation spriteId, ResourceLocation resource,
                               ResourceLocation atlasId, PixelTransform transform) {
        return new SpriteRef(spriteId, resource, atlasId, transform);
    }

    /** 发布键（textureIcons 键）。 */
    public ResourceLocation spriteId() {
        return spriteId;
    }

    /** 源纹理路径（decode 查找用）。 */
    public ResourceLocation resource() {
        return resource;
    }

    /** 目标图集覆盖（可 null → 由 spriteId 前缀推断）。 */
    public ResourceLocation atlasId() {
        return atlasId;
    }

    /** 像素变换（可 null）。 */
    public PixelTransform transform() {
        return transform;
    }

    @Override
    public String toString() {
        return "SpriteRef{" + spriteId + (resource.equals(spriteId) ? "" : " <- " + resource)
                + (atlasId != null ? " @ " + atlasId : "") + "}";
    }
}
