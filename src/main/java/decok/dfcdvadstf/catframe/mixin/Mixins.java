package decok.dfcdvadstf.catframe.mixin;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

/**
 * Declarative registry of every mixin shipped by the mod. All of them are
 * served by a single ordinary channel: {@link CatFrameMixinPlugin} selects them
 * from {@link NormalMixin} when the {@code mixins.catframe.json} config loads.
 * Every builder must therefore leave the phase unset ({@code null}), as
 * required by the
 * {@link org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin}
 * channel.
 * <p>
 * Load-time state (physical side, {@code applyIf} conditions, required and
 * excluded target mods) is evaluated by the GTNHMixins loaders when the
 * class lists are built, instead of being frozen into JSON arrays.
 * <p>
 * 本模组全部 mixin 的声明式注册表。所有 mixin 由单一普通通道供给：
 * {@link CatFrameMixinPlugin} 在 {@code mixins.catframe.json} 配置加载时从
 * {@link NormalMixin} 中选择。因此每个 builder 都必须不设置 phase（保持
 * {@code null}）——这是
 * {@link org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin}
 * 通道的要求。加载时的状态（物理侧别、{@code applyIf} 条件、必需/排除的
 * 目标模组）由 GTNHMixins 的加载器在生成类清单时求值，而不是固化进 JSON
 * 数组。
 */
public class Mixins {
    /**
     * Mixins served by {@link CatFrameMixinPlugin} through the ordinary
     * {@code mixins.catframe.json} config; every builder must leave the phase
     * unset ({@code null}).
     * <p>
     * 由 {@link CatFrameMixinPlugin} 经普通 {@code mixins.catframe.json}
     * 配置供给的 mixin；每个 builder 都必须不设置 phase（保持 {@code null}）。
     */
    public enum NormalMixin implements IMixins {

        /** Repository and GUI integration of the built-in resource packs. */
        BUILTIN_RESOURCE_PACKS(Side.CLIENT,
                "middle.MixinBuiltinResourcePackRepository",
                "middle.MixinBuiltinResourcePackRepositoryEntry",
                "middle.MixinBuiltinResourcePackListEntryFound"),

        /** Keyboard dispatch and the public keyboard event bridge for screens. */
        SCREEN_INPUT(Side.CLIENT, "middle.event.MixinGuiScreen", "middle.event.MixinGuiScreenEventBridge"),

        /** Render pipeline interception: block rendering and chunk compile hooks. */
        RENDERING(Side.CLIENT, "middle.render.MixinRenderBlocks", "middle.render.MixinWorldRenderer"),

        /** Built-in pack startup bootstrap on the first vanilla refresh after preInit. */
        BUILTIN_PACK_BOOTSTRAP(Side.CLIENT, "middle.MixinMinecraftRefreshResources");

        private final MixinBuilder builder;

        NormalMixin(Side side, String... mixins) {
            this.builder = new MixinBuilder().addSidedMixins(side, mixins);
        }

        @Override
        public MixinBuilder getBuilder() {
            return this.builder;
        }
    }
}
