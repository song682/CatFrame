package decok.dfcdvadstf.catframe.mixin;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

/**
 * Declarative registry of every mixin shipped by the mod, grouped by loading
 * channel. A channel consumes builders of exactly one phase, so the
 * declarations are split into separate enums:
 * <ul>
 * <li>{@link EarlyMixin} is registered by {@link CatFrameEarlyMixinPlugin}
 * through the {@code mixins.catframe.early.json} config during the FML coremod
 * stage and must use {@code Phase.EARLY};</li>
 * <li>{@link NormalMixin} is selected by {@link CatFrameMixinPlugin} when the
 * ordinary {@code mixins.catframe.json} config loads and must leave the phase
 * unset ({@code null}), as required by the
 * {@link org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin}
 * channel.</li>
 * </ul>
 * Load-time state (physical side, {@code applyIf} conditions, required and
 * excluded target mods) is evaluated by the GTNHMixins loaders when the
 * class lists are built, instead of being frozen into JSON arrays.
 * <p>
 * 本模组全部 mixin 的声明式注册表，按加载通道分组。一个通道只消费单一
 * phase 的 builder，因此声明拆分为两个枚举：
 * <ul>
 * <li>{@link EarlyMixin} 由 {@link CatFrameEarlyMixinPlugin} 经
 * {@code mixins.catframe.early.json} 在 FML coremod 阶段注册，必须使用
 * {@code Phase.EARLY}；</li>
 * <li>{@link NormalMixin} 由 {@link CatFrameMixinPlugin} 在普通
 * {@code mixins.catframe.json} 配置加载时选择，必须不设置 phase（保持
 * {@code null}）——这是
 * {@link org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin}
 * 通道的要求。</li>
 * </ul>
 * 加载时的状态（物理侧别、{@code applyIf} 条件、必需/排除的目标模组）由
 * GTNHMixins 的加载器在生成类清单时求值，而不是固化进 JSON 数组。
 */
public class Mixins {

    /**
     * Early mixins, registered by {@link CatFrameEarlyMixinPlugin} before any
     * game class is loaded; every builder must set {@code Phase.EARLY}.
     * <p>
     * early mixin：由 {@link CatFrameEarlyMixinPlugin} 在任何游戏类加载之前
     * 注册；每个 builder 都必须设置 {@code Phase.EARLY}。
     */
    public enum EarlyMixin implements IMixins {

        /** Repository and GUI integration of the built-in resource packs. */
        BUILTIN_RESOURCE_PACKS(new MixinBuilder("Built-in resource pack repository integration")
                .addClientMixins(
                        "early.MixinResourcePackRepository",
                        "early.MixinResourcePackRepositoryEntry",
                        "early.MixinResourcePackListEntryFound")
                .setPhase(Phase.EARLY));

        private final MixinBuilder builder;

        EarlyMixin(MixinBuilder builder) {
            this.builder = builder;
        }

        @Override
        public MixinBuilder getBuilder() {
            return this.builder;
        }
    }

    /**
     * Ordinary mixins, selected by {@link CatFrameMixinPlugin} when the regular
     * config loads; every builder must leave the phase unset ({@code null}).
     * <p>
     * 普通 mixin：由 {@link CatFrameMixinPlugin} 在普通配置加载时选择；每个
     * builder 都必须不设置 phase（保持 {@code null}）。
     */
    public enum NormalMixin implements IMixins {

        /** Keyboard dispatch and the public keyboard event bridge for screens. */
        SCREEN_INPUT(Side.CLIENT, "middle.MixinGuiScreen", "middle.MixinGuiScreenEventBridge"),

        /** Render pipeline interception: block rendering and chunk compile hooks. */
        RENDERING(Side.CLIENT, "middle.MixinRenderBlocks", "middle.MixinWorldRenderer"),

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
