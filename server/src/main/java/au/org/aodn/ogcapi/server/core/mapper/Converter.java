package au.org.aodn.ogcapi.server.core.mapper;

@FunctionalInterface
public interface Converter<F, T> {
    T convert(F from);
}
