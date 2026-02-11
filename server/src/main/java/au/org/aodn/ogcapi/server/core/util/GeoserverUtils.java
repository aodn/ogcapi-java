package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.ogcapi.server.core.model.LinkModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Slf4j
public class GeoserverUtils {
    /**
     * Fuzzy match utility to compare layer names, ignoring namespace prefixes
     * For example: "underway:nuyina_underway_202122020" matches "nuyina_underway_202122020"
     * For example: "abc/cde" matches "abc"
     *
     * @param text1 - First text to compare
     * @param text2 - Second text to compare
     * @return true if texts match (after removing namespace prefix) and subfix
     */
    public static boolean roughlyMatch(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return false;
        }

        // Remove namespace prefix (text before ":")
        String normalized1 = text1.contains(":") ? text1.substring(text1.indexOf(":") + 1) : text1;
        String normalized2 = text2.contains(":") ? text2.substring(text2.indexOf(":") + 1) : text2;

        // Remove "/" and anything follows
        normalized1 = normalized1.split("/")[0];
        normalized2 = normalized2.split("/")[0];

        return normalized1.equals(normalized2);
    }

    /**
     * Extract typename from WFS or WMS URL query parameters.
     * For WFS URLs, looks for typeName/TYPENAME/typename parameter.
     * For WMS URLs, looks for layers/LAYERS parameter.
     *
     * @param url - The WFS or WMS URL
     * @return typename/layer name if found, empty otherwise
     */
    public static Optional<String> extractLayernameOrTypenameFromUrl(String url) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
            var queryParams = builder.build().getQueryParams();

            // Try WFS parameter name variations (typeName)
            List<String> typeNames = queryParams.get("typeName");
            if (typeNames == null || typeNames.isEmpty()) {
                typeNames = queryParams.get("TYPENAME");
            }
            if (typeNames == null || typeNames.isEmpty()) {
                typeNames = queryParams.get("typename");
            }
            if (typeNames != null && !typeNames.isEmpty()) {
                // URL decode the typename (e.g., "underway%3Aunderway_60" -> "underway:underway_60")
                String typename = UriUtils.decode(typeNames.get(0), StandardCharsets.UTF_8);
                return Optional.of(typename);
            }

            // Try WMS parameter name variations (layers)
            // TODO: the layers might be joined layernames
            List<String> layerNames = queryParams.get("layers");
            if (layerNames == null || layerNames.isEmpty()) {
                layerNames = queryParams.get("LAYERS");
            }
            if (layerNames == null || layerNames.isEmpty()) {
                layerNames = queryParams.get("Layers");
            }
            if (layerNames != null && !layerNames.isEmpty()) {
                // URL decode the layer name
                String layerName = UriUtils.decode(layerNames.get(0), StandardCharsets.UTF_8);
                return Optional.of(layerName);
            }
        } catch (Exception e) {
            log.debug("Failed to extract typename/layer from URL: {}", url, e);
        }
        return Optional.empty();
    }

    /**
     * Extract layer name or typename from a LinkModel.
     * If link title does not match the typename/layername extracted from URL, return the extracted typename/layername.
     *
     * @param link - The LinkModel object
     * @return layer name/typename if found
     */
    public static Optional<String> extractLayernameOrTypenameFromLink(LinkModel link) {
        if (link == null) {
            return Optional.empty();
        }

        Optional<String> extractedName = extractLayernameOrTypenameFromUrl(link.getHref());

        if (link.getTitle() != null && !link.getTitle().isEmpty()) {
            if (extractedName.isPresent() && !roughlyMatch(link.getTitle(), extractedName.get())) {
                log.debug("Link title '{}' does not match type/layer name extracted from URL, return the extracted layer/type name {}", link.getTitle(), extractedName.get());
                return extractedName;
            }
            return Optional.of(link.getTitle());
        }

        return extractedName;
    }
}
