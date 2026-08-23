package decok.dfcdvadstf.catframe.datagen.migration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.datagen.CachedOutput;
import decok.dfcdvadstf.catframe.datagen.DataGenerator;
import decok.dfcdvadstf.catframe.datagen.DataProvider;
import decok.dfcdvadstf.catframe.datagen.PackOutput;
import decok.dfcdvadstf.catframe.datagen.migration.data.ModelBlockRawData;
import decok.dfcdvadstf.catframe.datagen.migration.data.ModelItemRawData;
import decok.dfcdvadstf.catframe.datagen.model.ModelTemplates;
import decok.dfcdvadstf.catframe.datagen.model.ModelTemplates.Template;
import decok.dfcdvadstf.catframe.datagen.util.JsonWriter;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Generates all 909 minecraft model files (643 block + 266 item).
 * <p>
 * Every model is emitted through {@link ModelTemplates} when it matches a
 * template exactly — the JSON is nothing but {@code parent} + {@code textures}
 * and the parent equals one of the template parents (looked up through a
 * reverse index, no hand-maintained mapping table). The remaining irregular
 * models (custom elements, display transforms, odd parents) fall back to the
 * extracted data tables verbatim. Either path produces a semantically
 * identical tree to the hand-written file, so a diff validator can prove
 * zero drift.
 * <p>
 * 生成全部 909 个 minecraft 模型文件（643 个 block + 266 个 item）。
 * 当模型与模板完全匹配时——JSON 仅含 {@code parent} + {@code textures} 两键
 * 且 parent 命中模板 parent（通过反向索引查找，无需手写映射表）——经
 * {@link ModelTemplates} 发射；其余不规则模型（自定义 elements、display
 * 变换、非常规 parent）回退到提取的数据表原样写出。两条路径产出的树都与
 * 手写文件语义等价，diff 校验器可证明零漂移。
 */
@SideOnly(Side.CLIENT)
public final class MinecraftModelsGen implements DataProvider {

    private static final Logger LOGGER = LogManager.getLogger("CatFrame/Datagen");

    /** parent path → template, built once from the enum (no manual mapping). */
    private static final Map<String, Template> BY_PARENT = new LinkedHashMap<String, Template>();
    static {
        for (Template template : Template.values()) {
            BY_PARENT.put(template.parent(), template);
        }
    }

    @Override
    public CompletableFuture<?> run(PackOutput output, CachedOutput cache) {
        Map<String, JsonElement> models = new LinkedHashMap<String, JsonElement>();
        int templated = 0;
        int raw = 0;
        for (Map.Entry<String, JsonElement> entry : ModelBlockRawData.load().entrySet()) {
            JsonElement rebuilt = tryTemplate(entry.getValue());
            if (rebuilt != null) {
                models.put(entry.getKey(), rebuilt);
                templated++;
            } else {
                models.put(entry.getKey(), entry.getValue());
                raw++;
            }
        }
        for (Map.Entry<String, JsonElement> entry : ModelItemRawData.load().entrySet()) {
            JsonElement rebuilt = tryTemplate(entry.getValue());
            if (rebuilt != null) {
                models.put(entry.getKey(), rebuilt);
                templated++;
            } else {
                models.put(entry.getKey(), entry.getValue());
                raw++;
            }
        }
        LOGGER.info("[datagen] models: " + models.size() + " total, " + templated
                + " via templates, " + raw + " verbatim");
        return DataGenerator.saveAll(cache, models, json -> json,
                key -> pathFor(output, key));
    }

    /**
     * Rebuild a model through its template when the JSON is exactly
     * {@code {"parent": <template parent>, "textures": {...}}}.
     *
     * @param json parsed model tree from the data table
     * @return template-built tree, or {@code null} if the model does not match
     */
    private static JsonElement tryTemplate(JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            return null;
        }
        JsonObject obj = json.getAsJsonObject();
        if (obj.entrySet().size() != 2) {
            return null;
        }
        JsonElement parentEl = obj.get("parent");
        JsonElement texturesEl = obj.get("textures");
        if (parentEl == null || !parentEl.isJsonPrimitive()
                || texturesEl == null || !texturesEl.isJsonObject()) {
            return null;
        }
        Template template = BY_PARENT.get(parentEl.getAsString());
        if (template == null) {
            return null;
        }
        Map<String, String> textures = new LinkedHashMap<String, String>();
        for (Map.Entry<String, JsonElement> slot : texturesEl.getAsJsonObject().entrySet()) {
            if (slot.getValue().isJsonPrimitive()) {
                textures.put(slot.getKey(), slot.getValue().getAsString());
            } else {
                return null; // non-string texture value: not a plain template case
            }
        }
        ModelJson model = ModelTemplates.create(template, textures);
        return JsonWriter.toJsonElement(model);
    }

    /**
     * Resolve a data-table key ({@code block/...} or {@code item/...}) to its
     * model output path.
     *
     * @param output pack output
     * @param key    data-table key
     * @return target path
     */
    private static java.nio.file.Path pathFor(PackOutput output, String key) {
        if (key.startsWith("block/")) {
            return output.json("minecraft", PackOutput.Category.MODELS_BLOCK,
                    key.substring("block/".length()));
        }
        if (key.startsWith("item/")) {
            return output.json("minecraft", PackOutput.Category.MODELS_ITEM,
                    key.substring("item/".length()));
        }
        throw new IllegalArgumentException("Unknown model category: " + key);
    }

    @Override
    public String getName() {
        return "Minecraft Models";
    }
}
