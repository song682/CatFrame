package decok.dfcdvadstf.catframe.datapack.mcfunction;

import decok.dfcdvadstf.catframe.datapack.DataPackErrors;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written {@code .mcfunction} parser (worklist D5, reconsidered).
 * <p>
 * The format is line-oriented, so no grammar is needed: each non-blank line
 * that is not a comment is one command. Rules:
 * </p>
 * <ul>
 * <li>a UTF-8 byte order mark is ignored;</li>
 * <li>{@code \r\n}, {@code \n} and {@code \r} all end a line;</li>
 * <li>a line whose first non-blank character is {@code #} is a comment;</li>
 * <li>one leading {@code /} is stripped, so {@code /say hi} and {@code say hi}
 *     are the same command;</li>
 * <li>a line ending in {@code \} continues on the next line: the backslash is
 *     removed and the lines are concatenated with nothing inserted, so
 *     {@code say Hel\} + {@code lo} is {@code say Hello}. A comment line does not
 *     continue. The command keeps the number of its first line;</li>
 * <li>no inline comments.</li>
 * </ul>
 * <p>
 * Parsing never throws. Command validity is decided at run time by the
 * server's command manager, which reports failures to the caller exactly like
 * a typed command would.
 * </p>
 */
public final class McFunctionParser {

    private McFunctionParser() {
    }

    /**
     * Parses function source text.
     *
     * @param id      function id being parsed (for the returned object)
     * @param packId  producing pack
     * @param path    pack-relative path (for the report)
     * @param text    file content, {@code null} when unreadable
     * @param errors  report sink
     * @return the parsed function, or {@code null} when the text was unreadable
     */
    public static McFunction parse(net.minecraft.util.ResourceLocation id, String packId, String path,
                                   String text, DataPackErrors errors) {
        if (text == null) {
            errors.error(packId, path, "file could not be read");
            return null;
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        List<McFunction.Line> lines = new ArrayList<>();
        String[] raw = text.split("\r\n|\n|\r", -1);
        int i = 0;
        while (i < raw.length) {
            String first = raw[i];
            int number = i + 1;
            i++;
            String trimmed = first.trim();
            if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
                continue;
            }
            // Join continuation lines: a trailing backslash splices in the next line.
            StringBuilder joined = new StringBuilder();
            String segment = first;
            while (true) {
                boolean continued = segment.endsWith("\\");
                joined.append(continued ? segment.substring(0, segment.length() - 1) : segment);
                if (!continued || i >= raw.length) {
                    break;
                }
                segment = raw[i];
                i++;
            }
            String command = joined.toString().trim();
            if (command.startsWith("/")) {
                command = command.substring(1).trim();
            }
            if (command.isEmpty()) {
                continue;
            }
            lines.add(new McFunction.Line(number, command));
        }
        return new McFunction(id, packId, path, lines);
    }
}
