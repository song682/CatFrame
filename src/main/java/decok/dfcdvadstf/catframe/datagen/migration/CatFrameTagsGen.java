package decok.dfcdvadstf.catframe.datagen.migration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.datagen.CachedOutput;
import decok.dfcdvadstf.catframe.datagen.DataGenerator;
import decok.dfcdvadstf.catframe.datagen.DataProvider;
import decok.dfcdvadstf.catframe.datagen.PackOutput;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Generates the 31 catframe block/item tags.
 * <p>
 * Every tag value list lives in {@link TagRules} (extracted from the
 * hand-written files); this provider only routes each entry to the
 * {@code tags/blocks/} or {@code tags/items/} directory.
 * <p>
 * 生成 catframe 的 31 个方块/物品 tag。每个 tag 的值表在 {@link TagRules}
 * （从手写文件提取）；本 provider 只负责把条目路由到
 * {@code tags/blocks/} 或 {@code tags/items/} 目录。
 */
@SideOnly(Side.CLIENT)
public final class CatFrameTagsGen implements DataProvider {

    @Override
    public CompletableFuture<?> run(PackOutput output, CachedOutput cache) {
        Map<String, JsonElement> tags = new LinkedHashMap<String, JsonElement>();
        for (Map.Entry<String, JsonObject> entry : TagRules.all().entrySet()) {
            tags.put(entry.getKey(), entry.getValue());
        }
        return DataGenerator.saveAll(cache, tags, json -> json,
                key -> pathFor(output, key));
    }

    /**
     * Resolve a rule-table key ({@code blocks/...} or {@code items/...})
     * to its tag output path.
     *
     * @param output pack output
     * @param key    rule-table key
     * @return target path
     */
    private static Path pathFor(PackOutput output, String key) {
        if (key.startsWith("blocks/")) {
            return output.json("catframe", PackOutput.Category.TAGS_BLOCKS,
                    key.substring("blocks/".length()));
        }
        if (key.startsWith("items/")) {
            return output.json("catframe", PackOutput.Category.TAGS_ITEMS,
                    key.substring("items/".length()));
        }
        throw new IllegalArgumentException("Unknown tag category: " + key);
    }

    @Override
    public String getName() {
        return "CatFrame Tags";
    }
}
