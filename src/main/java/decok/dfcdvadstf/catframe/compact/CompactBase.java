package decok.dfcdvadstf.catframe.compact;

import cpw.mods.fml.common.Loader;

public class CompactBase {
    public static boolean isIGIMEInstalled() {
        return Loader.isModLoaded("ingameime");
    }

    public static boolean isIMEBackportInstalled() {
        return Loader.isModLoaded("ime_input_backport");
    }

    public static boolean isAngelicaInstalled() {
        return Loader.isModLoaded("angelica");
    }

    public static boolean isNotFineInstalled() {
        return Loader.isModLoaded("notfine");
    }

    public static boolean isOptiFutureOptimizedInstalled() {
        return Loader.isModLoaded("optifuture");
    }
}