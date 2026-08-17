package decok.dfcdvadstf.catframe.resources.atlas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.resources.atlas.source.AtlasSource;
import decok.dfcdvadstf.catframe.resources.atlas.source.FilterSource;
import decok.dfcdvadstf.catframe.resources.atlas.source.SpriteRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
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
 * [渲染三域架构] 收集分流：加载全部定义后按 atlas id 归属消费 —— blocks / items 定义
 * 喂原版 TextureMap（{@code CatAtlasManager.registerDefinedSprites}），{@code catframe:ui}
 * 定义喂 CatFrame 自建 GUI 图集（{@code UiTextureAtlasManager}，独立事件链）；
 * 其它 atlas id 的定义由各自消费方处理。
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

    /**
     * [渲染三域架构] 定义驱动收集（按 atlas id 归属分流）：取指定图集定义的全部 source
     * 按序产出 sprite ref，id 原样采用（数据驱动键）。
     * <ol>
     *   <li>filter 源命中 → 移除（仅作用于定义驱动集合）；</li>
     *   <li>重复 sprite id → warn + 跳过（先入者胜，同 pack 覆盖语义）；</li>
     *   <li>SpriteRef 的 atlasId 覆盖字段仅 debug 记录（消费方决定是否接受跨图集引用）。</li>
     * </ol>
     * 由 {@code CatAtlasManager.registerDefinedSprites}（原版 blocks/items 输出端）与
     * {@code UiTextureAtlasManager}（{@code catframe:ui} GUI 图集）共用。
     *
     * <p>Definition-driven collection for one atlas id: sources emit sprite refs
     * in order, filters remove, duplicates warn-and-skip (first wins).
     *
     * @param atlasId 图集 id（{@code <ns>:<id>}，如 {@code minecraft:blocks} / {@code catframe:ui}）
     * @return 去重后的 sprite 引用列表（定义缺失时为空）
     */
    public static List<SpriteRef> collectRefs(String atlasId) {
        Map<String, List<AtlasSource>> defs = loadAll();
        List<AtlasSource> sources = defs.get(atlasId);
        LinkedHashMap<String, SpriteRef> merged = new LinkedHashMap<>();
        if (sources != null) {
            for (AtlasSource source : sources) {
                List<SpriteRef> refs;
                try {
                    refs = source.list(Minecraft.getMinecraft().getResourceManager());
                } catch (RuntimeException e) {
                    CatFrame.logger.warn("[AtlasDefinition] source '{}' in atlas '{}' failed, skipping: {}",
                            source.type(), atlasId, e.getMessage());
                    continue;
                }
                for (SpriteRef ref : refs) {
                    String spriteId = ref.spriteId().toString();
                    if (isFiltered(sources, spriteId)) {
                        continue;
                    }
                    if (merged.containsKey(spriteId)) {
                        CatFrame.logger.warn(
                                "[AtlasDefinition] duplicate sprite '{}' in atlas '{}': earlier entry wins, later skipped",
                                spriteId, atlasId);
                        continue;
                    }
                    if (ref.atlasId() != null && !atlasId.equals(ref.atlasId().toString())) {
                        CatFrame.logger.debug("[AtlasDefinition] sprite '{}' targets atlas '{}' != current '{}', "
                                        + "kept in current atlas (consumer decides whether to accept)",
                                spriteId, ref.atlasId(), atlasId);
                    }
                    merged.put(spriteId, ref);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** 定义驱动集合中是否被任一 filter 源命中移除。 */
    private static boolean isFiltered(List<AtlasSource> sources, String spriteId) {
        for (AtlasSource source : sources) {
            if (source instanceof FilterSource && source.shouldRemove(spriteId)) {
                CatFrame.logger.debug("[AtlasDefinition] filter removed '{}'", spriteId);
                return true;
            }
        }
        return false;
    }
}
