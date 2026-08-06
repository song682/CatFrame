package decok.dfcdvadstf.catframe.compact;

import cpw.mods.fml.common.Loader;

public class CompactBase {

    // IME Support
    public static boolean isIGIMEInstalled() {
        return Loader.isModLoaded("ingameime");
    }

    public static boolean isIMEBackportInstalled() {
        return Loader.isModLoaded("ime_input_backport");
    }

    // MCPatcher Format + OptiFine-like mod compact
    public static boolean isAngelicaInstalled() {
        return Loader.isModLoaded("angelica");
    }

    public static boolean isNotFineInstalled() {
        return Loader.isModLoaded("notfine");
    }

    public static boolean isOptiFutureInstalled() {
        return Loader.isModLoaded("optifuture");
    }

    // Tags support
    public static boolean isHogTagInstalled() {
        return Loader.isModLoaded("hogutils");
    }

    public static boolean isWolfTagInstalled() {
        return Loader.isModLoaded("pineapple_tag");
    }
}