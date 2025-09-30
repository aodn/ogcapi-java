package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.tile.model.TileMatrixSets;
import org.springframework.format.FormatterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class CustomWebMvcConfigurer implements WebMvcConfigurer {
    /**
     * In springboot, parameter and path variable conversion isn't done via @JsonCreator but Converter, here
     * we define additional generic converter for the Enum types.
     * @param registry - System pass in argument
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, TileMatrixSets.class, TileMatrixSets::fromValue);
    }
    /**
     * Configure async support timeout for streaming downloads
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(1200000); // 20 minutes for streaming downloads
    }
}
