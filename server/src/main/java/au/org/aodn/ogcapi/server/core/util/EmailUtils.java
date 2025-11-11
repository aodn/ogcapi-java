package au.org.aodn.ogcapi.server.core.util;

import au.org.aodn.ogcapi.features.model.MultipolygonGeoJSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Utility for email-related operations
 */
@Slf4j
public class EmailUtils {

    /**
     * Read a base64 encoded image from resources
     * @param filename - the filename in /img/ directory
     * @return base64 encoded image as data URL
     * @throws IOException if resource not found
     */
    public static String readBase64Image(String filename) throws IOException {
        InputStream is = EmailUtils.class.getResourceAsStream("/img/" + filename);
        if (is == null) {
            throw new IOException("Resource not found: /img/" + filename);
        }
        return "data:image/png;base64," + new String(is.readAllBytes()).trim();
    }

    /**
     * Generate the complete subsetting section HTML (header + bbox + time range)
     * Returns empty string if no subsetting is applied
     */
    public static String generateSubsettingSection(
            String startDate,
            String endDate,
            Object multipolygon,
            ObjectMapper objectMapper
    ) {
        try {
            // Format dates
            String displayStartDate = (startDate != null && !startDate.equals(DatetimeUtils.NON_SPECIFIED_DATE))
                    ? startDate.replace("-", "/") : "";
            String displayEndDate = (endDate != null && !endDate.equals(DatetimeUtils.NON_SPECIFIED_DATE))
                    ? endDate.replace("-", "/") : "";

            // Check if dates are specified
            boolean hasDateSubsetting = !displayStartDate.isEmpty() || !displayEndDate.isEmpty();

            // Check if bbox is specified
            boolean hasBboxSubsetting = multipolygon != null && !isEmptyMultiPolygon(multipolygon);

            // If no subsetting at all, return empty string
            if (!hasDateSubsetting && !hasBboxSubsetting) {
                return "";
            }

            StringBuilder html = new StringBuilder();

            // Add subsetting header
            if (hasBboxSubsetting || hasDateSubsetting) {
                html.append(buildSubsettingHeader());
            }

            // Add bbox section if present
            if (hasBboxSubsetting) {
                html.append(buildBboxWrapper(generateBboxHtml(multipolygon, objectMapper)));
            }

            // Add spacing between bbox and time range if both exist
            if (hasBboxSubsetting && hasDateSubsetting) {
                html.append(buildSpacerSection());
            }

            // Add time range section if present
            if (hasDateSubsetting) {
                html.append(buildTimeRangeWrapper(displayStartDate, displayEndDate));
            }

            return html.toString();

        } catch (Exception e) {
            log.error("Error generating subsetting section", e);
            return "";
        }
    }

    /**
     * Generate HTML content for bounding box data only (without wrapper)
     * @param multipolygon - the multipolygon object
     * @param objectMapper - Jackson ObjectMapper for JSON processing
     * @return HTML string for bbox data rows
     */
    public static String generateBboxHtml(Object multipolygon, ObjectMapper objectMapper) {
        try {
            if (multipolygon == null) {
                return "";
            }

            // Extract coordinates directly from the object
            List<List<List<List<BigDecimal>>>> coordinates = extractCoordinates(multipolygon, objectMapper);

            if (coordinates == null || coordinates.isEmpty()) {
                return "";
            }

            StringBuilder html = new StringBuilder();
            int bboxCounter = 0;

            // Process each polygon separately
            for (List<List<List<BigDecimal>>> polygon : coordinates) {
                // Find min/max for THIS polygon only
                double minLon = Double.MAX_VALUE;
                double maxLon = Double.NEGATIVE_INFINITY;
                double minLat = Double.MAX_VALUE;
                double maxLat = Double.NEGATIVE_INFINITY;

                for (List<List<BigDecimal>> ring : polygon) {
                    for (List<BigDecimal> point : ring) {
                        if (point.size() >= 2) {
                            double lon = point.get(0).doubleValue();
                            double lat = point.get(1).doubleValue();
                            minLon = Math.min(minLon, lon);
                            maxLon = Math.max(maxLon, lon);
                            minLat = Math.min(minLat, lat);
                            maxLat = Math.max(maxLat, lat);
                        }
                    }
                }

                // Use BboxUtils to normalize this polygon's bbox
                MultiPolygon normalizedBbox = BboxUtils.normalizeBbox(minLon, maxLon, minLat, maxLat);

                // Build HTML for each normalized bbox
                for (int i = 0; i < normalizedBbox.getNumGeometries(); i++) {
                    Polygon normalizedPolygon = (Polygon) normalizedBbox.getGeometryN(i);
                    Envelope envelope = normalizedPolygon.getEnvelopeInternal();

                    String north = "" + envelope.getMaxY();
                    String south = "" + envelope.getMinY();
                    String west = "" + envelope.getMinX();
                    String east = "" + envelope.getMaxX();

                    // Add spacing between multiple bboxes
                    if (bboxCounter > 0) {
                        html.append("<tr><td style=\"font-size:0;padding:0;word-break:break-word;\">")
                                .append("<div style=\"height:24px;line-height:24px;\">&#8202;</div>")
                                .append("</td></tr>");
                    }

                    bboxCounter++;
                    int displayIndex = (coordinates.size() > 1 || normalizedBbox.getNumGeometries() > 1) ? bboxCounter : 0;
                    html.append(buildBboxSection(north, south, west, east, displayIndex));
                }
            }

            return html.toString();

        } catch (Exception e) {
            log.error("Error generating bbox HTML", e);
            return "";
        }
    }

    /**
     * Check if multipolygon is empty or represents the full world
     */
    private static boolean isEmptyMultiPolygon(Object multipolygon) {
        try {
            // Null check
            if (multipolygon == null) {
                return true;
            }

            // Check for "non-specified" string
            if (multipolygon instanceof String && "non-specified".equals(multipolygon.toString())) {
                return true;
            }

            if (multipolygon instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) multipolygon;
                Object coords = map.get("coordinates");

                // No coordinates field at all
                if (coords == null) {
                    return true;
                }

                if (coords instanceof List) {
                    List<?> coordsList = (List<?>) coords;
                    // Empty coordinates
                    if (coordsList.isEmpty()) {
                        return true;
                    }
                    // Full world bbox
                    if (isFullWorldBbox(coordsList)) {
                        return true;
                    }
                    // Has valid coordinates
                    return false;
                } else {
                    // coordinates exists but is not a List
                    return true;
                }
            }

            // Not a Map, not a String, treat as empty
            return true;
        } catch (Exception e) {
            log.warn("Error checking if multipolygon is empty, treating as empty", e);
            return true;
        }
    }

    /**
     * Check if coordinates represent the full world (±180 longitude, ±90 latitude)
     */
    private static boolean isFullWorldBbox(List<?> coordinates) {
        try {
            // Check each polygon in the MultiPolygon
            for (Object polygonObj : coordinates) {
                if (!(polygonObj instanceof List)) continue;
                List<?> polygon = (List<?>) polygonObj;

                // Check each ring in the polygon
                for (Object ringObj : polygon) {
                    if (!(ringObj instanceof List)) continue;
                    List<?> ring = (List<?>) ringObj;

                    // Count how many world boundary points we find
                    boolean hasMaxLon = false;  // 180 or -180
                    boolean hasMaxLat = false;  // 90
                    boolean hasMinLat = false;  // -90

                    for (Object pointObj : ring) {
                        if (!(pointObj instanceof List)) continue;
                        List<?> point = (List<?>) pointObj;

                        if (point.size() >= 2) {
                            double lon = ((Number) point.get(0)).doubleValue();
                            double lat = ((Number) point.get(1)).doubleValue();

                            if (Math.abs(lon) == 180.0) hasMaxLon = true;
                            if (lat == 90.0) hasMaxLat = true;
                            if (lat == -90.0) hasMinLat = true;
                        }
                    }

                    // If we found all world boundaries, it's the full world
                    if (hasMaxLon && hasMaxLat && hasMinLat) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract coordinates from multipolygon object (handles both MultipolygonGeoJSON and Map)
     */
    private static List<List<List<List<BigDecimal>>>> extractCoordinates(Object multipolygon, ObjectMapper objectMapper) throws Exception {
        if (multipolygon instanceof MultipolygonGeoJSON) {
            return ((MultipolygonGeoJSON) multipolygon).getCoordinates();
        }

        if (multipolygon instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) multipolygon;
            Object coords = map.get("coordinates");

            if (coords != null) {
                String coordsJson = objectMapper.writeValueAsString(coords);
                return objectMapper.readValue(coordsJson,
                        objectMapper.getTypeFactory().constructParametricType(List.class,
                                objectMapper.getTypeFactory().constructParametricType(List.class,
                                        objectMapper.getTypeFactory().constructParametricType(List.class,
                                                objectMapper.getTypeFactory().constructParametricType(List.class, BigDecimal.class)))));
            }
        }

        return null;
    }

    /**
     * Build the subsetting section header
     */
    private static String buildSubsettingHeader() {
        return "<!--[if mso | IE]></td></tr></table></td></tr><tr><td width=\"1280px\"><table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"width:1264px;\" width=\"1264\"><tr><td style=\"line-height:0;font-size:0;mso-line-height-rule:exactly;\"><![endif]-->" +
                "<div class=\"r e y\" style=\"background:#ffffff;background-color:#ffffff;margin:0px auto;max-width:1264px;\">" +
                "<table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"background:#ffffff;background-color:#ffffff;width:100%;\">" +
                "<tbody><tr><td style=\"border:none;direction:ltr;font-size:0;padding:4px 24px 4px 22px;text-align:center;\">" +
                "<!--[if mso | IE]><table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"vertical-align:middle;width:592px;\"><![endif]-->" +
                "<div class=\"m h\" style=\"font-size:0;text-align:left;direction:ltr;display:inline-block;vertical-align:middle;width:100%;\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"border:none;vertical-align:middle;\" width=\"100%\">" +
                "<tbody><tr><td align=\"center\" class=\"tr-0\" style=\"background:transparent;font-size:0;padding:0;word-break:break-word;\">" +
                "<table cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" border=\"0\" style=\"color:#000000;line-height:normal;table-layout:fixed;width:100%;border:none;\">" +
                "<tr><td align=\"left\" class=\"u\" style=\"padding:0;height:auto;word-wrap:break-word;vertical-align:middle;\" width=\"auto\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\"><tr><td align=\"left\" width=\"100%\">" +
                "<div style=\"font-family: 'Open Sans', 'Arial', sans-serif; font-size: 16px; font-weight: 400; line-height: 150%; text-align: left; color: #090c02\">" +
                "<p style=\"Margin:0;mso-line-height-alt:24px;font-size:16px;line-height:150%;\">Subsetting for this collection:</p>" +
                "</div></td></tr></table></td></tr></table></td></tr></tbody></table></div>" +
                "<!--[if mso | IE]></td></tr></table><![endif]--></td></tr></tbody></table></div>" +
                "<!--[if mso | IE]></td></tr></table></td></tr><tr><td width=\"1280px\"><table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"width:1264px;\" width=\"1264\"><tr><td style=\"line-height:0;font-size:0;mso-line-height-rule:exactly;\"><![endif]-->" +
                "<div style=\"margin:0px auto;max-width:1264px;\"><table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"width:100%;\">" +
                "<tbody><tr><td style=\"direction:ltr;font-size:0;padding:0;text-align:center;\">" +
                "<!--[if mso | IE]><table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"vertical-align:top;width:1264px;\"><![endif]-->" +
                "<div class=\"o h\" style=\"font-size:0;text-align:left;direction:ltr;display:inline-block;vertical-align:top;width:100%;\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" width=\"100%\"><tbody><tr><td style=\"vertical-align:top;padding:0;\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" width=\"100%\"><tbody><tr>" +
                "<td style=\"font-size:0;padding:0;word-break:break-word;\" aria-hidden=\"true\"><div style=\"height:0;line-height:0;\">&#8202;</div></td>" +
                "</tr></tbody></table></td></tr></tbody></table></div>" +
                "<!--[if mso | IE]></td></tr></table><![endif]--></td></tr></tbody></table></div>";
    }

    /**
     * Build bbox wrapper with table structure
     */
    private static String buildBboxWrapper(String bboxContent) {
        return "<!--[if mso | IE]></td></tr></table></td></tr><tr><td width=\"1280px\"><table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"width:1264px;\" width=\"1264\"><tr><td style=\"line-height:0;font-size:0;mso-line-height-rule:exactly;\"><![endif]-->" +
                "<div class=\"r e y\" style=\"background:#ffffff;background-color:#ffffff;margin:0px auto;max-width:1264px;\">" +
                "<table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"background:#ffffff;background-color:#ffffff;width:100%;\">" +
                "<tbody><tr><td style=\"border:none;direction:ltr;font-size:0;padding:10px 20px 10px 20px;text-align:center;\">" +
                "<!--[if mso | IE]><table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"vertical-align:middle;width:600px;\"><![endif]-->" +
                "<div class=\"c h\" style=\"font-size:0;text-align:left;direction:ltr;display:inline-block;vertical-align:middle;width:100%;\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"border:none;vertical-align:middle;\" width=\"100%\">" +
                "<tbody>" + bboxContent + "</tbody></table></div>" +
                "<!--[if mso | IE]></td></tr></table><![endif]--></td></tr></tbody></table></div>";
    }

    /**
     * Build spacer section between bbox and time range
     */
    private static String buildSpacerSection() {
        return "<!--[if mso | IE]></td></tr></table></td></tr><tr><td width=\"1280px\"><table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"width:1264px;\" width=\"1264\"><tr><td style=\"line-height:0;font-size:0;mso-line-height-rule:exactly;\"><![endif]-->" +
                "<div style=\"margin:0px auto;max-width:1264px;\"><table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"width:100%;\">" +
                "<tbody><tr><td style=\"direction:ltr;font-size:0;padding:0;text-align:center;\">" +
                "<!--[if mso | IE]><table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"vertical-align:top;width:1264px;\"><![endif]-->" +
                "<div class=\"o h\" style=\"font-size:0;text-align:left;direction:ltr;display:inline-block;vertical-align:top;width:100%;\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" width=\"100%\"><tbody><tr><td style=\"vertical-align:top;padding:0;\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" width=\"100%\"><tbody><tr>" +
                "<td style=\"font-size:0;padding:0;word-break:break-word;\" aria-hidden=\"true\"><div style=\"height:0;line-height:0;\">&#8202;</div></td>" +
                "</tr></tbody></table></td></tr></tbody></table></div>" +
                "<!--[if mso | IE]></td></tr></table><![endif]--></td></tr></tbody></table></div>";
    }

    /**
     * Build time range wrapper with content
     */
    private static String buildTimeRangeWrapper(String startDate, String endDate) {
        return "<!--[if mso | IE]></td></tr></table></td></tr><tr><td width=\"1280px\"><table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"width:1264px;\" width=\"1264\"><tr><td style=\"line-height:0;font-size:0;mso-line-height-rule:exactly;\"><![endif]-->" +
                "<div class=\"r e y\" style=\"background:#ffffff;background-color:#ffffff;margin:0px auto;max-width:1264px;\">" +
                "<table align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"background:#ffffff;background-color:#ffffff;width:100%;\">" +
                "<tbody><tr><td style=\"border:none;direction:ltr;font-size:0;padding:10px 20px 10px 20px;text-align:center;\">" +
                "<!--[if mso | IE]><table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"vertical-align:middle;width:600px;\"><![endif]-->" +
                "<div class=\"c h\" style=\"font-size:0;text-align:left;direction:ltr;display:inline-block;vertical-align:middle;width:100%;\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"border:none;vertical-align:middle;\" width=\"100%\">" +
                "<tbody><tr><td align=\"center\" class=\"tr-0\" style=\"background:transparent;font-size:0;padding:0;word-break:break-word;\">" +
                "<table cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" border=\"0\" style=\"color:#000000;line-height:normal;table-layout:fixed;width:100%;border:none;\">" +
                "<tr><td align=\"left\" class=\"u\" style=\"padding:0;height:auto;word-wrap:break-word;vertical-align:middle;\" width=\"32\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\"><tr><td align=\"left\" width=\"100%\">" +
                "<img alt width=\"32\" style=\"display:block;width:32px;height:32px;\" src=\"{{TIME_RANGE_IMG}}\"></td></tr></table></td>" +
                "<td style=\"vertical-align:middle;color:transparent;font-size:0;\" width=\"16\">&#8203;</td>" +
                "<td align=\"left\" class=\"u\" style=\"padding:0;height:auto;word-wrap:break-word;vertical-align:middle;\" width=\"auto\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\"><tr><td align=\"left\" width=\"100%\">" +
                "<div style=\"font-family: 'Open Sans', 'Arial', sans-serif; font-size: 14px; font-weight: 500; line-height: 157%; text-align: left; color: #090c02\">" +
                "<p style=\"Margin:0;mso-line-height-alt:22px;font-size:14px;line-height:157%;\">Time Range</p></div></td></tr></table></td></tr>" +
                "</table></td></tr>" +
                "<tr><td style=\"font-size:0;padding:0;word-break:break-word;\"><div style=\"height:8px;line-height:8px;\">&#8202;</div></td></tr>" +
                "<tr><td align=\"center\" class=\"tr-0\" style=\"background:transparent;font-size:0;padding:0px 48px 0px 48px;word-break:break-word;\">" +
                "<table cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" border=\"0\" style=\"color:#000000;line-height:normal;table-layout:fixed;width:100%;border:none;\">" +
                "<tr><td align=\"left\" class=\"u\" style=\"padding:0;height:auto;word-wrap:break-word;vertical-align:middle;\" width=\"500\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\"><tr><td align=\"left\" width=\"100%\">" +
                "<div style=\"font-family: 'Open Sans', 'Arial', sans-serif; font-size: 14px; font-weight: 500; line-height: 157%; text-align: left; color: #090c02\">" +
                "<p style=\"Margin:0;mso-line-height-alt:22px;font-size:14px;line-height:157%;\">"+startDate+" - "+endDate+"</p></div></td></tr></table></td></tr>" +
                "</table></td></tr></tbody></table></div>" +
                "<!--[if mso | IE]></td></tr></table><![endif]--></td></tr></tbody></table></div>";
    }

    protected static String buildBboxSection(String north, String south, String west, String east, int index) {
        String title = index > 0 ? "Bounding Box " + index : "Bounding Box Selection";

        return "<tr>" +
                "<td align=\"center\" class=\"tr-0\" style=\"background:transparent;font-size:0;padding:0;word-break:break-word;\">" +
                "<table cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" border=\"0\" style=\"color:#000000;line-height:normal;table-layout:fixed;width:100%;border:none;\">" +
                "<tr><td align=\"left\" class=\"u\" style=\"padding:0;height:auto;word-wrap:break-word;vertical-align:middle;\" width=\"32\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\"><tr><td align=\"left\" width=\"100%\">" +
                "<img alt width=\"32\" style=\"display:block;width:32px;height:32px;\" src=\"{{BBOX_IMG}}\"></td></tr></table></td>" +
                "<td style=\"vertical-align:middle;color:transparent;font-size:0;\" width=\"16\">&#8203;</td>" +
                "<td align=\"left\" class=\"u\" style=\"padding:0;height:auto;word-wrap:break-word;vertical-align:middle;\" width=\"auto\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\"><tr><td align=\"left\" width=\"100%\">" +
                "<div style=\"font-family: 'Open Sans', 'Arial', sans-serif; font-size: 14px; font-weight: 500; line-height: 157%; text-align: left; color: #090c02\">" +
                "<p style=\"Margin:0;mso-line-height-alt:22px;font-size:14px;line-height:157%;\">" + title + "</p></div></td></tr></table></td></tr>" +
                "</table></td></tr>" +
                "<tr><td style=\"font-size:0;padding:0;word-break:break-word;\"><div style=\"height:8px;line-height:8px;\">&#8202;</div></td></tr>" +
                "<tr><td align=\"center\" class=\"tr-0\" style=\"background:transparent;font-size:0;padding:0px 48px 0px 48px;word-break:break-word;\">" +
                "<table cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" border=\"0\" style=\"color:#000000;line-height:normal;table-layout:fixed;width:100%;border:none;\">" +
                "<tr><td align=\"left\" class=\"u\" style=\"padding:0;height:auto;word-wrap:break-word;vertical-align:middle;\" width=\"500\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\"><tr><td align=\"left\" width=\"100%\">" +
                "<div style=\"font-family: 'Open Sans', 'Arial', sans-serif; font-size: 14px; font-weight: 400; line-height: 157%; text-align: left; color: #3c3c3c\">" +
                "<p style=\"Margin:0;mso-line-height-alt:22px;font-size:14px;line-height:157%;\">N: " + north + "</p></div></td></tr></table></td></tr>" +
                "<tr><td style=\"font-size:0;padding:0;padding-bottom:0;word-break:break-word;color:transparent;\" aria-hidden=\"true\">" +
                "<div style=\"height:8px;line-height:8px;\">&#8203;</div></td></tr>" +
                "<tr><td align=\"left\" class=\"u\" style=\"padding:0;height:auto;word-wrap:break-word;vertical-align:middle;\" width=\"500\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\"><tr><td align=\"left\" width=\"100%\">" +
                "<div style=\"font-family: 'Open Sans', 'Arial', sans-serif; font-size: 14px; font-weight: 400; line-height: 157%; text-align: left; color: #3c3c3c\">" +
                "<p style=\"Margin:0;mso-line-height-alt:22px;font-size:14px;line-height:157%;\">S: " + south + "</p></div></td></tr></table></td></tr>" +
                "<tr><td style=\"font-size:0;padding:0;padding-bottom:0;word-break:break-word;color:transparent;\" aria-hidden=\"true\">" +
                "<div style=\"height:8px;line-height:8px;\">&#8203;</div></td></tr>" +
                "<tr><td align=\"left\" class=\"u\" style=\"padding:0;height:auto;word-wrap:break-word;vertical-align:middle;\" width=\"500\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\"><tr><td align=\"left\" width=\"100%\">" +
                "<div style=\"font-family: 'Open Sans', 'Arial', sans-serif; font-size: 14px; font-weight: 400; line-height: 157%; text-align: left; color: #3c3c3c\">" +
                "<p style=\"Margin:0;mso-line-height-alt:22px;font-size:14px;line-height:157%;\">W: " + west + "</p></div></td></tr></table></td></tr>" +
                "<tr><td style=\"font-size:0;padding:0;padding-bottom:0;word-break:break-word;color:transparent;\" aria-hidden=\"true\">" +
                "<div style=\"height:8px;line-height:8px;\">&#8203;</div></td></tr>" +
                "<tr><td align=\"left\" class=\"u\" style=\"padding:0;height:auto;word-wrap:break-word;vertical-align:middle;\" width=\"500\">" +
                "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\"><tr><td align=\"left\" width=\"100%\">" +
                "<div style=\"font-family: 'Open Sans', 'Arial', sans-serif; font-size: 14px; font-weight: 400; line-height: 157%; text-align: left; color: #3c3c3c\">" +
                "<p style=\"Margin:0;mso-line-height-alt:22px;font-size:14px;line-height:157%;\">E: " + east + "</p></div></td></tr></table></td></tr>" +
                "</table></td></tr>" +
                "</tr>";
    }
}
