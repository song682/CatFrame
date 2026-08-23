package decok.dfcdvadstf.catframe.datagen.migration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.datagen.CachedOutput;
import decok.dfcdvadstf.catframe.datagen.DataGenerator;
import decok.dfcdvadstf.catframe.datagen.DataProvider;
import decok.dfcdvadstf.catframe.datagen.PackOutput;
import decok.dfcdvadstf.catframe.datagen.item.ItemStateBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Generates the catframe {@code items/} decision trees (the two Blockbench
 * plushies).
 * <p>
 * Both plushies share the hand/inventory split pattern: first/third-person
 * hand contexts render the sculpted Blockbench model, every other display
 * context falls back to the static inventory model. Built with the
 * {@link ItemStateBuilder} DSL so future plushies can be registered with one
 * line.
 * <p>
 * 生成 catframe 的 {@code items/} 决策树（两个 Blockbench 玩偶）。
 * 两个玩偶共用「手持/背包」分流模式：第一/第三人称手持上下文渲染雕刻版
 * Blockbench 模型，其余显示上下文回退到静态背包模型。用
 * {@link ItemStateBuilder} DSL 构建，未来新增玩偶只需一行注册。
 */
@SideOnly(Side.CLIENT)
public final class CatFrameItemStatesGen implements DataProvider {

    @Override
    public CompletableFuture<?> run(PackOutput output, CachedOutput cache) {
        Map<String, JsonElement> items = new LinkedHashMap<String, JsonElement>();
        items.put("bluey_plushy", plushy("bluey"));
        items.put("bingo_plushy", plushy("bingo"));
        return DataGenerator.saveAll(cache, items, json -> json,
                key -> output.json("catframe", PackOutput.Category.ITEMS, key));
    }

    /**
     * Build one plushy decision tree: hand contexts use the sculpted model,
     * everything else uses the inventory model.
     *
     * @param name plushy base name ({@code bluey} or {@code bingo})
     * @return the file tree
     */
    private static JsonObject plushy(String name) {
        return ItemStateBuilder.of(ItemStateBuilder.displayContextSelect(
                "catframe:item/" + name, "catframe:item/" + name + "_inventory"))
                .build();
    }

    @Override
    public String getName() {
        return "CatFrame ItemStates";
    }
}
