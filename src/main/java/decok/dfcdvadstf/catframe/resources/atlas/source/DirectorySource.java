package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.resources.atlas.ResourcePackEnumerator;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 目录扫描源（对标 26.1.2 {@code minecraft:directory}）。
 * <p>
 * 扫描所有 namespace 的 {@code textures/<source>/} 顶层 png（1.7.10 复数纹理目录），
 * 产出 sprite id = {@code <ns>:<prefix><basename>}。由于 1.7.10 资源管理器无法列目录，
 * 扫描由 {@link ResourcePackEnumerator} 枚举 classpath/resourcepacks 归档实现；
 * 枚举不可用时返回空列表（模型驱动引用兜底）。
 * <p>
 * 定义 JSON 示例：
 * <pre>{@code {"type": "minecraft:directory", "source": "items", "prefix": "items/"}}</pre>
 *
 * <p>Scans {@code textures/<source>/} under every namespace and emits one
 * sprite ref per PNG, id = {@code ns:prefix+basename}.
 */
@SideOnly(Side.CLIENT)
public final class DirectorySource implements AtlasSource {

    /** 纹理目录名（1.7.10 复数，如 {@code "items"}）。 */
    private final String source;
    /** sprite id 前缀（如 {@code "items/"}，与 1.7.10 模型引用格式一致 —— Wiki 语义：
     * 前缀直接拼在命名空间 ID 路径最前面，{@code source="items"} + {@code prefix="items/"}
     * 对 {@code textures/items/apple.png} 产出 {@code minecraft:items/apple}）。 */
    private final String prefix;

    public DirectorySource(String source, String prefix) {
        this.source = source;
        this.prefix = prefix;
    }

    @Override
    public String type() {
        return "minecraft:directory";
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
        List<SpriteRef> out = new ArrayList<>();
        // 防重：同一 sprite id 可能出现在多个归档（pack 覆盖），只取首个
        Set<String> seen = new HashSet<>();
        String expected = "textures/" + source + "/";
        for (String path : ResourcePackEnumerator.listAssets("assets/")) {
            // assets/<ns>/textures/<source>/<file>.png
            String rest = path.substring("assets/".length());
            int slash = rest.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            String ns = rest.substring(0, slash);
            String tail = rest.substring(slash + 1);
            if (!tail.startsWith(expected) || !tail.endsWith(".png")) {
                continue;
            }
            String middle = tail.substring(expected.length(), tail.length() - 4);
            if (middle.isEmpty() || middle.indexOf('/') >= 0) {
                continue; // 非递归：仅顶层 png
            }
            String spriteId = ns + ":" + prefix + middle;
            if (seen.add(spriteId)) {
                out.add(SpriteRef.of(new ResourceLocation(spriteId)));
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return "DirectorySource{" + source + " -> " + prefix + "}";
    }
}
