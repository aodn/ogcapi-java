package au.org.aodn.ogcapi.server.core.util;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
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

    public static <T> void copyIgnoringNull(T source, T target) {
        if(source == null || target == null) return;

        BeanWrapper src = new BeanWrapperImpl(source);
        BeanWrapper tgt = new BeanWrapperImpl(target);

        for (PropertyDescriptor pd : src.getPropertyDescriptors()) {
            if (pd.getReadMethod() == null || pd.getWriteMethod() == null) continue;
            String name = pd.getName();
            if ("class".equals(name)) continue;

            Object value = src.getPropertyValue(name);
            if (value != null) {
                tgt.setPropertyValue(name, value);
            }
        }
    }
}
