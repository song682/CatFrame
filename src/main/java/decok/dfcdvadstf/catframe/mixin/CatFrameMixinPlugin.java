package decok.dfcdvadstf.catframe.mixin;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import decok.dfcdvadstf.catframe.Tags;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CatFrameMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return "mixins." + Tags.MODID + ".refmap.json";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    /**
     * Selects the regular mixins from {@link Mixins.NormalMixin} at config load
     * time: the GTNHMixins builder evaluates the load-time state (physical side,
     * {@code applyIf} conditions) and returns only the classes valid for this
     * run, which replaces the former static {@code "client"} list of the JSON
     * configuration.
     * <p>
     * 在配置加载时从 {@link Mixins.NormalMixin} 选择普通 mixin：GTNHMixins 的
     * builder 依据加载时的状态（物理侧别、{@code applyIf} 条件）只返回本次
     * 运行有效的类，取代原先 JSON 配置中的静态 {@code "client"} 列表。
     * </p>
     */
    @Override
    public List<String> getMixins() {
        return IMixins.getMixins(Mixins.NormalMixin.class);
    }

    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {}

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {}
}
