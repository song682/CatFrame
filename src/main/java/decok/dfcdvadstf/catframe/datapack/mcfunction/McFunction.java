package decok.dfcdvadstf.catframe.datapack.mcfunction;

import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;

/**
 * One parsed {@code .mcfunction} file: an ordered list of command lines.
 * <p>
 * Blank lines and {@code #} comment lines are dropped at parse time; every
 * remaining line keeps its 1-based source line number so runtime failures can
 * point back into the file.
 * </p>
 */
public final class McFunction {

    /** One command line of a function. */
    public static final class Line {

        private final int number;
        private final String command;

        public Line(int number, String command) {
            this.number = number;
            this.command = command;
        }

        /** 1-based line number in the source file. */
        public int getNumber() {
            return number;
        }

        /** Command text without a leading slash, trimmed. */
        public String getCommand() {
            return command;
        }

        @Override
        public String toString() {
            return number + ": " + command;
        }
    }

    private final ResourceLocation id;
    private final String packId;
    private final String path;
    private final List<Line> lines;

    public McFunction(ResourceLocation id, String packId, String path, List<Line> lines) {
        this.id = id;
        this.packId = packId;
        this.path = path;
        this.lines = Collections.unmodifiableList(lines);
    }

    /** Function id, e.g. {@code mymod:tick/heal} for {@code data/mymod/functions/tick/heal.mcfunction}. */
    public ResourceLocation getId() {
        return id;
    }

    /** Pack that supplied the winning file. */
    public String getPackId() {
        return packId;
    }

    /** Pack-relative source path. */
    public String getPath() {
        return path;
    }

    public List<Line> getLines() {
        return lines;
    }

    @Override
    public String toString() {
        return id + " (" + packId + ")";
    }
}
