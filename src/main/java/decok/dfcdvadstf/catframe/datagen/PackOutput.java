package decok.dfcdvadstf.catframe.datagen;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Pack output: resolves generated resource paths under an asset root.
 * <p>
 * The output root is the {@code assets} directory (e.g.
 * {@code src/main/resources/assets}); every category maps to the exact same
 * layout the runtime loader expects:
 * <ul>
 *   <li>{@code blockstates/&lt;ns&gt;/&lt;name&gt;.json}</li>
 *   <li>{@code models/block/...}, {@code models/item/...}</li>
 *   <li>{@code items/&lt;ns&gt;/&lt;name&gt;.json} (item state decision trees)</li>
 *   <li>{@code tags/blocks/...}, {@code tags/items/...}</li>
 *   <li>{@code atlases/...}, {@code lang/...}</li>
 *   <li>{@code &lt;ns&gt;/model_mappings.json}</li>
 * </ul>
 * <p>
 * 资源包输出：在资产根目录下解析生成资源的路径。
 * 输出根为 {@code assets} 目录（如 {@code src/main/resources/assets}）；
 * 每个类别映射到与运行时加载器完全一致的目录布局。
 */
public class PackOutput {

    /** Resource categories that map to a fixed sub-directory of the asset root. */
    public enum Category {
        BLOCKSTATES("blockstates"),
        MODELS_BLOCK("models/block"),
        MODELS_ITEM("models/item"),
        ITEMS("items"),
        TAGS_BLOCKS("tags/blocks"),
        TAGS_ITEMS("tags/items"),
        ATLASES("atlases"),
        LANG("lang"),
        /** Direct child of the namespace dir: {@code assets/<ns>/model_mappings.json}. */
        MODEL_MAPPINGS("");

        private final String subDir;

        Category(String subDir) {
            this.subDir = subDir;
        }

        /** Relative sub-directory under the namespace directory ("" for namespace root). */
        public String subDir() {
            return subDir;
        }
    }

    private final Path assetRoot;

    /**
     * @param assetRoot the {@code assets} directory (e.g. {@code src/main/resources/assets})
     */
    public PackOutput(Path assetRoot) {
        this.assetRoot = assetRoot;
    }

    /**
     * Create a pack output from a filesystem path string.
     *
     * @param assetRootPath asset root path (may be relative to the working dir)
     */
    public static PackOutput of(String assetRootPath) {
        return new PackOutput(Paths.get(assetRootPath));
    }

    /**
     * Create a pack output from a filesystem path string.
     *
     * @param assetRootFile asset root directory
     */
    public static PackOutput of(File assetRootFile) {
        return new PackOutput(assetRootFile.toPath());
    }

    /**
     * The asset root directory (may not exist yet).
     *
     * @return asset root path
     */
    public Path assetRoot() {
        return assetRoot;
    }

    /**
     * Resolve the output path for a JSON file of the given category.
     *
     * @param namespace resource namespace (e.g. {@code minecraft}, {@code catframe})
     * @param category  resource category
     * @param name      file name without {@code .json} extension
     * @return target path, e.g. {@code <root>/minecraft/blockstates/stone.json}
     */
    public Path json(String namespace, Category category, String name) {
        Path nsDir = assetRoot.resolve(namespace);
        Path dir = category.subDir().isEmpty() ? nsDir : nsDir.resolve(category.subDir());
        return dir.resolve(name + ".json");
    }

    /**
     * Resolve an arbitrary path under a namespace directory.
     * <p>
     * Used for non-JSON or special assets (e.g. Blockbench models copied verbatim).
     *
     * @param namespace resource namespace
     * @param relative  path relative to the namespace directory (forward slashes)
     * @return target path
     */
    public Path namespacePath(String namespace, String relative) {
        return assetRoot.resolve(namespace).resolve(relative.replace('/', File.separatorChar));
    }
}
