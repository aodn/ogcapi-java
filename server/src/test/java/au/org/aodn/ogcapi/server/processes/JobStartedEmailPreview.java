package au.org.aodn.ogcapi.server.processes;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Renders the job-started email template to an HTML file for browser preview.
 * No AWS needed - it calls the template rendering directly.
 *
 * Run from the repo root:
 *   ./mvnw-ca -q -pl server -am test-compile exec:java \
 *     -Dexec.classpathScope=test \
 *     -Dexec.mainClass=au.org.aodn.ogcapi.server.processes.JobStartedEmailPreview
 */
public class JobStartedEmailPreview {

    // One ring of each kind, so the preview shows every layout at once:
    // two rectangles -> Bounding Box Selection, a triangle and a pentagon -> Polygon Selection.
    static final String SAMPLE_MULTI_POLYGON = "{\"type\":\"MultiPolygon\",\"coordinates\":["
            + "[[[145.0,-40.0],[145.0,-41.0],[146.0,-41.0],[146.0,-40.0],[145.0,-40.0]]],"
            + "[[[150.0,-35.0],[150.0,-33.0],[152.0,-33.0],[152.0,-35.0],[150.0,-35.0]]],"
            + "[[[145.0,-40.0],[146.0,-41.0],[144.5,-41.5],[145.0,-40.0]]],"
            + "[[[145.0,-40.0],[146.0,-40.0],[146.5,-41.0],[145.5,-42.0],[144.5,-41.0],[145.0,-40.0]]]"
            + "]}";

    public static void main(String[] args) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RestServices services = new RestServices(null, objectMapper, null, null);

        Object multiPolygon = objectMapper.readValue(SAMPLE_MULTI_POLYGON, Map.class);
        String html = services.generateStartedEmailContent(
                "test-uuid-1234",
                "slocum_glider_delayed_qc",
                "2020-01-01",
                "2020-06-30",
                multiPolygon,
                "Test Collection Title",
                "https://example.com/metadata",
                "Suggested citation goes here.",
                "csv"
        );

        Path out = Path.of("preview-job-started-email.html");
        Files.writeString(out, html);
        System.out.println("Wrote " + out.toAbsolutePath());
    }
}
