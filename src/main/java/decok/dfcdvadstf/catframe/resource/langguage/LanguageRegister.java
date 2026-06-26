package decok.dfcdvadstf.catframe.resource.langguage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.LanguageRegistry;
import decok.dfcdvadstf.catframe.CatFrame;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Language file registry — mods register their language file directories here
 * during preInit. JSON lang files ({@code xx_xx.json}, all-lowercase) are scanned
 * from the mod's jar/directory and injected into Forge's {@link LanguageRegistry},
 * so vanilla {@code I18n} / {@code StatCollector} handle all translation lookups.
 * <p>
 * 语言文件注册表 —— 各 mod 在 preInit 中注册自己的语言文件目录。
 * 扫描 mod jar/目录中的 JSON 语言文件（{@code xx_xx.json}，全小写），
 * 注入 Forge {@link LanguageRegistry}，由原版 {@code I18n} / {@code StatCollector} 接管翻译。
 * <p>
 * Usage / 用法:
 * <pre>{@code
 *   // In your mod's preInit:
 *   LanguageRegister.domain("mymod", "assets/mymod/lang");
 *   // That's it — translations are injected into LanguageRegistry automatically.
 * }</pre>
 */
public final class LanguageRegister {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    /**
     * modid → basePath (e.g. "catframe" → "assets/catframe/lang")
     */
    private static final Map<String, String> domains = new LinkedHashMap<>();

    private LanguageRegister() {
    }

    /**
     * Registers a mod's language file directory. Scans the mod's jar/directory
     * for {@code xx_xx.json} files (all-lowercase language codes) and injects
     * them into Forge's {@link LanguageRegistry}.
     * <p>
     * 注册 mod 的语言文件目录。扫描 mod 的 jar/目录中 {@code xx_xx.json} 文件
     * （全小写语言码），注入 Forge {@link LanguageRegistry}。
     *
     * @param modid    Forge mod identifier (must match {@code @Mod(modid=...)}) / Forge mod 标识符
     * @param basePath classpath-relative directory containing lang JSON files,
     *                 e.g. {@code "assets/mymod/lang"} / classpath 相对目录
     */
    public static void domain(String modid, String basePath) {
        domains.put(modid, basePath);
        scanAndInject(modid, basePath);
    }

    /**
     * Returns an unmodifiable view of all registered domains.
     * <p>
     * 返回所有已注册域的不可修改视图。
     */
    public static Map<String, String> getDomains() {
        return Collections.unmodifiableMap(domains);
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

        if (source.isFile()) {
            scanJar(source, basePath);
        } else if (source.isDirectory()) {
            scanDirectory(source, basePath);
        }
    }

    /**
     * Scans a jar file for lang JSON files under the given base path.
     */
    private static void scanJar(File jarFile, String basePath) {
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

                try (InputStream in = jar.getInputStream(entry)) {
                    Map<String, String> data = parseJsonLang(in);
                    if (!data.isEmpty()) {
                        String vanillaCode = toVanillaCode(langCode);
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
    private static void scanDirectory(File modDir, String basePath) {
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

            try (InputStream in = new FileInputStream(f)) {
                Map<String, String> data = parseJsonLang(in);
                if (!data.isEmpty()) {
                    String vanillaCode = toVanillaCode(langCode);
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
