package decok.dfcdvadstf.catframe;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * CatFrame Mod Configuration
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
     * @return Whether debug logging should be output: development environment or {@link #debugLogThingsEnabled} is true
     */
    public static boolean shouldLogDebug() {
        return debugLogThingsEnabled || isDevEnvironment();
    }

    /**
     * Check if running in a development environment (deobfuscated environment). 
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
