package decok.dfcdvadstf.catframe.utils;

import java.util.Objects;

public class ObjectsUtil {
    private ObjectsUtil() {}

    public static <T> T requireNonNullElse(T value, T defaultValue) {
        return value != null ? value : Objects.requireNonNull(defaultValue, "defaultValue");
    }
}
