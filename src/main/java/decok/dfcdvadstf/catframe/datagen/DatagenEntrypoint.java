package decok.dfcdvadstf.catframe.datagen;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.datagen.util.DiffValidator;
import decok.dfcdvadstf.catframe.datagen.util.DiffValidator.Diff;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Datagen mode entry point — the single execution endpoint for resource
 * generation, and the future direct hook for a Gradle datagen task.
 * <p>
 * Enabled by the JVM property {@code -Dcatframe.datagen}:
 * <ul>
 *   <li>{@code -Dcatframe.datagen} / {@code -Dcatframe.datagen=true} → write to
 *       the default asset root ({@code src/main/resources/assets})</li>
 *   <li>{@code -Dcatframe.datagen=<dir>} → write to the given asset root</li>
 * </ul>
 * When enabled the mod runs all providers during FML init (client side), prints
 * a per-provider summary, then {@link System#exit(int)}s with 0 on success or
 * non-zero on failure so a Gradle {@code JavaExec} task can gate on the exit
 * code. No UI / window dependency, headless-friendly.
 * <p>
 * datagen 模式统一执行端点 —— 资源生成的唯一入口，也是将来 Gradle datagen
 * 任务的直连钩子。由 JVM 属性 {@code -Dcatframe.datagen} 开启：
 * <ul>
 *   <li>{@code -Dcatframe.datagen} / {@code -Dcatframe.datagen=true} → 写入默认
 *       资产根目录（{@code src/main/resources/assets}）</li>
 *   <li>{@code -Dcatframe.datagen=<dir>} → 写入指定资产根目录</li>
 * </ul>
 * 开启后模组在 FML init 阶段（客户端）运行全部 provider，打印逐 provider
 * 汇总，然后以 0（成功）/ 非 0（失败）退出码 {@link System#exit(int)}，
 * 供 Gradle {@code JavaExec} 任务按退出码判定。零 UI/窗口依赖，headless 友好。
 */
@SideOnly(Side.CLIENT)
public final class DatagenEntrypoint {

    /** JVM property that enables datagen mode. */
    public static final String PROPERTY = "catframe.datagen";

    /** Default asset root when the property value is {@code true} (or empty). */
    public static final String DEFAULT_ASSET_ROOT = "src/main/resources/assets";

    /** Whether datagen mode is active for this run. */
    public static boolean ENABLED = false;

    /** Resolved asset output root (valid when {@link #ENABLED}). */
    public static String OUTPUT_DIR = DEFAULT_ASSET_ROOT;

    private DatagenEntrypoint() {
    }

    /**
     * Inspect the JVM property and arm datagen mode if present.
     * Idempotent; safe to call multiple times.
     */
    public static void parseSystemProperty() {
        String value = System.getProperty(PROPERTY);
        if (value == null) return;
        if (value.isEmpty() || value.equalsIgnoreCase("true")) {
            ENABLED = true;
            OUTPUT_DIR = DEFAULT_ASSET_ROOT;
        } else {
            ENABLED = true;
            OUTPUT_DIR = value;
        }
    }

    /**
     * Run datagen if enabled by {@code -Dcatframe.datagen}, then exit the JVM.
     * <p>
     * When the property is absent this is a no-op, so normal game launches are
     * completely unaffected. When armed, providers run synchronously and the
     * process exits with 0 on success / 1 on failure — this is the contract a
     * Gradle datagen task (JavaExec) relies on.
     * <p>
     * Invoke from {@code ClientProxy.init()} (client side only).
     */
    public static void tryRun() {
        parseSystemProperty();
        if (!ENABLED) return;

        Path assetRoot = Paths.get(OUTPUT_DIR);
        CatFrame.logger.info("[CatFrame-Datagen] running providers, output = {}", assetRoot.toAbsolutePath());

        DataGenerator generator = new DataGenerator();
        DatagenProviders.registerAll(generator);
        DataGenerator.RunResult result = generator.runAll(assetRoot);

        for (DataGenerator.ProviderStat stat : result.providers) {
            if (stat.error != null) {
                CatFrame.logger.error("[CatFrame-Datagen] provider '{}' FAILED after {} ms: {}",
                        stat.name, stat.millis, stat.error.toString(), stat.error);
            } else {
                CatFrame.logger.info("[CatFrame-Datagen] provider '{}': {} written, {} skipped, {} ms",
                        stat.name, stat.written, stat.skipped, stat.millis);
            }
        }
        CatFrame.logger.info("[CatFrame-Datagen] total: {} written, {} skipped",
                result.totalWritten(), result.totalSkipped());

        boolean success = result.success();
        if (success && !isDefaultOutput()) {
            success = validateAgainstDefault(assetRoot);
        }

        exit(success ? 0 : 1);
    }

    /**
     * Terminate the JVM with the datagen exit code.
     * <p>
     * A direct {@link System#exit(int)} would be trapped by Forge's
     * {@code TerminalTransformer} (it rewrites {@code System.exit} calls in
     * mod code and throws {@code ExitTrappedException} unless the call
     * originates from {@code cpw.mods.fml.*}). {@code FMLCommonHandler.exitJava}
     * lives inside the untransformed FML class itself, so its native
     * {@link Runtime#halt(int)} path exits cleanly with the code intact — the
     * contract Gradle tasks rely on.
     *
     * @param code exit code (0 = success, non-zero = failure)
     */
    private static void exit(int code) {
        FMLCommonHandler.instance().exitJava(code, true);
    }

    /**
     * @return whether the configured output is the default asset root
     */
    private static boolean isDefaultOutput() {
        return OUTPUT_DIR.equals(DEFAULT_ASSET_ROOT);
    }

    /**
     * Migration acceptance: compare a staging output against the default
     * hand-written asset root. Every generated file must be semantically
     * identical to its existing counterpart; new files are allowed but
     * reported. Any semantic difference fails the run (non-zero exit code),
     * so the staging run doubles as the diff gate before overwriting.
     *
     * @param stagingRoot the generated staging output root
     * @return {@code true} when every generated file matches the existing root
     */
    private static boolean validateAgainstDefault(Path stagingRoot) {
        DiffValidator.Result diff = DiffValidator.compare(stagingRoot,
                resolveDefaultAssetRoot());
        for (String file : diff.added) {
            CatFrame.logger.warn("[CatFrame-Datagen] new file (not in existing assets): {}", file);
        }
        for (Diff d : diff.differ) {
            CatFrame.logger.error("[CatFrame-Datagen] DIFF {} : {}", d.file, d.detail);
        }
        CatFrame.logger.info("[CatFrame-Datagen] diff: {} identical, {} differ, {} added",
                diff.identical.size(), diff.differ.size(), diff.added.size());
        return diff.pass();
    }

    /**
     * Resolve the default asset root against the working directory.
     * <p>
     * Plain JavaExec datagen tasks run with the project directory as working
     * directory, but ForgeGradle 1.2's {@code runClient} runs the game from
     * the {@code runDir} ({@code .minecraft}). Try the working directory
     * first, then its parent (the project root) — whichever actually contains
     * the asset root wins.
     *
     * @return the existing default asset root (or the direct candidate when
     *         neither exists, letting provider errors surface the problem)
     */
    private static Path resolveDefaultAssetRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path direct = cwd.resolve(DEFAULT_ASSET_ROOT);
        if (Files.isDirectory(direct)) {
            return direct;
        }
        if (cwd.getParent() != null) {
            Path viaParent = cwd.getParent().resolve(DEFAULT_ASSET_ROOT);
            if (Files.isDirectory(viaParent)) {
                return viaParent;
            }
        }
        return direct;
    }
}
