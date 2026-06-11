package decok.dfcdvadstf.catframe.langguage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Language domain registry — mods register their language file directories here
 * during preInit, so that {@link LocalizationManager} knows where to load JSON
 * translations from.
 * <p>
 * 语言域注册表 —— 各 mod 在 preInit 中注册自己的语言文件目录，
 * {@link LocalizationManager} 据此知道从哪里加载 JSON 翻译。
 * <p>
 * Usage / 用法:
 * <pre>{@code
 *   // In your mod's preInit:
 *   LanguageRegister.domain("mymod", "assets/mymod/lang");
 *   LanguageRegister.domain("catframe", "assets/catframe/lang");
 *
 *   // Then call LocalizationManager.Loader.load() once all domains are registered.
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
     * Registers a language domain for a mod.
     * <p>
     * 为 mod 注册一个语言域。
     *
     * @param modid    mod identifier, used in {@code modid:key} format / mod 标识符
     * @param basePath classpath-relative directory containing lang JSON files,
     *                 e.g. {@code "assets/mymod/lang"} / classpath 相对目录
     */
    public static void domain(String modid, String basePath) {
        domains.put(modid, basePath);
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
