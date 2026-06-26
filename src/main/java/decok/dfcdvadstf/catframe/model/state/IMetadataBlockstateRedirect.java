package decok.dfcdvadstf.catframe.model.state;

import net.minecraft.block.Block;

/**
 * Redirects blockstate lookups on a per-metadata basis.
 * <p>
 * When registered for a {@link Block}, the baking pipeline will delegate
 * each metadata value (0–15) to a different blockstate JSON file
 * instead of using the block's own blockstate. This allows splitting
 * multi-color blocks (e.g. stained_glass_pane) into 16 per-color files.
 * </p>
 *
 * @see decok.dfcdvadstf.catframe.model.VMMDataLoader#registerBlockstateRedirect
 */
@FunctionalInterface
public interface IMetadataBlockstateRedirect {

    /**
     * Return the blockstate filename (without {@code .json} suffix) for the given metadata value,
     * or {@code null} to skip this metadata value.
     */
    String redirect(int metadata);
}
