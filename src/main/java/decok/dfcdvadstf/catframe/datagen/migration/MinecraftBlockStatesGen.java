package decok.dfcdvadstf.catframe.datagen.migration;

import com.google.gson.JsonElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.datagen.CachedOutput;
import decok.dfcdvadstf.catframe.datagen.DataGenerator;
import decok.dfcdvadstf.catframe.datagen.DataProvider;
import decok.dfcdvadstf.catframe.datagen.PackOutput;
import decok.dfcdvadstf.catframe.datagen.migration.data.BlockstateRawData;
import decok.dfcdvadstf.catframe.datagen.util.JsonWriter;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * Generates all 144 minecraft blockstate files.
 * <p>
 * Two sources are merged: {@link BlockstateRules} routes the 99 structurally
 * regular files through the {@code BlockStateBuilders} presets, and
 * {@link BlockstateRawData} emits the remaining 45 irregular ones verbatim
 * (multipart trees, weighted variants, unusual key matrices).
 * <p>
 * The provider also performs the modern-datagen "validate" pass: it walks
 * {@link Block#blockRegistry} and reports registered blocks that have no
 * generation rule (potential misses) as a single summary warning instead of
 * spamming the log.
 * <p>
 * 生成全部 144 个 minecraft blockstate 文件。两个来源合并：
 * {@link BlockstateRules} 把 99 个结构规整的文件路由到
 * {@code BlockStateBuilders} 预设；{@link BlockstateRawData} 把其余 45 个
 * 不规则文件原样写出（multipart 树、加权变体、非常规键矩阵）。
 * 本 provider 同时执行高版本 datagen 的 "validate" 校验：
 * 遍历 {@link Block#blockRegistry}，把「已注册但无生成规则」的方块
 * 汇总为一条警告而非刷屏。
 */
@SideOnly(Side.CLIENT)
public final class MinecraftBlockStatesGen implements DataProvider {

    private static final Logger LOGGER = LogManager.getLogger("CatFrame/Datagen");

    @Override
    public CompletableFuture<?> run(PackOutput output, CachedOutput cache) {
        Map<String, JsonElement> states = new LinkedHashMap<String, JsonElement>();
        for (Map.Entry<String, BlockstateJson> entry : BlockstateRules.all().entrySet()) {
            states.put(entry.getKey(), JsonWriter.toJsonElement(entry.getValue()));
        }
        for (Map.Entry<String, JsonElement> entry : BlockstateRawData.load().entrySet()) {
            if (!states.containsKey(entry.getKey())) {
                states.put(entry.getKey(), entry.getValue());
            }
        }
        validateCoverage(states.keySet());
        return DataGenerator.saveAll(cache, states, json -> json,
                key -> output.json("minecraft", PackOutput.Category.BLOCKSTATES, key));
    }

    /**
     * Cross-check the generated set against the live block registry.
     * <p>
     * Registered {@code minecraft:} blocks without a generation rule are
     * aggregated into one warning (technical blocks like {@code air} or
     * {@code flowing_water} are expected there); generation is not blocked,
     * the registry only informs.
     *
     * @param generated names of blockstates this run will produce
     */
    private static void validateCoverage(Set<String> generated) {
        Set<String> missing = new TreeSet<String>();
        try {
            for (Object keyObj : Block.blockRegistry.getKeys()) {
                ResourceLocation key = (ResourceLocation) keyObj;
                if (!"minecraft".equals(key.getResourceDomain())) {
                    continue;
                }
                if (!generated.contains(key.getResourcePath())) {
                    missing.add(key.getResourcePath());
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[datagen] block registry unavailable, coverage check skipped: "
                    + t.getMessage());
            return;
        }
        if (!missing.isEmpty()) {
            LOGGER.warn("[datagen] " + missing.size()
                    + " registered minecraft blocks have no blockstate rule: "
                    + missing);
        }
    }

    @Override
    public String getName() {
        return "Minecraft BlockStates";
    }
}
