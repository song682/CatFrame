package decok.dfcdvadstf.catframe.datagen.asset;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.datagen.CachedOutput;
import decok.dfcdvadstf.catframe.datagen.DataProvider;
import decok.dfcdvadstf.catframe.datagen.PackOutput;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Copies non-generated assets verbatim from the classpath.
 * <p>
 * The Blockbench-sculpted plushy models (bluey/bingo and their inventory
 * variants), the atlas definitions and the language files are hand-crafted
 * assets that make no sense to describe in code — they are copied byte for
 * byte from the pack on the classpath (normally
 * {@code src/main/resources/assets}) to the output root. When the output
 * root equals the source root, the SHA-1 cache makes the copy a no-op.
 * <p>
 * 原样复制非生成类资产。Blockbench 手工雕刻的玩偶模型（bluey/bingo 及其
 * 背包变体）、atlas 定义与语言文件是手工艺品，用代码描述毫无意义——
 * 它们从 classpath 上的资源包（通常是 {@code src/main/resources/assets}）
 * 逐字节复制到输出根。当输出根与源根相同时，SHA-1 缓存使复制变为空操作。
 */
@SideOnly(Side.CLIENT)
public final class AssetCopyProvider implements DataProvider {

    /** [namespace, path-relative-to-namespace] pairs to copy verbatim. */
    private static final String[][] ASSETS = {
            // Blockbench sculpted plushy models (+ static inventory variants)
            {"catframe", "models/item/bluey.json"},
            {"catframe", "models/item/bluey_inventory.json"},
            {"catframe", "models/item/bingo.json"},
            {"catframe", "models/item/bingo_inventory.json"},
            // atlas definitions
            {"catframe", "atlases/gui.json"},
            {"catframe", "atlases/item.json"},
            {"minecraft", "atlases/blocks.json"},
            {"minecraft", "atlases/items.json"},
            // language files
            {"catframe", "lang/en_us.json"},
            {"catframe", "lang/zh_cn.json"},
    };

    private static final ExecutorService POOL = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()), runnable -> {
                Thread t = new Thread(runnable, "CatFrame-Datagen-Copy");
                t.setDaemon(true);
                return t;
            });

    @Override
    public CompletableFuture<?> run(PackOutput output, CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<CompletableFuture<?>>();
        for (String[] asset : ASSETS) {
            final String namespace = asset[0];
            final String relative = asset[1];
            final Path target = output.namespacePath(namespace, relative);
            writes.add(CompletableFuture.runAsync(
                    () -> copy(namespace, relative, target, cache), POOL));
        }
        return CompletableFuture.allOf(writes.toArray(new CompletableFuture<?>[0]));
    }

    /**
     * Read one classpath asset and write it to the target through the cache.
     *
     * @param namespace resource namespace
     * @param relative  path relative to the namespace directory
     * @param target    output path
     * @param cache     output cache
     */
    private static void copy(String namespace, String relative,
                             Path target, CachedOutput cache) {
        String resource = "assets/" + namespace + "/" + relative;
        InputStream in = AssetCopyProvider.class.getClassLoader()
                .getResourceAsStream(resource);
        if (in == null) {
            throw new CachedOutput.DatagenException(
                    "Missing copy source on classpath: " + resource);
        }
        try {
            try {
                cache.writeIfNeeded(target, readAll(in));
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new CachedOutput.DatagenException(
                    "Failed to copy " + resource + ": " + e.getMessage(), e);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    @Override
    public String getName() {
        return "Asset Copies";
    }
}
