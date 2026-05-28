package decok.dfcdvadstf.catframe.model;

import net.minecraft.world.IBlockAccess;

import java.util.Map;

/**
 * Implement this interface on your Block to register it for blockstate-based JSON model rendering.
 *
 * The system will:
 * 1. Load the blockstate JSON from assets/{namespace}/blockstates/{name}.json
 * 2. On each render, call {@link #getStateProperties} to get the current property map
 * 3. Match properties against blockstate variants to select the correct model
 *
 * Example usage (cake with bites):
 * <pre>
 * public class BlockModCake extends Block implements IBlockStateProvider {
 *     public String getBlockstateNamespace() { return "mymod"; }
 *     public String getBlockstateName() { return "cake"; }
 *     public Map<String, String> getStateProperties(IBlockAccess world, int x, int y, int z, int metadata) {
 *         Map<String, String> props = new HashMap<>();
 *         props.put("bites", String.valueOf(metadata));
 *         return props;
 *     }
 * }
 * </pre>
 *
 * And the corresponding blockstate JSON (assets/mymod/blockstates/cake.json):
 * <pre>
 * {
 *   "variants": {
 *     "bites=0": { "model": "block/cake" },
 *     "bites=1": { "model": "block/cake_slice1" },
 *     "bites=2": { "model": "block/cake_slice2" },
 *     ...
 *   }
 * }
 * </pre>
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
   *
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
}
