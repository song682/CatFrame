package decok.dfcdvadstf.catframe.mixin;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import decok.dfcdvadstf.catframe.resources.builtin.BuiltinPackDescriptor;
import decok.dfcdvadstf.catframe.resources.builtin.BuiltinPackRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coremod entry point of the built-in resource pack framework.
 * <p>
 * The FML coremod stage is the only code that runs before {@code Minecraft}'s
 * constructor builds the {@code ResourcePackRepository}, so this class does two
 * things from there:
 * <ul>
 * <li>seeds {@link BuiltinPackRegistry} with pure data (no game class may be
 * touched at this point), letting the vanilla repository constructor restore
 * the enabled state of built-in packs from {@code options.txt} through its own
 * name-matching loop;</li>
 * <li>registers the early mixins through {@link IEarlyMixinLoader}, which
 * integrate the built-in packs into the repository and the resource pack
 * GUI.</li>
 * </ul>
 * The loader supplied by the mixin config is wired through the build script:
 * the {@code FMLCorePlugin} manifest attribute (production) / the
 * {@code -Dfml.coreMods.load} JVM argument (development).
 * <p>
 * 内置资源包框架的 coremod 入口。FML coremod 阶段是早于 {@code Minecraft} 构造
 * {@code ResourcePackRepository} 的唯一执行时机，因此本类在这里做两件事：
 * ①向 {@link BuiltinPackRegistry} 播种纯数据（此阶段不得触碰任何游戏类），让原版
 * 仓库构造器自身的按名匹配循环从 {@code options.txt} 恢复内置包启用状态；
 * ②经 {@link IEarlyMixinLoader} 注册 early mixin，把内置包接入仓库与资源包界面。
 * 加载器经由构建脚本接好：生产走 {@code FMLCorePlugin} manifest 属性，
 * 开发走 {@code -Dfml.coreMods.load} JVM 参数。
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({ "decok.dfcdvadstf.catframe.resources.builtin" })
public class CatFrameEarlyMixinPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    /** Must match the config resource shipped in {@code src/main/resources}. */
    private static final String MIXIN_CONFIG = "mixins.catframe.early.json";

    public CatFrameEarlyMixinPlugin() {
        BuiltinPackRegistry.register(new BuiltinPackDescriptor(
                "example",
                "resourcePack.catframe.builtin.example.name",
                "resourcePack.catframe.builtin.example.description"));
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
