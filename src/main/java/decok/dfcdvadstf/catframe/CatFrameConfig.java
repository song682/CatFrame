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
    /** Whether to show the welcome Toast when joining a world / 进入世界时是否显示欢迎 Toast */
    public boolean welcomeToast;
    public static boolean debugLogThingsEnabled = false;
    /**
     * 实验性设置：自研 CatAtlas 缝合后端开关（Hot Update 撤回方案，见
     * 《世界渲染CatAtlas化-方案与SWOT.md》第 7 节）。
     * <ul>
     *   <li>{@code false}（默认，原版后端）：数据驱动收集结果经 registerIcon 喂入原版
     *       TextureMap，世界方块内联写入 chunk 批次 —— OptiFine / mcpatcher 连接纹理等
     *       第三方渲染生态回到兼容基线；</li>
     *   <li>{@code true}（实验性 CatAtlas 后端）：自研图集缝合 + Post 换绑接管世界渲染。</li>
     * </ul>
     * Experimental setting: toggles the CatAtlas self-stitching backend. Default
     * false routes the data-driven texture collection into the vanilla TextureMap
     * (compatibility baseline for OptiFine / mcpatcher CTM); true re-enables the
     * experimental CatAtlas stitch + world-render takeover.
     */
    public static boolean catAtlasBackend = false;

    public CatFrameConfig(File file){
        config = new Configuration(file);
        config.addCustomCategoryComment("features", "Some examples and useful small things.");
        config.addCustomCategoryComment("dev", "Developer things");
        config.addCustomCategoryComment("experimental",
                "Experimental settings. May break compatibility with third-party render mods.");
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
        welcomeToast = config.getBoolean("welcomeToast", "features", true, "Set to true to show the CatFrame welcome toast when joining a world.");
        debugLogThingsEnabled = config.getBoolean("debugLogThingsEnabled", "dev", false, "Set to true to enable debug logging for the render system (Mixin logs, etc.).");
        catAtlasBackend = config.getBoolean("catAtlasBackend", "experimental", false,
                "[EXPERIMENTAL] Set to true to enable the CatAtlas self-stitching backend (custom atlas + world-render takeover). "
                        + "Default false feeds the data-driven texture collection into the vanilla TextureMap "
                        + "(compatibility baseline for OptiFine / mcpatcher connected textures).");
    }
}
