package decok.dfcdvadstf.catframe;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
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

    // ==================== 分类名 ====================
    public static final String CATEGORY_SLEEP = "sleep_effects";

    // ==================== 睡眠增益效果 ====================

    /** 是否在醒来时给予增益效果 */
    public static boolean enableSleepEffects = true;

    /** 再生效果时长（tick，20 = 1 秒） */
    public static int regenDuration = 200;

    /** 再生效果等级（0 = I, 1 = II, ...） */
    public static int regenAmplifier = 1;

    /** 速度效果时长（tick） */
    public static int speedDuration = 300;

    /** 速度效果等级 */
    public static int speedAmplifier = 1;

    /** 抗性提升时长（tick） */
    public static int resistanceDuration = 400;

    /** 抗性提升等级 */
    public static int resistanceAmplifier = 0;

    /** 力量效果时长（tick，0=禁用） */
    public static int strengthDuration = 0;

    /** 力量效果等级 */
    public static int strengthAmplifier = 0;

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

            // ———— sleep_effects ————
            enableSleepEffects = loadBool(CATEGORY_SLEEP, "enableSleepEffects", true,
                    "Set to false to disable all post-sleep status effects.");

            regenDuration = loadInt(CATEGORY_SLEEP, "regenDuration", 200,
                    "Regeneration duration in ticks (20 ticks = 1 second). 0 = disabled.",
                    0, 72000);

            regenAmplifier = loadInt(CATEGORY_SLEEP, "regenAmplifier", 1,
                    "Regeneration amplifier: 0 = Regeneration I, 1 = Regeneration II, etc.",
                    0, 255);

            speedDuration = loadInt(CATEGORY_SLEEP, "speedDuration", 300,
                    "Speed effect duration in ticks. 0 = disabled.",
                    0, 72000);

            speedAmplifier = loadInt(CATEGORY_SLEEP, "speedAmplifier", 1,
                    "Speed amplifier: 0 = Speed I, 1 = Speed II, etc.",
                    0, 255);

            resistanceDuration = loadInt(CATEGORY_SLEEP, "resistanceDuration", 400,
                    "Resistance effect duration in ticks. 0 = disabled.",
                    0, 72000);

            resistanceAmplifier = loadInt(CATEGORY_SLEEP, "resistanceAmplifier", 0,
                    "Resistance amplifier: 0 = Resistance I.",
                    0, 255);

            strengthDuration = loadInt(CATEGORY_SLEEP, "strengthDuration", 0,
                    "Strength effect duration in ticks. 0 = disabled (default).",
                    0, 72000);

            strengthAmplifier = loadInt(CATEGORY_SLEEP, "strengthAmplifier", 0,
                    "Strength amplifier: 0 = Strength I.",
                    0, 255);

        } catch (Exception e) {
            CatFrame.logger.log(Level.ERROR, "CatFrameConfig: failed to load config", e);
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
