package au.org.aodn.ogcapi.server.core.util;

import java.util.Optional;
import java.util.function.Supplier;

public class CommonUtils {
    public static <T> Optional<T> safeGet(Supplier<T> supplier) {
        try {
            return Optional.of(supplier.get());
        } catch (
                NullPointerException
                | IndexOutOfBoundsException
                | ClassCastException ignored) {
            return Optional.empty();
        }
    }
}
