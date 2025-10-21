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
     * Generate HTML content for bounding box section in email
     * @param multipolygon - the multipolygon object
     * @param objectMapper - Jackson ObjectMapper for JSON processing
     * @return HTML string for bbox section
     */
    public static String generateBboxHtml(Object multipolygon, ObjectMapper objectMapper) {
        try {
            if (multipolygon == null) {
                return buildBboxSection("0", "0", "0", "0", 0);
            }

            // Extract coordinates directly from the object
            List<List<List<List<BigDecimal>>>> coordinates = extractCoordinates(multipolygon, objectMapper);

            if (coordinates == null || coordinates.isEmpty()) {
                return buildBboxSection("0", "0", "0", "0", 0);
            }

            StringBuilder html = new StringBuilder();
            int bboxCounter = 0;

            // Process each polygon separately
            for (List<List<List<BigDecimal>>> polygon : coordinates) {
                // Find min/max for THIS polygon only
                double minLon = Double.MAX_VALUE;
                double maxLon = Double.MIN_VALUE;
                double minLat = Double.MAX_VALUE;
                double maxLat = Double.MIN_VALUE;

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

                    String north = String.format("%.5f", envelope.getMaxY());
                    String south = String.format("%.5f", envelope.getMinY());
                    String west = String.format("%.5f", envelope.getMinX());
                    String east = String.format("%.5f", envelope.getMaxX());

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
            return buildBboxSection("0", "0", "0", "0", 0);
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
