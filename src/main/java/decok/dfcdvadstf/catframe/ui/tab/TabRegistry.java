package decok.dfcdvadstf.catframe.ui.tab;

import decok.dfcdvadstf.catframe.ui.Text;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

/**
 * <p>
 * 外部模组Tab注册中心<br>
 * 提供静态API供外部模组注册自定义标签页，按 barId 分桶隔离，
 * 不同界面的 Tab 互不干扰。
 * </p>
 * <p>
 * External mod tab registration hub<br>
 * Provides static API for external mods to register custom tabs.
 * Entries are bucketed by {@code barId} so different screens stay isolated.
 * </p>
 */
public final class TabRegistry {

    /**
     * barId → entries registered under that bar. / barId → 该 bar 下注册的条目。
     */
    private static final Map<String, List<TabEntry>> buckets = new HashMap<>();

    /**
     * Per-bar freeze state.  Once a bar is frozen no more tabs can be registered to it.
     */
    private static final Set<String> frozenBars = new HashSet<>();

    private TabRegistry() {
    }

    /**
     * <p>
     * 注册一个自定义标签页到指定的 Bar（使用 Text 标题）<br>
     * 应在模组初始化阶段（如 FMLPreInitializationEvent）调用
     * </p>
     * <p>
     * Register a custom tab to a specific bar with a Text title<br>
     * Should be called during mod initialization (e.g. FMLPreInitializationEvent)
     * </p>
     *
     * @param barId    目标 Bar 的唯一标识符 / Target bar's unique identifier
     * @param factory  Tab实例工厂 / Tab instance factory
     * @param tabId    标签页唯一ID / Unique tab ID
     * @param nameText Text 格式的标题（可翻译/字面） / Text title (translatable or literal)
     * @param priority 排序优先级，数字越小越靠前 / Sort priority, lower number = earlier position
     */
    public static void registerTab(String barId, Supplier<Tab> factory, int tabId, Text nameText, int priority) {
        if (barId == null || barId.isEmpty()) {
            throw new IllegalArgumentException("barId must not be null or empty / barId 不能为 null 或空");
        }
        if (frozenBars.contains(barId)) {
            throw new IllegalStateException(
                    "TabRegistry bar '" + barId + "' is already frozen. Tabs must be registered before GUI initialization." +
                            " / TabRegistry bar '" + barId + "' 已冻结，必须在GUI初始化之前注册标签页。"
            );
        }

        List<TabEntry> bucket = buckets.computeIfAbsent(barId, k -> new ArrayList<>());

        // Check for duplicate ID within the same bar
        // 检查同一 bar 内 ID 是否重复
        for (TabEntry entry : bucket) {
            if (entry.tabId == tabId) {
                String display = entry.nameText != null ? entry.nameText.getString() : entry.nameKey;
                throw new IllegalArgumentException(
                        "Tab ID " + tabId + " is already registered in bar '" + barId + "' by " + display +
                                " / Tab ID " + tabId + " 已在 bar '" + barId + "' 中被 " + display + " 注册"
                );
            }
        }

        bucket.add(new TabEntry(barId, factory, tabId, nameText, priority));
    }

    /**
     * <p>
     * 注册一个自定义标签页到指定的 Bar（使用 Text 标题，默认优先级为 tabId）<br>
     * Register a custom tab with Text title and default priority (defaults to tabId)
     * </p>
     */
    public static void registerTab(String barId, Supplier<Tab> factory, int tabId, Text nameText) {
        registerTab(barId, factory, tabId, nameText, tabId);
    }

    /**
     * <p>
     * 注册一个自定义标签页到指定的 Bar<br>
     * 应在模组初始化阶段（如 FMLPreInitializationEvent）调用
     * </p>
     * <p>
     * Register a custom tab to a specific Bar<br>
     * Should be called during mod initialization (e.g. FMLPreInitializationEvent)
     * </p>
     *
     * @param barId    目标 Bar 的唯一标识符 / Target bar's unique identifier
     * @param factory  Tab实例工厂 / Tab instance factory
     * @param tabId    标签页唯一ID，建议从103开始避免与内置Tab冲突 / Unique tab ID, recommended to start from 103 to avoid conflicts
     * @param nameKey  本地化键名 / Localization key
     * @param priority 排序优先级，数字越小越靠前 / Sort priority, lower number = earlier position
     */
    public static void registerTab(String barId, Supplier<Tab> factory, int tabId, String nameKey, int priority) {
        if (barId == null || barId.isEmpty()) {
            throw new IllegalArgumentException("barId must not be null or empty / barId 不能为 null 或空");
        }
        if (frozenBars.contains(barId)) {
            throw new IllegalStateException(
                    "TabRegistry bar '" + barId + "' is already frozen. Tabs must be registered before GUI initialization." +
                            " / TabRegistry bar '" + barId + "' 已冻结，必须在GUI初始化之前注册标签页。"
            );
        }

        List<TabEntry> bucket = buckets.computeIfAbsent(barId, k -> new ArrayList<>());

        // Check for duplicate ID within the same bar
        // 检查同一 bar 内 ID 是否重复
        for (TabEntry entry : bucket) {
            if (entry.tabId == tabId) {
                String display = entry.nameText != null ? entry.nameText.getString() : entry.nameKey;
                throw new IllegalArgumentException(
                        "Tab ID " + tabId + " is already registered in bar '" + barId + "' by " + display +
                                " / Tab ID " + tabId + " 已在 bar '" + barId + "' 中被 " + display + " 注册"
                );
            }
        }

        bucket.add(new TabEntry(barId, factory, tabId, nameKey, priority));
    }

    /**
     * <p>
     * 注册一个自定义标签页（使用默认优先级，默认优先级为tabId）<br>
     * 当不指定优先级时，标签页会按照 tabId 从小到大排序
     * </p>
     * <p>
     * Register a custom tab with default priority (defaults to tabId)<br>
     * When priority is not specified, tabs are sorted by tabId in ascending order
     * </p>
     */
    public static void registerTab(String barId, Supplier<Tab> factory, int tabId, String nameKey) {
        registerTab(barId, factory, tabId, nameKey, tabId);
    }

    /**
     * <p>
     * 获取指定 Bar 下所有已注册的标签页条目（按优先级排序）<br>
     * 返回不可修改的副本
     * </p>
     * <p>
     * Get all registered tab entries for a specific bar (sorted by priority)<br>
     * Returns an unmodifiable copy
     * </p>
     *
     * @param barId 目标 Bar 的唯一标识符 / Target bar's unique identifier
     */
    public static List<TabEntry> getEntries(String barId) {
        List<TabEntry> bucket = buckets.get(barId);
        if (bucket == null || bucket.isEmpty()) {
            return Collections.emptyList();
        }
        List<TabEntry> sorted = new ArrayList<>(bucket);
        sorted.sort(Comparator.comparingInt((TabEntry e) -> e.priority).thenComparingInt(e -> e.tabId));
        return Collections.unmodifiableList(sorted);
    }

    /**
     * <p>
     * 冻结指定 Bar 的注册表，阻止后续向该 Bar 注册<br>
     * 由 TabManager 在初始化时调用
     * </p>
     * <p>
     * Freeze the registry for a specific bar to prevent further registration<br>
     * Called by TabManager on initialization
     * </p>
     */
    public static void freeze(String barId) {
        frozenBars.add(barId);
    }

    /**
     * <p>
     * 检查指定 Bar 的注册表是否已冻结<br>
     * Check if a specific bar's registry is frozen
     * </p>
     */
    public static boolean isFrozen(String barId) {
        return frozenBars.contains(barId);
    }

    /**
     * <p>
     * 清空指定 Bar 的注册表（主要用于测试或内部重置）<br>
     * Clear a specific bar's registry (mainly for testing or internal reset)
     * </p>
     */
    public static void clear(String barId) {
        buckets.remove(barId);
        frozenBars.remove(barId);
    }

    /**
     * <p>
     * 清空所有注册表（主要用于测试或内部重置）<br>
     * Clear the entire registry (mainly for testing or internal reset)
     * </p>
     */
    public static void clearAll() {
        buckets.clear();
        frozenBars.clear();
    }

    /**
     * <p>
     * 注册条目数据类<br>
     * Registration entry data class
     * </p>
     */
    public static final class TabEntry {
        public final String barId;
        public final Supplier<Tab> factory;
        public final int tabId;
        /**
         * Raw name key (string form, always present). / 原始名称键（字符串形式，始终存在）。
         */
        public final String nameKey;
        /**
         * Text title (may be null if registered with plain String). / Text 标题（若用 String 注册则为 null）。
         */
        @Nullable
        public final Text nameText;
        public final int priority;

        TabEntry(String barId, Supplier<Tab> factory, int tabId, String nameKey, int priority) {
            this.barId = barId;
            this.factory = factory;
            this.tabId = tabId;
            this.nameKey = nameKey;
            this.nameText = null;
            this.priority = priority;
        }

        TabEntry(String barId, Supplier<Tab> factory, int tabId, Text nameText, int priority) {
            this.barId = barId;
            this.factory = factory;
            this.tabId = tabId;
            this.nameKey = nameText != null ? nameText.getRaw() : "";
            this.nameText = nameText;
            this.priority = priority;
        }
    }
}
