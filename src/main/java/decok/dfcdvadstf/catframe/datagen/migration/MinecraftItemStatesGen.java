package decok.dfcdvadstf.catframe.datagen.migration;

import com.google.gson.JsonElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.datagen.CachedOutput;
import decok.dfcdvadstf.catframe.datagen.DataGenerator;
import decok.dfcdvadstf.catframe.datagen.DataProvider;
import decok.dfcdvadstf.catframe.datagen.PackOutput;
import decok.dfcdvadstf.catframe.datagen.migration.data.ItemStateRawData;

import java.util.concurrent.CompletableFuture;

/**
 * Generates all 292 minecraft {@code items/} decision-tree files.
 * <p>
 * The extracted data table holds the complete hand-written trees verbatim —
 * direct {@code minecraft:model} leaves, {@code minecraft:select} meta
 * dispatch and {@code minecraft:condition} branches alike. Item trees are
 * re-emitted unchanged because their variety (nested select/condition/
 * range_dispatch compositions) defeats template extraction without any
 * fidelity gain.
 * <p>
 * 生成全部 292 个 minecraft {@code items/} 决策树文件。提取的数据表原样
 * 保存了完整手写树——直接 {@code minecraft:model} 叶子、{@code minecraft:select}
 * meta 分发与 {@code minecraft:condition} 分支一律直出。items 决策树的形态
 * 多样性（嵌套 select/condition/range_dispatch 组合）使模板化提取无收益，
 * 因此原样重写。
 */
@SideOnly(Side.CLIENT)
public final class MinecraftItemStatesGen implements DataProvider {

    @Override
    public CompletableFuture<?> run(PackOutput output, CachedOutput cache) {
        return DataGenerator.saveAll(cache, ItemStateRawData.load(), json -> json,
                key -> output.json("minecraft", PackOutput.Category.ITEMS, key));
    }

    @Override
    public String getName() {
        return "Minecraft ItemStates";
    }
}
