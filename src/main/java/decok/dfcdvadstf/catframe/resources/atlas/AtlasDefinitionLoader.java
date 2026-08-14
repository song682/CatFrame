package decok.dfcdvadstf.catframe.resources.atlas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.resources.atlas.source.AtlasSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图集定义发现器 —— 枚举所有可达归档中的 {@code assets/<namespace>/atlases/<id>.json}
 * 并解码（命名空间驱动注册，设计文档确认决策）。
 * <p>
 * 目录名与 Wiki 一致为复数 {@code atlases/}（原版定义文件即
 * {@code assets/minecraft/atlases/blocks.json} → 图集 id {@code minecraft:blocks}）。
 * 枚举用 {@link ResourcePackEnumerator}（classpath + resourcepacks），同 id 定义
 * 跨资源包合并语义：内容一律经 {@code getResource} 读取（自动取最高优先级版本），
 * 故同 id 多次 put 的内容一致，天然满足「高优先级包覆盖同定义文件」的 Wiki 语义。
 * <p>
 * 目前 CatFrame 渲染分组只绑定 blocks/items 两个图集，其它 atlas id 的定义
 * 仅记 debug 日志（消费方决定忽略或扩展渲染分组）。
 *
 * <p>Discovers and decodes atlas definition JSONs across all namespaces;
 * same-id definitions merge by pack priority through the resource manager.
 */
@SideOnly(Side.CLIENT)
public final class AtlasDefinitionLoader {

    private AtlasDefinitionLoader() {
    }

    /**
     * 加载全部图集定义。
     *
     * @return atlasId（{@code <ns>:<id>}）→ 已解码的 source 列表
     */
    public static Map<String, List<AtlasSource>> loadAll() {
        Map<String, List<AtlasSource>> defs = new LinkedHashMap<>();
        IResourceManager mgr = Minecraft.getMinecraft().getResourceManager();
        for (String path : ResourcePackEnumerator.listAssets("assets/")) {
            // assets/<ns>/atlases/<id>.json（顶层，不递归子目录）
            if (!path.endsWith(".json")) {
                continue;
            }
            int s1 = path.indexOf('/');
            int s2 = path.indexOf('/', s1 + 1);
            int s3 = path.indexOf('/', s2 + 1);
            if (s1 <= 0 || s2 <= 0 || s3 <= 0) {
                continue;
            }
            String ns = path.substring(s1 + 1, s2);
            String dir = path.substring(s2 + 1, s3);
            if (!"atlases".equals(dir)) {
                continue;
            }
            String id = path.substring(s3 + 1, path.length() - ".json".length());
            if (id.isEmpty() || id.indexOf('/') >= 0) {
                continue;
            }
            String atlasId = ns + ":" + id;
            ResourceLocation rl = new ResourceLocation(ns, "atlases/" + id + ".json");
            try {
                IResource res = mgr.getResource(rl);
                List<AtlasSource> sources = AtlasDecoder.decode(res.getInputStream());
                defs.put(atlasId, sources);
                CatFrame.logger.info("[AtlasDefinition] loaded '{}' ({} sources)", atlasId, sources.size());
            } catch (IOException e) {
                CatFrame.logger.warn("[AtlasDefinition] failed to load '{}': {}", rl, e.getMessage());
            }
        }
        return defs;
    }
}
