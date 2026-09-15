package decok.dfcdvadstf.catframe.mixin;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import decok.dfcdvadstf.catframe.resources.builtin.BuiltinPackRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coremod entry point of the early mixin channel of the built-in resource pack
 * framework.
 * <p>
 * The FML coremod stage runs before {@code Minecraft}'s constructor builds the
 * {@code ResourcePackRepository}, so this class registers the early mixins
 * through {@link IEarlyMixinLoader}; they integrate the built-in packs into the
 * repository and the resource pack GUI. The packs themselves are registered by
 * ordinary mod code during {@code preInit} (see {@link BuiltinPackRegistry}),
 * because the config object that gates them is only built in
 * {@code CatFrame.preInit}. Nothing here may touch a game class: the
 * constructor and the mixin list are consumed before FML installs the runtime
 * deobfuscation remapper, so a game class reachable from this class's graph
 * would fail to load under obfuscation.
 * <p>
 * The loader supplied by the mixin config is wired through the build script:
 * the {@code FMLCorePlugin} manifest attribute (production) / the
 * {@code -Dfml.coreMods.load} JVM argument (development).
 * <p>
 * 内置资源包框架 early mixin 通道的 coremod 入口。FML coremod 阶段早于
 * {@code Minecraft} 构造 {@code ResourcePackRepository}，因此本类经
 * {@link IEarlyMixinLoader} 注册 early mixin，把内置包接入仓库与资源包界面；
 * 包本身的注册由普通模组代码在 {@code preInit} 完成（见
 * {@link BuiltinPackRegistry}），因为门控它们的配置对象只在
 * {@code CatFrame.preInit} 中构建。本类不得触碰任何游戏类：构造器与 mixin
 * 清单在 FML 装好运行时反混淆映射之前就被消费，类图中可达的游戏类会在混淆
 * 环境下加载失败。
 * 加载器经由构建脚本接好：生产走 {@code FMLCorePlugin} manifest 属性，
 * 开发走 {@code -Dfml.coreMods.load} JVM 参数。
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({ "decok.dfcdvadstf.catframe.resources.builtin" })
public class CatFrameEarlyMixinPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    /** Must match the config resource shipped in {@code src/main/resources}. */
    private static final String MIXIN_CONFIG = "mixins.catframe.early.json";

    public CatFrameEarlyMixinPlugin() {
        // Intentionally empty: this runs before FML installs the runtime
        // deobfuscation remapper, so no game class may be touched here.
    }

    @Override
    public String getMixinConfig() {
        return MIXIN_CONFIG;
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return IMixins.getEarlyMixins(Mixins.EarlyMixin.class, loadedCoreMods);
    }

    @Override
    public String toString() {
        // The gtnhmixins loader identifies coremods through Object.toString()
        return "CatFrame Built-in Pack Core";
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
