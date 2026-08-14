package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 正则过滤源（对标 26.1.2 {@code minecraft:filter}）。
 * <p>
 * 不产出 sprite；收集器对其<b>已收集</b>的 id 按 namespace / path 正则匹配移除
 * （matches 全匹配）。正则缺省 = 匹配全部。仅作用于定义驱动集合，模型驱动引用
 * 不受过滤（模型引用的纹理必须缝合）。
 * <p>
 * 定义 JSON 示例：
 * <pre>{@code {"type": "minecraft:filter", "namespace": "minecraft", "path": "block/.*_debug.*"}}</pre>
 *
 * <p>Regex filter: removes already-collected sprite ids matching namespace/path
 * patterns; never touches model-driven refs.
 */
@SideOnly(Side.CLIENT)
public final class FilterSource implements AtlasSource {

    /** namespace 正则（null = 全部）。 */
    private final Pattern namespace;
    /** path 正则（null = 全部）。 */
    private final Pattern path;

    public FilterSource(String namespaceRegex, String pathRegex) {
        this.namespace = (namespaceRegex == null || namespaceRegex.isEmpty())
                ? null : Pattern.compile(namespaceRegex);
        this.path = (pathRegex == null || pathRegex.isEmpty())
                ? null : Pattern.compile(pathRegex);
    }

    @Override
    public String type() {
        return "minecraft:filter";
    }

    @Override
    public List<SpriteRef> list(IResourceManager manager) {
        return Collections.emptyList();
    }

    @Override
    public boolean removesCollected() {
        return true;
    }

    @Override
    public boolean shouldRemove(String spriteId) {
        try {
            ResourceLocation id = new ResourceLocation(spriteId);
            if (namespace != null && !namespace.matcher(id.getResourceDomain()).matches()) {
                return false;
            }
            return path == null || path.matcher(id.getResourcePath()).matches();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "FilterSource{" + namespace + " / " + path + "}";
    }
}
