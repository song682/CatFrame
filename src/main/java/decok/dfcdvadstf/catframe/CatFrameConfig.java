package decok.dfcdvadstf.catframe;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.apache.logging.log4j.Level;

import java.io.File;

/**
 * CatFrame 模组配置。使用 Forge 1.7.10 的 {@link Configuration} 体系。
 * <p>
 * 所有配置项均为 static，在 {@link #init(FMLPreInitializationEvent)} 中加载。
 */
public class CatFrameConfig {

    private static Configuration config;

    // ==================== 功能开关 ====================

    /** 是否启用 Bluey 毛绒玩偶物品，默认 false */
    public static boolean enableBlueyPlushy = false;

    // ==================== 调试日志 ====================

    /** 是否开启调试日志（Mixin 渲染日志等），默认 false */
    public static boolean debugLogThingsEnabled = false;

    /**
     * @return 是否应该输出调试日志：开发环境 或 {@link #debugLogThingsEnabled} 为 true
     */
    public static boolean shouldLogDebug() {
        return debugLogThingsEnabled || isDevEnvironment();
    }

    /**
     * 检查是否运行在开发环境（反编译环境）。
     */
    private static boolean isDevEnvironment() {
        try {
            Object val = Launch.blackboard.get("fml.deobfuscatedEnvironment");
            return val instanceof Boolean && (Boolean) val;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 初始化 ====================

    /**
     * 从 {@link FMLPreInitializationEvent#getSuggestedConfigurationFile()} 加载配置。
     * 在 CatFrame.preInit() 中调用。
     */
    public static void init(FMLPreInitializationEvent event) {
        File configFile = event.getSuggestedConfigurationFile();
        config = new Configuration(configFile);

        try {
            config.load();

            // ———— features ————
            enableBlueyPlushy = loadBool("features", "enableBlueyPlushy", false,
                    "Set to true to enable the Bluey plushy item.");

            // ———— debug ————
            debugLogThingsEnabled = loadBool("debug", "debugLogThingsEnabled", false,
                    "Set to true to enable debug logging for the render system (Mixin logs, etc.).");

        } catch (Exception e) {
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    /**
     * 强制保存当前值到配置文件。
     */
    public static void save() {
        if (config != null && config.hasChanged()) {
            config.save();
        }
    }

    // ==================== 辅助 ====================

    private static boolean loadBool(String category, String key, boolean defaultValue, String comment) {
        Property prop = config.get(category, key, defaultValue);
        prop.comment = comment;
        return prop.getBoolean(defaultValue);
    }

    private static int loadInt(String category, String key, int defaultValue, String comment,
                                int min, int max) {
        Property prop = config.get(category, key, defaultValue);
        prop.comment = comment;
        prop.setMinValue(min);
        prop.setMaxValue(max);
        return prop.getInt(defaultValue);
    }
}
