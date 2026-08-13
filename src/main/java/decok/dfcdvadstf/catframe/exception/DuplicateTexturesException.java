package decok.dfcdvadstf.catframe.exception;

public class DuplicateTexturesException extends RuntimeException {
    public DuplicateTexturesException(String message) {
        super("Duplicate sprite " + message + " from" + message + "already defined at the" + message + "");
    }
}
