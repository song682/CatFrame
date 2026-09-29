package decok.dfcdvadstf.catframe.resources.atlas.layout;

import java.util.Collections;
import java.util.List;

/**
 * Atlas stitch failure — carries a readable sprite list and size info (mirrors
 * 26.1.2 {@code StitcherException}: when atlas capacity is insufficient, all
 * sprites awaiting placement are listed for easier debugging).
 * <p>
 * Trigger condition: during layout growth either axis exceeds the smaller of
 * {@code GL_MAX_TEXTURE_SIZE} and 16384. The caller ({@code CatAtlasManager})
 * catches it and falls back to the vanilla stitching path, so the game does not crash.
 */
public class TextureStitchException extends RuntimeException {

    /** Icon name of the sprite that currently cannot be placed. */
    private final String currentName;
    /** Icon names of all unplaced sprites (including the current one). */
    private final List<String> unplacedNames;
    /** Used storage size at failure (bounding box, not rounded to 2^n). */
    private final int usedWidth;
    private final int usedHeight;
    /** Hardware / soft size limit. */
    private final int maxWidth;
    private final int maxHeight;

    public TextureStitchException(String currentName, List<String> unplacedNames,
                                  int usedWidth, int usedHeight,
                                  int maxWidth, int maxHeight) {
        super(buildMessage(currentName, unplacedNames, usedWidth, usedHeight, maxWidth, maxHeight));
        this.currentName = currentName;
        this.unplacedNames = Collections.unmodifiableList(unplacedNames);
        this.usedWidth = usedWidth;
        this.usedHeight = usedHeight;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    private static String buildMessage(String currentName, List<String> unplacedNames,
                                       int usedWidth, int usedHeight,
                                       int maxWidth, int maxHeight) {
        return "Unable to fit sprite '" + currentName + "' into texture atlas: "
                + "storage " + usedWidth + "x" + usedHeight
                + " exceeds limit " + maxWidth + "x" + maxHeight
                + "; unplaced sprites (" + unplacedNames.size() + "): " + unplacedNames;
    }

    /** Icon name of the sprite that currently cannot be placed. */
    public String getCurrentName() {
        return currentName;
    }

    /** Icon names of all unplaced sprites. */
    public List<String> getUnplacedNames() {
        return unplacedNames;
    }

    /** Used storage size at failure (bounding box). */
    public int getUsedWidth() {
        return usedWidth;
    }

    public int getUsedHeight() {
        return usedHeight;
    }

    /** Size limit (min(GL_MAX_TEXTURE_SIZE, 16384)). */
    public int getMaxWidth() {
        return maxWidth;
    }

    public int getMaxHeight() {
        return maxHeight;
    }
}
