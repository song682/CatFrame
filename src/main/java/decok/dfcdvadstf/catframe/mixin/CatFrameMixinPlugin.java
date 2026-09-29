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
     */
    @Override
    public List<String> getMixins() {
        return IMixins.getMixins(Mixins.class);
    }

    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {}

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {}
}
