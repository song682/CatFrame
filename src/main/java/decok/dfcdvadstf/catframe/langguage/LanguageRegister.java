package decok.dfcdvadstf.catframe.langguage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Language domain registry — mods register their language file directories here
 * during preInit. Translations are loaded automatically after the first registration.
 * <p>
 * 语言域注册表 —— 各 mod 在 preInit 中注册自己的语言文件目录，
 * 首次注册后自动加载翻译，无需手动调用 load()。
 * <p>
 * Usage / 用法:
 * <pre>{@code
 *   // In your mod's preInit or init:
 *   LanguageRegister.domain("mymod", "assets/mymod/lang");
 *   LanguageRegister.domain("catframe", "assets/catframe/lang");
 *   // No need to call load() - it's triggered automatically!
 * }</pre>
 */
public final class LanguageRegister {

    /**
     * domain → basePath (e.g. "catframe" → "assets/catframe/lang")
     */
    static final Map<String, String> domains = new LinkedHashMap<>();

    private LanguageRegister() {
    }

    /**
     * Registers a language domain for a mod. Triggers automatic loading on first call.
     * <p>
     * 为 mod 注册一个语言域。首次调用时自动触发加载。
     *
     * @param modid    mod identifier, used in {@code modid:key} format / mod 标识符
     * @param basePath classpath-relative directory containing lang JSON files,
     *                 e.g. {@code "assets/mymod/lang"} / classpath 相对目录
     */
    public static void domain(String modid, String basePath) {
        domains.put(modid, basePath);
        // Auto-trigger loading on first registration, or incrementally load new domain
        // 首次注册时自动触发全量加载，之后增量加载新域
        if (!LocalizationManager.isLoaded()) {
            LocalizationManager.loadAll();
        } else {
            // Already loaded — incrementally load only the new domain
            // 已加载 — 仅增量加载新注册的域
            LocalizationManager.loadDomain(modid, basePath);
        }
    }

    /**
     * Returns an unmodifiable view of all registered domains.
     * <p>
     * 返回所有已注册域的不可修改视图。
     */
    public static Map<String, String> getDomains() {
        return Collections.unmodifiableMap(domains);
    }
}
