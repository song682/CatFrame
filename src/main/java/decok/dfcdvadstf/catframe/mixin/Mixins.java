package decok.dfcdvadstf.catframe.mixin;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

/**
 * Declarative registry of every mixin shipped by the mod. All of them are
 * served by a single ordinary channel: {@link CatFrameMixinPlugin} selects them
 * from {@code Mixin} when the {@code mixins.catframe.json} config loads.
 * Every builder must therefore leave the phase unset ({@code null}), as
 * required by the
 * {@link org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin}
 * channel.
 */
public enum Mixins implements IMixins {

        /** Repository and GUI integration of the built-in resource packs. */
        BUILTIN_RESOURCE_PACKS(Side.CLIENT,
                "middle.MixinBuiltinResourcePackRepository",
                "middle.MixinBuiltinResourcePackRepositoryEntry",
                "middle.MixinBuiltinResourcePackListEntryFound"),

        /** Keyboard dispatch and the public keyboard event bridge for screens. */
        SCREEN_INPUT(Side.CLIENT, "middle.event.MixinGuiScreen", "middle.event.MixinGuiScreenEventBridge"),

        /** Render pipeline interception: block rendering and chunk compile hooks. */
        RENDERING(Side.CLIENT, "middle.render.MixinRenderBlocks", "middle.render.MixinWorldRenderer",
                "middle.render.MixinEntityDiggingFX"),

        /** Built-in pack startup bootstrap on the first vanilla refresh after preInit. */
        BUILTIN_PACK_BOOTSTRAP(Side.CLIENT, "middle.MixinMinecraftRefreshResources");

        private final MixinBuilder builder;

        Mixins(Side side, String... mixins) {
            this.builder = new MixinBuilder().addSidedMixins(side, mixins);
        }

        @Override
        public MixinBuilder getBuilder() {
            return this.builder;
        }

}
