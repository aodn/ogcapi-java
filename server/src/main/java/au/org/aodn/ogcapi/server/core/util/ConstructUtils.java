package au.org.aodn.ogcapi.server.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;

import java.io.IOException;
import java.util.Optional;


public class ConstructUtils {

    @Setter
    private static ObjectMapper objectMapper = new ObjectMapper();  // Give default

    public static  <T> Optional<T> constructByJsonString(String jsonString, Class<T> clazz) {
        try {
            return Optional.ofNullable(objectMapper.readValue(jsonString, clazz));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }
}
