package decok.dfcdvadstf.catframe.datagen.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Semantic diff validator for migration acceptance.
 * <p>
 * Compares every JSON file under a generated root against its counterpart in
 * the existing asset root: both sides are parsed and compared recursively with
 * object key order ignored and formatting ignored, so a reformatted but
 * semantically identical file passes. The first mismatch per file is reported
 * with a JSON path (e.g. {@code variants["facing=east"].model}) so generation
 * descriptions can be fixed without eyeballing full dumps.
 * <p>
 * Files present in the generated root but missing from the existing root are
 * reported as {@code added} (new assets are legal — the migration may extend
 * coverage — but they must be reviewed). Files present only in the existing
 * root are ignored: textures, lang extras and other non-generated assets
 * legitimately live outside the generation set.
 * <p>
 * 迁移验收用的语义 diff 校验器。将生成根目录下每个 JSON 文件与现有资产根
 * 目录中的对应文件对比：两侧解析后递归比较，忽略对象键序与格式，
 * 因此「重新格式化但语义相同」的文件可通过。每个文件的首个差异以 JSON
 * 路径报告（如 {@code variants["facing=east"].model}），便于修正生成描述
 * 而无需肉眼核对完整转储。
 * 生成根有而现有根缺失的文件记为 {@code added}（新增资产合法——迁移可能
 * 扩展覆盖——但必须人工复核）。仅在现有根存在的文件被忽略：纹理、语言
 * 附加项等非生成资产本就该留在生成集之外。
 */
public final class DiffValidator {

    private DiffValidator() {
    }

    /**
     * One file-level difference.
     */
    public static final class Diff {

        /** Relative path of the file (forward slashes). */
        public final String file;

        /** First semantic mismatch with a JSON path, e.g. {@code textures.all}. */
        public final String detail;

        Diff(String file, String detail) {
            this.file = file;
            this.detail = detail;
        }
    }

    /**
     * Comparison outcome of one validation run.
     */
    public static final class Result {

        /** Files that are semantically identical to the existing ones. */
        public final List<String> identical = new ArrayList<String>();

        /** Files whose semantic content differs (with the first mismatch). */
        public final List<Diff> differ = new ArrayList<Diff>();

        /** Files generated but absent from the existing root. */
        public final List<String> added = new ArrayList<String>();

        /**
         * @return {@code true} when no file differs (added files allowed)
         */
        public boolean pass() {
            return differ.isEmpty();
        }

        /**
         * @return total number of files compared
         */
        public int total() {
            return identical.size() + differ.size() + added.size();
        }
    }

    /**
     * Compare every JSON file under the generated root with its counterpart
     * under the existing root.
     *
     * @param generatedRoot generated output root (e.g. staging directory)
     * @param existingRoot  the hand-written asset root to validate against
     * @return comparison result
     */
    public static Result compare(Path generatedRoot, Path existingRoot) {
        Result result = new Result();
        List<Path> files = listJson(generatedRoot);
        for (Path file : files) {
            String relative = generatedRoot.relativize(file).toString()
                    .replace('\\', '/');
            Path existing = existingRoot.resolve(relative);
            if (!Files.isRegularFile(existing)) {
                result.added.add(relative);
                continue;
            }
            String generatedText = read(file);
            String existingText = read(existing);
            String mismatch = firstMismatch(generatedText, existingText);
            if (mismatch == null) {
                result.identical.add(relative);
            } else {
                result.differ.add(new Diff(relative, mismatch));
            }
        }
        return result;
    }

    /**
     * Recursively collect all {@code *.json} files under a root (any depth).
     *
     * @param root directory to scan
     * @return JSON file paths, sorted for deterministic output
     */
    private static List<Path> listJson(Path root) {
        List<Path> out = new ArrayList<Path>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try {
            java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(root);
            try {
                for (Path child : stream) {
                    if (Files.isDirectory(child)) {
                        out.addAll(listJson(child));
                    } else if (child.getFileName().toString().endsWith(".json")) {
                        out.add(child);
                    }
                }
            } finally {
                stream.close();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan " + root + ": " + e.getMessage(), e);
        }
        out.sort(null);
        return out;
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parse both texts and return the first semantic mismatch, or {@code null}
     * when they are semantically identical.
     *
     * @param generated generated file text
     * @param existing  existing file text
     * @return mismatch description or {@code null}
     */
    private static String firstMismatch(String generated, String existing) {
        JsonElement generatedJson;
        JsonElement existingJson;
        try {
            generatedJson = new JsonParser().parse(generated);
        } catch (Exception e) {
            return "generated file is not valid JSON: " + e.getMessage();
        }
        try {
            existingJson = new JsonParser().parse(existing);
        } catch (Exception e) {
            return "existing file is not valid JSON: " + e.getMessage();
        }
        return firstDiff(generatedJson, existingJson, "$");
    }

    /**
     * Collect the keys of a JSON object (Gson 2.2.4 has no {@code keySet()}).
     *
     * @param obj object
     * @return sorted key set
     */
    private static Set<String> keys(JsonObject obj) {
        Set<String> out = new TreeSet<String>();
        for (java.util.Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            out.add(entry.getKey());
        }
        return out;
    }

    /**
     * Recursively compare two JSON trees; returns the first difference as a
     * JSON-path description or {@code null} when equal.
     * <p>
     * Object key order is irrelevant (each key is compared by name); array
     * order matters; primitives compare by their exact textual form so numeric
     * representation differences ({@code 1} vs {@code 1.0}) are caught.
     *
     * @param a    generated-side node
     * @param b    existing-side node
     * @param path current JSON path (for the error message)
     * @return mismatch description or {@code null}
     */
    private static String firstDiff(JsonElement a, JsonElement b, String path) {
        if (a == null || b == null) {
            return path + ": only one side has a value";
        }
        if (a.isJsonObject() && b.isJsonObject()) {
            JsonObject oa = a.getAsJsonObject();
            JsonObject ob = b.getAsJsonObject();
            Set<String> keysA = keys(oa);
            Set<String> keysB = keys(ob);
            if (!keysA.equals(keysB)) {
                Set<String> onlyA = new TreeSet<String>(keysA);
                onlyA.removeAll(keysB);
                Set<String> onlyB = new TreeSet<String>(keysB);
                onlyB.removeAll(keysA);
                return path + ": key sets differ (only generated: " + onlyA
                        + ", only existing: " + onlyB + ")";
            }
            for (String key : keysA) {
                String mismatch = firstDiff(oa.get(key), ob.get(key),
                        path + "." + key);
                if (mismatch != null) {
                    return mismatch;
                }
            }
            return null;
        }
        if (a.isJsonArray() && b.isJsonArray()) {
            JsonArray aa = a.getAsJsonArray();
            JsonArray ab = b.getAsJsonArray();
            if (aa.size() != ab.size()) {
                return path + ": array size " + aa.size() + " vs " + ab.size();
            }
            for (int i = 0; i < aa.size(); i++) {
                String mismatch = firstDiff(aa.get(i), ab.get(i),
                        path + "[" + i + "]");
                if (mismatch != null) {
                    return mismatch;
                }
            }
            return null;
        }
        if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive pa = a.getAsJsonPrimitive();
            com.google.gson.JsonPrimitive pb = b.getAsJsonPrimitive();
            if (pa.isNumber() && pb.isNumber()) {
                // Compare numerically so literal-form differences like
                // 0.4 vs 0.40 pass (both load as the same float).
                if (pa.getAsNumber().doubleValue() != pb.getAsNumber().doubleValue()) {
                    return path + ": generated " + a + " vs existing " + b;
                }
                return null;
            }
            if (!a.toString().equals(b.toString())) {
                return path + ": generated " + a + " vs existing " + b;
            }
            return null;
        }
        return path + ": node type differs (generated "
                + a.getClass().getSimpleName() + " vs existing "
                + b.getClass().getSimpleName() + ")";
    }
}
