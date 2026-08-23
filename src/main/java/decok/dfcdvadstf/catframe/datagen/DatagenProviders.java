package decok.dfcdvadstf.catframe.datagen;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.datagen.asset.AssetCopyProvider;
import decok.dfcdvadstf.catframe.datagen.migration.CatFrameItemStatesGen;
import decok.dfcdvadstf.catframe.datagen.migration.CatFrameTagsGen;
import decok.dfcdvadstf.catframe.datagen.migration.MinecraftBlockStatesGen;
import decok.dfcdvadstf.catframe.datagen.migration.MinecraftItemStatesGen;
import decok.dfcdvadstf.catframe.datagen.migration.MinecraftModelsGen;

/**
 * Central registry of all datagen providers.
 * <p>
 * Every migration provider (tags, blockstates, models, item states, asset
 * copies) is registered here so {@link DatagenEntrypoint} stays a thin shell.
 * <p>
 * 全部 datagen provider 的集中注册处。所有迁移 provider（tags、blockstate、
 * 模型、items 决策树、资产复制）在此注册，使 {@link DatagenEntrypoint} 保持轻薄。
 */
@SideOnly(Side.CLIENT)
public final class DatagenProviders {

    private DatagenProviders() {
    }

    /**
     * Register all providers onto the generator.
     *
     * @param generator the datagen generator to populate
     */
    public static void registerAll(DataGenerator generator) {
        generator.addProvider(new MinecraftBlockStatesGen())
                .addProvider(new MinecraftModelsGen())
                .addProvider(new MinecraftItemStatesGen())
                .addProvider(new CatFrameItemStatesGen())
                .addProvider(new CatFrameTagsGen())
                .addProvider(new AssetCopyProvider());
    }
}
