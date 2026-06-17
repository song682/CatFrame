package decok.dfcdvadstf.catframe.model.state;

import net.minecraft.world.IBlockAccess;

import java.util.Map;

/**
 * Implement this interface on your Block to register it for blockstate-based JSON model rendering.
 * <p>
 * The system will:
 * 1. Load the blockstate JSON from assets/{namespace}/blockstates/{name}.json
 * 2. On each render, call {@link #getStateProperties} to get the current property map
 * 3. Match properties against blockstate variants to select the correct model
 * <p>
 * v0.3.0 adds optional {@link CatStateDefinition} support via {@link #getStateDefinition()}
 * and {@link #getBlockState(IBlockAccess, int, int, int, int)} for type-safe property handling.
 */
public interface IBlockStateProvider {

    /**
     * The namespace where the blockstate JSON is located.
     * e.g., "mymod" will look for assets/mymod/blockstates/{name}.json
     */
    String getBlockstateNamespace();

    /**
     * The blockstate file name (without .json extension).
     * e.g., "cake" will load assets/{namespace}/blockstates/cake.json
     */
    String getBlockstateName();

    /**
     * Convert the current block state (world position + metadata) into a property map.
     * The returned map is used to match against blockstate variants.
     * <p>
     * For example, a cake with metadata=3 might return {"bites": "3"}.
     * This gets matched against variant key "bites=3" in the blockstate JSON.
     *
     * @param world    the world instance
     * @param x        block X coordinate
     * @param y        block Y coordinate
     * @param z        block Z coordinate
     * @param metadata the block's metadata value
     * @return property map for variant matching
     */
    Map<String, String> getStateProperties(IBlockAccess world, int x, int y, int z, int metadata);

    /**
     * v0.3.0: Optionally return a {@link CatStateDefinition} that defines all typed properties
     * for this block. When non-null, the rendering system can use {@link CatBlockState}
     * for more efficient variant matching and O(1) state transitions.
     * <p>
     * Default returns {@code null} (no typed property definition).
     *
     * @return CatStateDefinition defining this block's typed properties, or null
     */
    default CatStateDefinition<?> getStateDefinition() {
        return null;
    }

    /**
     * v0.3.0: Optionally return a {@link CatBlockState} for the given world position and metadata.
     * This is used together with {@link #getStateDefinition()} to enable type-safe
     * property-based model dispatch.
     * <p>
     * Default implementation returns {@code null}, falling back to the legacy
     * {@link #getStateProperties(IBlockAccess, int, int, int, int)} path.
     *
     * @param world    the world instance
     * @param x        block X coordinate
     * @param y        block Y coordinate
     * @param z        block Z coordinate
     * @param metadata the block's metadata value
     * @return a CatBlockState for this position, or null
     */
    default CatBlockState getBlockState(IBlockAccess world, int x, int y, int z, int metadata) {
        return null;
    }
}
