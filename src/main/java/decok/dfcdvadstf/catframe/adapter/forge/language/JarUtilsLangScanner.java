package decok.dfcdvadstf.catframe.adapter.forge.language;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.Tags;
import io.qzz.dfdvdsf.jarfile.JarUtil;
import io.qzz.dfdvdsf.jarfile.UrlBuffered;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Optional JarUtils integration — consumes the resource index that JarUtils
 * builds by concurrently scanning every mod jar (at its own post-init),
 * picks out other mods' JSON lang files ({@code assets/<domain>/lang/xx_xx.json},
 * all-lowercase codes) and injects them into Forge's {@code LanguageRegistry}
 * via {@link LanguageRegister#injectExternal}. Vanilla 1.7.10 only understands
 * legacy {@code .lang} properties files, so modern JSON translations of other
 * mods would otherwise go unnoticed.
 * <p>
 * 可选的 JarUtils 集成 —— 消费 JarUtils 在其 post-init 阶段并发扫描全体模组
 * jar 建成的资源索引，挑出其它模组的 JSON 语言文件
 * （{@code assets/<domain>/lang/xx_xx.json}，全小写语言码），经
 * {@link LanguageRegister#injectExternal} 注入 Forge {@code LanguageRegistry}。
 * 原版 1.7.10 只认旧式 {@code .lang} properties 文件，其它模组的新式 JSON
 * 翻译若不经过这里就会被无视。
 * <p>
 * All references to JarUtils classes live inside this class; it must only be
 * touched when {@code Loader.isModLoaded("jarutils")} is true, so its class
 * loading never happens without JarUtils on the classpath.
 * <p>
 * 对 JarUtils 类的全部引用都集中在本类内；仅当
 * {@code Loader.isModLoaded("jarutils")} 为真时才允许触碰本类，
 * 从而保证 JarUtils 不在 classpath 上时本类绝不会被加载。
 */
public final class JarUtilsLangScanner {

    /**
     * The JarUtils mod identifier. / JarUtils 的模组标识符。
     */
    public static final String JAR_UTILS_MODID = "jarutils";

    /**
     * Lang directory under each resource domain. / 各资源域下的语言目录。
     */
    private static final String LANG_DIR = "lang";

    private JarUtilsLangScanner() {
    }

    /**
     * Walks JarUtils's index and injects every foreign JSON lang file found.
     * CatFrame's own files are skipped — they are already loaded by
     * {@link LanguageRegister#load()} at pre-init.
     * <p>
     * 遍历 JarUtils 索引，注入找到的每一份外部 JSON 语言文件。
     * CatFrame 自身的文件被跳过——它们已在 preInit 由
     * {@link LanguageRegister#load()} 加载。
     */
    public static void loadExternalLangs() {
        Set<UrlBuffered> index = JarUtil.getSet();
        if (index.isEmpty()) {
            CatFrame.logger.warn("LanguageRegister: JarUtils index is empty, skipping external lang scan");
            return;
        }

        // Deduplicate by domain/file so duplicate index entries inject once
        // 按 domain/file 去重，保证重复索引条目只注入一次
        Set<String> seen = new HashSet<>();
        int injected = 0;

        for (UrlBuffered url : index) {
            // Only jar entries carry clean '/'-separated entry names
            // 仅 jar 条目具有干净的 '/' 分隔条目名
            if (!url.isJar()) continue;

            // Expected shape: assets/<domain>/lang/<code>.json
            // 期望形态：assets/<domain>/lang/<code>.json
            String[] seg = url.getFileUrl().split("/");
            if (seg.length != 4 || !"assets".equals(seg[0]) || !LANG_DIR.equals(seg[2])) continue;

            String domain = seg[1];
            String fileName = seg[3];
            if (!fileName.endsWith(".json")) continue;

            // Only accept all-lowercase language codes (e.g. "en_us", "zh_cn")
            // 只接受全小写语言码（如 "en_us"、"zh_cn"）
            String langCode = fileName.substring(0, fileName.length() - ".json".length());
            if (!langCode.equals(langCode.toLowerCase(Locale.ENGLISH))) continue;

            // Our own files were already loaded at pre-init
            // 自身文件已在 preInit 阶段加载完毕
            if (Tags.MODID.equals(domain)) continue;
            if (!seen.add(domain + '/' + fileName)) continue;

            try (InputStream in = JarUtil.getInputStreamFromUrl(url)) {
                if (in == null) continue;
                LanguageRegister.injectExternal(domain, LANG_DIR, fileName, in);
                injected++;
            } catch (Exception e) {
                CatFrame.logger.error("LanguageRegister: failed to read indexed lang file '{}'", url, e);
            }
        }

        CatFrame.logger.info("LanguageRegister: JarUtils external lang scan finished, {} file(s) injected", injected);
    }
}
