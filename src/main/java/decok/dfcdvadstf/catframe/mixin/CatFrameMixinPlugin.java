package decok.dfcdvadstf.catframe.mixin;

import decok.dfcdvadstf.catframe.Tags;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CatFrameMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return "mixins." + Tags.MODID + ".refmap.json";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    /**
     * Return {@code null} to delegate mixin selection to the JSON configuration
     * ({@code mixins.catframe.json}), which uses the {@code "client"} list for
     * client-only mixins and an empty {@code "mixins"} list (no common mixins).
     * <p>
     * 返回 {@code null} 以将 mixin 选择委托给 JSON 配置（{@code mixins.catframe.json}），
     * 该配置通过 {@code "client"} 列表限定客户端专用 mixin，{@code "mixins"} 为空
     * （无公共 mixin），从而在专用服务器上不会尝试加载客户端类。
     * </p>
     */
    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }
}
