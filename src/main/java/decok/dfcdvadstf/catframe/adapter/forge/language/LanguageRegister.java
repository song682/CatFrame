package decok.dfcdvadstf.catframe.adapter.forge.language;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.LanguageRegistry;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.Tags;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Language file loader — scans CatFrame's own jar/directory during preInit
 * for JSON lang files ({@code xx_xx.json}, all-lowercase) and injects them
 * into Forge's {@link LanguageRegistry}, so vanilla {@code I18n} /
 * {@code StatCollector} handle all translation lookups.
 * <p>
 * 语言文件加载器 —— 在 preInit 阶段扫描 CatFrame 自身 jar/目录中的
 * JSON 语言文件（{@code xx_xx.json}，全小写），注入 Forge {@link LanguageRegistry}，
 * 由原版 {@code I18n} / {@code StatCollector} 接管翻译。
 * <p>
 * Currently single-path: only {@code assets/catframe/lang} is searched.
 * 当前为单一路径：仅搜索 {@code assets/catframe/lang}。
 * <p>
 * Compat-layer scans may additionally feed other mods' JSON lang files in
 * via {@link #injectExternal}.
 * 兼容层扫描可经 {@link #injectExternal} 补充注入其它模组的 JSON 语言文件。
 * <p>
 * Usage / 用法:
 * <pre>{@code
 *   // In preInit:
 *   LanguageRegister.load();
 * }</pre>
 */
public final class LanguageRegister {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    /**
     * The single lang base path searched: "assets/catframe/lang"
     * 唯一被搜索的语言目录：{@code assets/catframe/lang}
     */
    private static final String BASE_PATH = "assets/" + Tags.MODID + "/lang";

    /**
     * Discovered lang files: [(vanillaLangCode, fileName)]
     * e.g. [("en_US", "en_us.json"), ("zh_CN", "zh_cn.json")]
     */
    private static final List<LangFileEntry> langFiles = new ArrayList<>();

    private LanguageRegister() {
    }

    private static class LangFileEntry {
        final String vanillaCode;    // "en_US"
        final String fileName;       // "en_us.json"
        final String resourceDomain; // "catframe"
        final String resourceDir;    // "lang"

        LangFileEntry(String vanillaCode, String fileName, String resourceDomain, String resourceDir) {
            this.vanillaCode = vanillaCode;
            this.fileName = fileName;
            this.resourceDomain = resourceDomain;
            this.resourceDir = resourceDir;
        }
    }

    /**
     * Scans CatFrame's own jar/directory for {@code xx_xx.json} files
     * (all-lowercase language codes) under {@link #BASE_PATH} and injects
     * them into Forge's {@link LanguageRegistry}.
     * <p>
     * 扫描 CatFrame 自身 jar/目录中 {@link #BASE_PATH} 下的 {@code xx_xx.json}
     * 文件（全小写语言码），注入 Forge {@link LanguageRegistry}。
     */
    public static void load() {
        scanAndInject(Tags.MODID, BASE_PATH);
    }

    /**
     * Injects an externally discovered lang file — e.g. found in another
     * mod's jar by a compat-layer scan — and remembers it so later
     * resource-manager reloads re-inject it with resource pack overrides
     * on top. The stream is consumed but not closed here.
     * <p>
     * 注入外部发现的语言文件——例如兼容层扫描从其它模组 jar 中找到的
     * 文件——并记录之，使后续资源管理器重载能带着资源包覆盖重新注入。
     * 本方法消费但不关闭传入的流。
     *
     * @param resourceDomain resource domain of the file / 文件所属资源域
     * @param resourceDir    directory under the domain, e.g. "lang" / 域内目录
     * @param fileName       all-lowercase json name, e.g. "en_us.json" / 全小写文件名
     * @param in             the file's content stream / 文件内容流
     */
    static void injectExternal(String resourceDomain, String resourceDir, String fileName, InputStream in) {
        String langCode = fileName.substring(0, fileName.length() - ".json".length());
        String vanillaCode = toVanillaCode(langCode);
        Map<String, String> data = parseJsonLang(in);
        if (data.isEmpty()) return;

        langFiles.add(new LangFileEntry(vanillaCode, fileName, resourceDomain, resourceDir));
        LanguageRegistry.instance().injectLanguage(vanillaCode, new HashMap<>(data));
        CatFrame.logger.info("LanguageRegister: injected {} external keys from '{}:{}/{}' as '{}'",
                data.size(), resourceDomain, resourceDir, fileName, vanillaCode);
    }

    /**
     * Reloads all known JSON lang files from {@link IResourceManager}.
     * This reads from all active resource packs, so resource pack overrides
     * are picked up automatically. Keys are injected into
     * {@link LanguageRegistry} in reverse priority order (mod jar first,
     * then resource pack overrides on top).
     * <p>
     * 从 {@link IResourceManager} 重新加载所有已知的 JSON 语言文件。<br>
     * 会读取所有活跃资源包中的文件（包括 override），按优先级从低到高注入。
     *
     * @param manager the resource manager (from the reload event) / 资源管理器
     */
    public static void reloadFromResourceManager(IResourceManager manager) {
        if (langFiles.isEmpty()) return;

        for (LangFileEntry entry : langFiles) {
            String resPath = entry.resourceDir + "/" + entry.fileName;
            ResourceLocation loc = new ResourceLocation(entry.resourceDomain, resPath);

            try {
                @SuppressWarnings("unchecked")
                List<IResource> resources = manager.getAllResources(loc);
                // getAllResources returns resources priority-descending
                // (highest priority = resource pack override first).
                // Iterate in reverse so mod jar data loads first,
                // then resource pack overrides on top.
                for (int i = resources.size() - 1; i >= 0; i--) {
                    try (InputStream in = resources.get(i).getInputStream()) {
                        Map<String, String> data = parseJsonLang(in);
                        if (!data.isEmpty()) {
                            LanguageRegistry.instance().injectLanguage(
                                    entry.vanillaCode, new HashMap<>(data));
                            if (i == 0) {
                                CatFrame.logger.debug(
                                        "LanguageRegister: reloaded {} keys from '{}' as '{}'",
                                        data.size(), resPath, entry.vanillaCode);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Resource not found in resource manager — skip
                CatFrame.logger.debug("LanguageRegister: no resource '{}' from resource manager", loc);
            }
        }
    }

    // ==================== Internal scanning ====================

    /**
     * Scans the mod's jar or directory for {@code xx_xx.json} files and injects
     * their contents into {@link LanguageRegistry}.
     */
    private static void scanAndInject(String modid, String basePath) {
        ModContainer container = Loader.instance().getIndexedModList().get(modid);
        if (container == null) {
            CatFrame.logger.warn("LanguageRegister: mod '{}' not found in Loader, skipping lang scan", modid);
            return;
        }

        File source = container.getSource();
        if (source == null) {
            CatFrame.logger.warn("LanguageRegister: mod '{}' has no source file, skipping lang scan", modid);
            return;
        }

        // basePath = "assets/catframe/lang"
        //   → resourceDomain = "catframe" (between "assets/" and next "/")
        //   → resourceDir    = "lang" (everything after the domain)
        // basePath = "assets/catframe/lang"
        //   → resourceDomain = "catframe"（"assets/" 与下一个 "/" 之间）
        //   → resourceDir    = "lang"（域之后的部分）
        int assetsEnd = basePath.indexOf('/') + 1;
        int domainEnd = basePath.indexOf('/', assetsEnd);
        if (domainEnd < 0) {
            CatFrame.logger.warn("LanguageRegister: malformed lang base path '{}', skipping", basePath);
            return;
        }
        String resourceDomain = basePath.substring(assetsEnd, domainEnd);
        String resourceDir = basePath.substring(domainEnd + 1);

        if (source.isFile()) {
            scanJar(source, basePath, resourceDomain, resourceDir);
        } else if (source.isDirectory()) {
            scanDirectory(source, basePath, resourceDomain, resourceDir);
        }
    }

    /**
     * Scans a jar file for lang JSON files under the given base path.
     */
    private static void scanJar(File jarFile, String basePath, String resourceDomain, String resourceDir) {
        String prefix = basePath + "/";
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix) || !name.endsWith(".json")) continue;
                if (entry.isDirectory()) continue;

                // Extract filename: "assets/catframe/lang/en_us.json" → "en_us.json"
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                String langCode = fileName.substring(0, fileName.length() - ".json".length());

                // Only accept all-lowercase language codes (e.g. "en_us", "zh_cn")
                if (!langCode.equals(langCode.toLowerCase(Locale.ENGLISH))) {
                    CatFrame.logger.debug("LanguageRegister: skipping non-lowercase json '{}'", name);
                    continue;
                }

                String vanillaCode = toVanillaCode(langCode);

                // Record the discovered file for later IRMRL reload
                langFiles.add(new LangFileEntry(vanillaCode, fileName, resourceDomain, resourceDir));

                try (InputStream in = jar.getInputStream(entry)) {
                    Map<String, String> data = parseJsonLang(in);
                    if (!data.isEmpty()) {
                        LanguageRegistry.instance().injectLanguage(vanillaCode, new HashMap<>(data));
                        CatFrame.logger.info("LanguageRegister: injected {} keys from jar '{}' as '{}'",
                                data.size(), name, vanillaCode);
                    }
                }
            }
        } catch (Exception e) {
            CatFrame.logger.error("LanguageRegister: failed to scan jar '{}'", jarFile, e);
        }
    }

    /**
     * Scans a directory (dev environment) for lang JSON files under the given base path.
     */
    private static void scanDirectory(File modDir, String basePath, String resourceDomain, String resourceDir) {
        File langDir = new File(modDir, basePath);
        if (!langDir.isDirectory()) {
            CatFrame.logger.warn("LanguageRegister: lang directory not found: {}", langDir);
            return;
        }

        File[] files = langDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File f : files) {
            String fileName = f.getName();
            String langCode = fileName.substring(0, fileName.length() - ".json".length());

            // Only accept all-lowercase language codes
            if (!langCode.equals(langCode.toLowerCase(Locale.ENGLISH))) {
                CatFrame.logger.debug("LanguageRegister: skipping non-lowercase json '{}'", fileName);
                continue;
            }

            String vanillaCode = toVanillaCode(langCode);

            // Record the discovered file for later IRMRL reload
            langFiles.add(new LangFileEntry(vanillaCode, fileName, resourceDomain, resourceDir));

            try (InputStream in = new FileInputStream(f)) {
                Map<String, String> data = parseJsonLang(in);
                if (!data.isEmpty()) {
                    LanguageRegistry.instance().injectLanguage(vanillaCode, new HashMap<>(data));
                    CatFrame.logger.info("LanguageRegister: injected {} keys from '{}' as '{}'",
                            data.size(), f.getPath(), vanillaCode);
                }
            } catch (Exception e) {
                CatFrame.logger.error("LanguageRegister: failed to parse '{}'", f, e);
            }
        }
    }

    /**
     * Converts a lowercase language code to vanilla format.
     * {@code "en_us"} → {@code "en_US"}, {@code "zh_cn"} → {@code "zh_CN"}.
     */
    static String toVanillaCode(String langCode) {
        int underscore = langCode.indexOf('_');
        if (underscore > 0 && underscore < langCode.length() - 1) {
            return langCode.substring(0, underscore)
                    + "_"
                    + langCode.substring(underscore + 1).toUpperCase(Locale.ENGLISH);
        }
        return langCode;
    }

    /**
     * Parses a JSON lang file (flat key-value) into a Map.
     */
    private static Map<String, String> parseJsonLang(InputStream in) {
        try {
            Map<String, String> data = GSON.fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), MAP_TYPE);
            return data != null ? data : Collections.emptyMap();
        } catch (Exception e) {
            CatFrame.logger.error("LanguageRegister: failed to parse JSON", e);
            return Collections.emptyMap();
        }
    }
}
