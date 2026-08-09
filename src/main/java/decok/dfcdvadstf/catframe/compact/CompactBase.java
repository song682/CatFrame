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

    /**
     * 不兼容模组拒绝加载：ItemPhysic 通过 ASM 全量替换 {@code RenderItem.doRender}
     * 并在其中优先调用 {@code ForgeHooksClient.renderEntityItem}，与 CatFrame 对所有
     * 注册模型物品的 Forge IItemRenderer 接管体系结构性互斥（掉落物渲染不可共存），
     * 且其旋转逻辑依赖自身 doRender 的私有状态流转，无法在不扭曲 CatFrame 渲染架构
     * 的前提下兼容。检测到即崩溃，避免静默退化为原版渲染或双重渲染等诡异现象。
     * <p>
     * Rejects an incompatible mod: ItemPhysic replaces {@code RenderItem.doRender}
     * wholesale via ASM and calls {@code ForgeHooksClient.renderEntityItem} first,
     * which structurally conflicts with CatFrame's Forge IItemRenderer takeover of
     * every model-registered item (dropped-item rendering cannot coexist), and its
     * rotation depends on private per-doRender state that cannot be replayed without
     * warping CatFrame's render architecture. Crash on detection instead of silently
     * degrading to vanilla or double rendering.
     *
     * @throws RuntimeException 检测到 ItemPhysic 时崩溃 / thrown when ItemPhysic is present
     */
    public static void rejectItemPhysic() {
        if (Loader.isModLoaded("itemphysic") || classExists("com.creativemd.itemphysic.physics.ClientPhysic")) {
            throw new RuntimeException(
                    "[CatFrame] Incompatible mod detected: ItemPhysic. Both mods take over dropped-item "
                            + "rendering and cannot coexist. Please remove ItemPhysic from your instance.");
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}