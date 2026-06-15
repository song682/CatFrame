package decok.dfcdvadstf.catframe;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * CatFrame 模组配置。
 */
public class CatFrameConfig {

    private static Configuration config;

    public boolean enableBlueyPlushy;
    public static boolean debugLogThingsEnabled = false;

    public CatFrameConfig(File file){
        config = new Configuration(file);
        config.addCustomCategoryComment("features", "Some examples and useful small things.");
        config.addCustomCategoryComment("dev", "Developer things");
        Options();
        config.save();
    }

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

    public void Options(){
        enableBlueyPlushy = config.getBoolean("enableBlueyPlushy", "features", false, "Set to true to enable the Bluey plushy item.");
        debugLogThingsEnabled = config.getBoolean("dev", "debugLogThingsEnabled", false, "Set to true to enable debug logging for the render system (Mixin logs, etc.).");
    }
}
