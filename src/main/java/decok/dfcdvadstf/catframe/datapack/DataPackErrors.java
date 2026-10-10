package decok.dfcdvadstf.catframe.datapack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Consolidated problem report for one datapack load or reload pass (worklist A6).
 * <p>
 * Every discovery, parse, build and apply step funnels its findings into a single
 * collector so a reload produces one coherent report instead of scattered log
 * noise. Problems never throw; the pipeline keeps running on bad data and the
 * report is surfaced to the log and to {@code /datapack} command feedback.
 * </p>
 * <p>
 * Each {@link Problem} carries the producing pack, the pack-relative file path
 * (when known) and a human-readable message. {@code packId} and {@code path} are
 * nullable for problems that originate outside a single file (e.g. build-phase
 * dependency cycles).
 * </p>
 */
public final class DataPackErrors {

    private static final Logger LOGGER = LogManager.getLogger(DataPackErrors.class);

    /**
     * Problem severity. {@code WARNING} signals recovered or cosmetic issues
     * (duplicate pack ids, empty tags); {@code ERROR} signals data that was
     * rejected or failed to apply.
     */
    public enum Severity {
        WARNING,
        ERROR
    }

    /** One immutable problem record. */
    public static final class Problem {

        private final Severity severity;
        private final String packId;
        private final String path;
        private final String message;

        public Problem(Severity severity, String packId, String path, String message) {
            this.severity = severity;
            this.packId = packId;
            this.path = path;
            this.message = message;
        }

        public Severity getSeverity() {
            return severity;
        }

        /** Producing pack id, or {@code null} when not tied to a single pack. */
        public String getPackId() {
            return packId;
        }

        /** Pack-relative file path, or {@code null} when not tied to a single file. */
        public String getPath() {
            return path;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[').append(severity).append("] ");
            if (packId != null) {
                sb.append(packId);
                if (path != null) {
                    sb.append('/').append(path);
                }
                sb.append(": ");
            }
            sb.append(message);
            return sb.toString();
        }
    }

    private final List<Problem> problems = new ArrayList<>();

    public void warning(String packId, String path, String message) {
        report(Severity.WARNING, packId, path, message);
    }

    public void error(String packId, String path, String message) {
        report(Severity.ERROR, packId, path, message);
    }

    public void report(Severity severity, String packId, String path, String message) {
        problems.add(new Problem(severity, packId, path, message));
    }

    /** All recorded problems, in report order. */
    public List<Problem> getProblems() {
        return Collections.unmodifiableList(problems);
    }

    public int count(Severity severity) {
        int n = 0;
        for (Problem p : problems) {
            if (p.getSeverity() == severity) {
                n++;
            }
        }
        return n;
    }

    public boolean hasErrors() {
        return count(Severity.ERROR) > 0;
    }

    public int size() {
        return problems.size();
    }

    /**
     * Dumps the full report to the log: one summary line plus one line per
     * problem. Called once per apply pass by the manager.
     */
    public void logAll() {
        if (problems.isEmpty()) {
            LOGGER.info("Datapack pass finished with no problems");
            return;
        }
        LOGGER.info("Datapack pass finished: {} error(s), {} warning(s)",
                count(Severity.ERROR), count(Severity.WARNING));
        for (Problem p : problems) {
            if (p.getSeverity() == Severity.ERROR) {
                LOGGER.error("{}", p);
            } else {
                LOGGER.warn("{}", p);
            }
        }
    }
}
