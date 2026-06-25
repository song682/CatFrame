package decok.dfcdvadstf.catframe.langguage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * JSON-based localization manager with namespace support, aligned with Minecraft 26.1.2 design.
 * <p>
 * Keys are stored internally as {@code domain:key}. Supports two API forms:
 * <pre>{@code
 *   LocalizationManager.Translation.translate("catframe:menu.paused");
 *   LocalizationManager.Translation.translate("catframe", "menu.paused", args...);
 * }</pre>
 * <p>
 * Translations are loaded automatically when mods register their domains via
 * {@link LanguageRegister#domain(String, String)}. No manual load call needed.
 * <p>
 * Uses context classloader to support cross-mod resource loading (e.g. CreateWorldUI).
 */
public final class LocalizationManager {

    /** Separator between domain and key */
    public static final char DOMAIN_SEPARATOR = ':';

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    /** Current-language translations: "domain:key" → text */
    static final Map<String, String> translations = new HashMap<>();

    /** Fallback (en_us) translations: "domain:key" → text */
    static final Map<String, String> fallback = new HashMap<>();

    /** Keys that have been marked as enabled/translatable: "domain:key" */
    static final Set<String> enabledKeys = new HashSet<>();

    static boolean loaded = false;

    private LocalizationManager() {}

    // ==================== Loading ====================

    /**
     * Returns whether translations have been loaded.
     * <p>返回翻译是否已加载。</p>
     */
    static boolean isLoaded() {
        return loaded;
    }

    /**
     * Loads translations from all registered domains. Called automatically by
     * {@link LanguageRegister#domain(String, String)} after the first registration.
     * <p>
     * 从所有已注册域加载翻译。由 {@link LanguageRegister#domain(String, String)} 在首次注册后自动调用。</p>
     */
    @SideOnly(Side.CLIENT)
    static void loadAll() {
        if (loaded) return;

        String currentLang = getCurrentLanguage();

        for (Map.Entry<String, String> entry : LanguageRegister.domains.entrySet()) {
            String domain = entry.getKey();
            String basePath = entry.getValue();

            // Always load en_us as fallback
            loadDomainFile(domain, basePath, "en_us", fallback);

            // Load current language (skip if already en_us)
            if (!"en_us".equals(currentLang)) {
                loadDomainFile(domain, basePath, currentLang, translations);
            }
        }

        // Fill missing entries from fallback
        for (Map.Entry<String, String> entry : fallback.entrySet()) {
            if (!translations.containsKey(entry.getKey())) {
                translations.put(entry.getKey(), entry.getValue());
            }
        }

        loaded = true;
        CatFrame.logger.info("LocalizationManager loaded: lang={}, domains={}, keys={}",
                currentLang, LanguageRegister.domains.size(), translations.size());
    }

    /**
     * Incrementally loads a single domain's translations into the existing maps.
     * Called when a new domain is registered after initial loading.
     * <p>增量加载单个域的翻译到现有映射中。在初始加载后注册新域时调用。</p>
     *
     * @param domain   the domain identifier (mod id)
     * @param basePath classpath-relative directory containing lang JSON files
     */
    @SideOnly(Side.CLIENT)
    static void loadDomain(String domain, String basePath) {
        String currentLang = getCurrentLanguage();

        // Load en_us as fallback
        loadDomainFile(domain, basePath, "en_us", fallback);

        // Load current language
        if (!"en_us".equals(currentLang)) {
            loadDomainFile(domain, basePath, currentLang, translations);
        }

        // Fill missing entries from fallback for this domain
        for (Map.Entry<String, String> entry : fallback.entrySet()) {
            if (entry.getKey().startsWith(domain + DOMAIN_SEPARATOR)
                    && !translations.containsKey(entry.getKey())) {
                translations.put(entry.getKey(), entry.getValue());
            }
        }

        CatFrame.logger.info("LocalizationManager incrementally loaded domain '{}': lang={}, total keys={}",
                domain, currentLang, translations.size());
    }

    /**
     * Reloads all translations (e.g. after language switch). Public for Mixin access.
     * <p>重新加载所有翻译（如语言切换后）。对 Mixin 公开。</p>
     */
    @SideOnly(Side.CLIENT)
    public static void reload() {
        translations.clear();
        fallback.clear();
        loaded = false;
        loadAll();
    }

    @SideOnly(Side.CLIENT)
    private static String getCurrentLanguage() {
        try {
            return Minecraft.getMinecraft().getLanguageManager()
                    .getCurrentLanguage().getLanguageCode()
                    .toLowerCase(Locale.ENGLISH);
        } catch (Exception e) {
            CatFrame.logger.warn("Failed to detect language, falling back to en_us", e);
            return "en_us";
        }
    }

    @SideOnly(Side.CLIENT)
    private static void loadDomainFile(String domain, String basePath,
                                       String langCode, Map<String, String> target) {
        String path = basePath + "/" + langCode + ".json";
        // Use context classloader to access resources from all mods, not just CatFrame
        // 使用 context classloader 访问所有模组的资源，而不仅是 CatFrame
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = LocalizationManager.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                CatFrame.logger.warn("Language file not found: {}", path);
                return;
            }
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            Map<String, String> data = GSON.fromJson(reader, MAP_TYPE);
            if (data != null) {
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    target.put(domain + DOMAIN_SEPARATOR + entry.getKey(), entry.getValue());
                }
                CatFrame.logger.info("Loaded {} keys from {}:{} ({})",
                        data.size(), domain, langCode, path);
            }
        } catch (Exception e) {
            CatFrame.logger.error("Failed to load language file: {}", path, e);
        }
    }

    // ==================== Translation ====================

    /**
     * Core translation lookups — domain:key resolution, StatCollector-style
     * {@code translateToLocal}, and key existence checks.
     * <p>
     * 核心翻译查询 —— domain:key 解析、StatCollector 风格的 {@code translateToLocal}、
     * 以及键存在性检查。
     */
    public static class Translation {

        // ──── translate ────

        /**
         * Translates using a {@code domain:key} string.
         * <pre>{@code
         *   Translation.translate("catframe:menu.paused");
         *   Translation.translate("catframe:item.count", 5);
         * }</pre>
         */
        public static String translate(String resourceKey, Object... args) {
            int sep = resourceKey.indexOf(DOMAIN_SEPARATOR);
            if (sep <= 0) {
                CatFrame.logger.warn("Translation key missing domain separator ':': {}", resourceKey);
                return resourceKey;
            }
            String domain = resourceKey.substring(0, sep);
            String key = resourceKey.substring(sep + 1);
            return translate(domain, key, args);
        }

        /**
         * Translates with explicit domain and key, similar to
         * {@code new ResourceLocation(domain, path)}.
         * <pre>{@code
         *   Translation.translate("catframe", "menu.paused");
         *   Translation.translate("catframe", "item.count", 5);
         * }</pre>
         */
        public static String translate(String domain, String key, Object... args) {
            String fullKey = domain + DOMAIN_SEPARATOR + key;
            String text = translations.get(fullKey);
            if (text == null) {
                text = fallback.get(fullKey);
            }
            if (text == null) {
                CatFrame.logger.warn("Missing translation for: {}", fullKey);
                return args.length > 0 ? fullKey + " " + Arrays.toString(args) : fullKey;
            }
            if (args.length > 0) {
                try {
                    return String.format(text, args);
                } catch (Exception e) {
                    CatFrame.logger.warn("Format error for '{}': {}", fullKey, e.getMessage());
                }
            }
            return text;
        }

        // ──── translateToLocal (StatCollector-style) ────

        /**
         * Translates a key to the current locale — equivalent to vanilla 1.7.10's
         * {@code StatCollector.translateToLocal}. Accepts both {@code domain:key}
         * and description IDs (e.g. {@code "block.minecraft.stone"}).
         * <pre>{@code
         *   Translation.translateToLocal("catframe:menu.paused");
         *   Translation.translateToLocal("block.minecraft.stone");
         *   Translation.translateToLocal("item.catframe.meat_raw");
         * }</pre>
         */
        public static String translateToLocal(String key, Object... args) {
            int sep = key.indexOf(DOMAIN_SEPARATOR);
            if (sep > 0) {
                return translate(key.substring(0, sep), key.substring(sep + 1), args);
            }
            // description ID format: type.domain.name → domain boundary at second dot
            int firstDot = key.indexOf('.');
            if (firstDot > 0) {
                int secondDot = key.indexOf('.', firstDot + 1);
                if (secondDot > 0) {
                    String domain = key.substring(firstDot + 1, secondDot);
                    String localKey = key.substring(secondDot + 1);
                    return translate(domain, localKey, args);
                }
            }
            return translate(key, args);
        }

        /**
         * Translates with explicit domain and key — StatCollector-style.
         * <pre>{@code
         *   Translation.translateToLocal("catframe", "menu.paused");
         *   Translation.translateToLocal("catframe", "item.count", 5);
         * }</pre>
         */
        public static String translateToLocal(String domain, String key, Object... args) {
            return translate(domain, key, args);
        }

        // ──── Key lookup ────

        /** Checks whether a {@code domain:key} exists. */
        public static boolean hasKey(String resourceKey) {
            return translations.containsKey(resourceKey) || fallback.containsKey(resourceKey);
        }

        /** Checks whether a domain+key pair exists. */
        public static boolean hasKey(String domain, String key) {
            return hasKey(domain + DOMAIN_SEPARATOR + key);
        }
    }

    // ==================== DescriptionIds ====================

    /**
     * Generates description IDs matching high-version Minecraft's
     * {@code Util.makeDescriptionId} — used for block/item translation keys.
     * <p>
     * 生成匹配高版本 Minecraft {@code Util.makeDescriptionId} 的描述 ID ——
     * 用于方块/物品的翻译键。
     */
    public static class DescriptionIds {

        /**
         * Generates a description ID from type + Identifier.
         * <pre>{@code
         *   DescriptionIds.make("block", "minecraft", "stone")   → "block.minecraft.stone"
         *   DescriptionIds.make("item", "mymod", "diamond")      → "item.mymod.diamond"
         *   DescriptionIds.make("item", "catframe", "meat_raw")  → "item.catframe.meat_raw"
         * }</pre>
         *
         * @param type   prefix type ("block" or "item")
         * @param domain mod namespace
         * @param name   block/item registry name (slashes replaced with dots)
         */
        public static String make(String type, String domain, String name) {
            return type + "." + domain + "." + name.replace('/', '.');
        }
    }

    // ==================== KeyTracking ====================

    /**
     * Tracks which translation keys have been marked as "enabled" —
     * called automatically by {@link decok.dfcdvadstf.catframe.ui.Text#setTranslatable}.
     * Useful for coverage verification and finding dead entries in lang JSONs.
     * <p>
     * 追踪哪些翻译键已被标记为"启用" ——
     * 由 {@link decok.dfcdvadstf.catframe.ui.Text#setTranslatable} 自动调用。
     * 用于覆盖率验证和发现 lang JSON 中的死条目。
     */
    public static class KeyTracking {

        /**
         * Marks a key as "enabled" — called automatically by {@code Text.setTranslatable}.
         */
        public static void mark(String domain, String key) {
            enabledKeys.add(domain + DOMAIN_SEPARATOR + key);
        }

        /** Returns whether the given domain+key has been marked as enabled. */
        public static boolean isEnabled(String domain, String key) {
            return enabledKeys.contains(domain + DOMAIN_SEPARATOR + key);
        }

        /** Returns an unmodifiable view of all enabled resource keys. */
        public static Set<String> all() {
            return Collections.unmodifiableSet(enabledKeys);
        }
    }
}
