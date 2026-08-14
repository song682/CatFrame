package decok.dfcdvadstf.catframe.resources.atlas.layout;

import java.util.Collections;
import java.util.List;

/**
 * 图集缝合失败异常 —— 携带可读的 sprite 清单与尺寸信息（对标 26.1.2
 * {@code StitcherException}：图集容量不足时列出全部待放置 sprite，便于调试定位）。
 * <p>
 * 触发条件：布局扩展时任一轴超过 {@code GL_MAX_TEXTURE_SIZE} 与 16384 的较小者。
 * 调用方（{@code CatAtlasManager}）捕获后降级为原版缝合路径，游戏不崩溃。
 *
 * <p>Thrown when atlas packing overflows the texture size limit; carries the
 * full list of sprites that could not be placed for a debuggable crash.
 */
public class CatStitchException extends RuntimeException {

    /** 当前无法放置的 sprite icon 名。 */
    private final String currentName;
    /** 全部未放置 sprite 的 icon 名清单（含当前项）。 */
    private final List<String> unplacedNames;
    /** 失败时的已用存储尺寸（未 2^n 化的包围盒）。 */
    private final int usedWidth;
    private final int usedHeight;
    /** 硬件/软性尺寸上限。 */
    private final int maxWidth;
    private final int maxHeight;

    public CatStitchException(String currentName, List<String> unplacedNames,
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

    /** 当前无法放置的 sprite icon 名。 */
    public String getCurrentName() {
        return currentName;
    }

    /** 全部未放置 sprite 的 icon 名清单。 */
    public List<String> getUnplacedNames() {
        return unplacedNames;
    }

    /** 失败时的已用存储尺寸（包围盒）。 */
    public int getUsedWidth() {
        return usedWidth;
    }

    public int getUsedHeight() {
        return usedHeight;
    }

    /** 尺寸上限（min(GL_MAX_TEXTURE_SIZE, 16384)）。 */
    public int getMaxWidth() {
        return maxWidth;
    }

    public int getMaxHeight() {
        return maxHeight;
    }
}
