package au.org.aodn.ogcapi.server.configuration;

import au.org.aodn.ogcapi.tile.model.TileMatrixSets;
import org.springframework.format.FormatterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class CustomWebMvcConfigurer implements WebMvcConfigurer {

    /**
     * In springboot, parameter and path variable conversion isn't done via @JsonCreator but Converter, here
     * we define additional generic converter for the Enum types.
     * @param registry
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, TileMatrixSets.class, source -> TileMatrixSets.fromValue(source));
    }
}
