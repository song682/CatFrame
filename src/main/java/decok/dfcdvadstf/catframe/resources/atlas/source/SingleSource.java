package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;

/**
 * 单纹理源（对标 26.1.2 {@code minecraft:single}）。
 * <p>
 * 把单个纹理以（可选）重命名后的 sprite id 收入图集 —— 适合未建模引用的散落纹理，
 * 或为 unstitch / paletted_permutations 提供显式入口。
 * <p>
 * 定义 JSON 示例：
 * <pre>{@code {"type": "minecraft:single", "resource": "minecraft:item/foo", "sprite": "minecraft:item/bar"}}</pre>
 * {@code sprite} 缺省时 sprite id = resource。
 *
 * <p>Adds one texture, optionally under a renamed sprite id.
 */
@SideOnly(Side.CLIENT)
public final class SingleSource implements AtlasSource {

    /** 源纹理路径。 */
    private final ResourceLocation resource;
    /** 发布 id（可 null → = resource）。 */
    private final ResourceLocation sprite;

    public SingleSource(ResourceLocation resource, ResourceLocation sprite) {
        this.resource = resource;
        this.sprite = sprite;
    }

    @Override
    public String type() {
        return "minecraft:single";
    }

    @Override
    public boolean removesCollected() {
        return false;
    }

    @Override
    public boolean shouldRemove(String spriteId) {
        return false;
    }

    @Override
    public List<SpriteRef> list(IResourceManager manager) {
        ResourceLocation id = sprite != null ? sprite : resource;
        return Collections.singletonList(SpriteRef.of(id, resource));
    }

    @Override
    public String toString() {
        return "SingleSource{" + resource + (sprite != null ? " as " + sprite : "") + "}";
    }
}
