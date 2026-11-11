package au.org.aodn.ogcapi.server.processes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import au.org.aodn.ogcapi.server.core.service.das.DasService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.SubmitJobRequest;
import software.amazon.awssdk.services.batch.model.SubmitJobResponse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

public class RestServicesTest {

    @Mock
    private BatchClient batchClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RestServices restServices;

    private AutoCloseable closeableMock;

    @BeforeEach
    public void setUp() {
        closeableMock = MockitoAnnotations.openMocks(this);
    }

    @AfterEach void cleanUp() throws Exception {
        closeableMock.close();
    }

    /**
     * The two-step call the admission service makes: render the request at accept time, then
     * submit it. Kept as one helper so these tests still read as one download request.
     */
    private String downloadData(String uuid, String key, String startDate, String endDate, Object polygons,
                                String recipient, String collectionTitle, String fullMetadataLink,
                                String suggestedCitation, String outputFormat) throws JsonProcessingException {
        DownloadRequest request = new DownloadRequest(uuid, key, startDate, endDate, polygons, recipient,
                collectionTitle, fullMetadataLink, suggestedCitation, null, outputFormat);
        return restServices.submitDownloadJob(
                RestServices.downloadJobName(recipient), restServices.buildDownloadParameters(request), null);
    }

    @Test
    public void testDownloadDataSuccess() throws JsonProcessingException {
        // Arrange
        String jobId = "12345";
        SubmitJobResponse submitJobResponse = SubmitJobResponse.builder().jobId(jobId).build();
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(submitJobResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("test-multipolygon");

        // Act
        String response = downloadData(
                "test-uuid", "test-dname", "2023-01-01", "2023-01-31", "test-multipolygon", "test@example.com", "Test Ocean Data Collection", "https://metadata.imas.utas.edu.au/.../test-uuid-123", "Cite data as: Mazor, T., Watermeyer, K., Hobley, T., Grinter, V., Holden, R., MacDonald, K. and Ferns, L. (2023).", "geotiff");

        // Assert
        assertEquals(jobId, response);
        verify(batchClient, times(1)).submitJob(any(SubmitJobRequest.class));
    }

    @Test
    public void testDownloadDataJsonProcessingException() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error") {});

        // Act & Assert
        try {
            downloadData("test-uuid", "test-dname", "2023-01-01", "2023-01-31", "test-multipolygon", "test@example.com","Test Ocean Data Collection", "https://metadata.imas.utas.edu.au/.../test-uuid-123", "Cite data as: Mazor, T., Watermeyer, K., Hobley, T., Grinter, V., Holden, R., MacDonald, K. and Ferns, L. (2023).", "geotiff");
        } catch (JsonProcessingException e) {
            assertEquals("Error", e.getMessage());
        }
    }

    @Test
    public void testDownloadDataCapturesSubmitJobRequest() throws JsonProcessingException {
        // Arrange
        String jobId = "12345";
        SubmitJobResponse submitJobResponse = SubmitJobResponse.builder().jobId(jobId).build();
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(submitJobResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("test-multipolygon");

        // Act
        String response = downloadData(
                "test-uuid", "test-dname","2023-01-01", "2023-01-31", "non-specified", "test@example.com", "Test Ocean Data Collection", "https://metadata.imas.utas.edu.au/.../test-uuid-123", "Cite data as: Mazor, T., Watermeyer, K., Hobley, T., Grinter, V., Holden, R., MacDonald, K. and Ferns, L. (2023).", "geotiff");

        // Capture the submitted request
        ArgumentCaptor<SubmitJobRequest> captor =
                ArgumentCaptor.forClass(SubmitJobRequest.class);
        verify(batchClient, times(1)).submitJob(captor.capture());
        SubmitJobRequest captured = captor.getValue();

        // Assert relevant parameters
        assertEquals("non-specified", captured.parameters().get("multi_polygon"));
        assertEquals("test-dname", captured.parameters().get("key"));
        assertEquals("Test Ocean Data Collection", captured.parameters().get("collection_title"));
        assertEquals("geotiff", captured.parameters().get("output_format"));
        assertEquals("https://metadata.imas.utas.edu.au/.../test-uuid-123",
                captured.parameters().get("full_metadata_link"));
        // The helper passes null as the share, so no top-level shareIdentifier goes out.
        assertNull(captured.shareIdentifier());
        assertEquals(jobId, response);
    }

    @Test
    public void submitDownloadJobSetsTheShareIdentifierOnTheRequest() throws JsonProcessingException {
        // Arrange
        String jobId = "12345";
        SubmitJobResponse submitJobResponse = SubmitJobResponse.builder().jobId(jobId).build();
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(submitJobResponse);

        // Act: submit with a non-null share, as the admission service does on a fair-share queue.
        restServices.submitDownloadJob("test-job", new HashMap<>(), "small");

        // Capture the submitted request
        ArgumentCaptor<SubmitJobRequest> captor = ArgumentCaptor.forClass(SubmitJobRequest.class);
        verify(batchClient, times(1)).submitJob(captor.capture());

        assertEquals("small", captor.getValue().shareIdentifier());
    }

    @Test
    public void submitJobReplacesEmptySuggestedCitationWithUnavailable() throws JsonProcessingException {
        // Arrange
        String jobId = "67890";
        SubmitJobResponse submitJobResponse = SubmitJobResponse.builder().jobId(jobId).build();
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(submitJobResponse);
        // polygons set to 'non-specified' to avoid objectMapper serialization

        // Act: pass empty suggestedCitation
        String response = downloadData(
                "test-uuid", "test-dname","2023-01-01", "2023-01-31", "non-specified", "test@example.com",
                "Test Ocean Data Collection", "https://metadata.imas.utas.edu.au/.../test-uuid-123", "", "geotiff");

        // Capture the submitted request
        ArgumentCaptor<SubmitJobRequest> captor = ArgumentCaptor.forClass(SubmitJobRequest.class);
        verify(batchClient, times(1)).submitJob(captor.capture());
        SubmitJobRequest captured = captor.getValue();

        String suggestedKey = DatasetDownloadEnums.Parameter.SUGGESTED_CITATION.getValue();
        assertEquals("unavailable", captured.parameters().get(suggestedKey));
        assertEquals(jobId, response);
    }

    @Test
    public void submitJobWithoutJobIdThrows() {
        // AWS Batch answered but gave us no job id, so the job was never really queued.
        // This must fail loudly: the caller sends the "processing started" email off the
        // back of a successful submit.
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(SubmitJobResponse.builder().build());

        assertThrows(IllegalStateException.class, () -> downloadData(
                "test-uuid", "test-dname", "2023-01-01", "2023-01-31", "non-specified", "test@example.com",
                "Test Ocean Data Collection", "https://metadata.imas.utas.edu.au/.../test-uuid-123", "", "geotiff"));
    }

    /**
     * RestServices with a real ObjectMapper, so the estimate JSON is really parsed.
     */
    private RestServices withEstimate(String estimateJson) {
        DasService dasService = mock(DasService.class);
        when(dasService.estimateCloudOptimisedDownloadSize(any(), anyMap(), any())).thenReturn(estimateJson);
        RestServices services = new RestServices(batchClient, new ObjectMapper(), "test-job-definition", "test-job-queue");
        ReflectionTestUtils.setField(services, "dasService", dasService);
        return services;
    }

    private static DownloadRequest estimateRequest() {
        return new DownloadRequest("test-uuid", "test.zarr", "2023-01-01", "2023-01-31", "non-specified",
                "test@example.com", "Test Ocean Data Collection", "https://portal.example.test/details/test-uuid",
                "Cite as", null, "netcdf");
    }

    @Test
    public void estimateDownloadBytesReadsTheOutputSize() throws JsonProcessingException {
        RestServices services = withEstimate(
                "{\"estimated_output_bytes\":193273528320,\"estimated_uncompressed_bytes\":400000000000}");

        assertEquals(193273528320L, services.estimateDownloadBytes(estimateRequest()));
    }

    @Test
    public void estimateDownloadBytesWithoutTheOutputSizeThrows() {
        RestServices services = withEstimate("{\"estimated_uncompressed_bytes\":400000000000}");

        assertThrows(IllegalStateException.class, () -> services.estimateDownloadBytes(estimateRequest()));
    }

    @Test
    public void startedEmailUsesSharedDatasetLayout() {
        RestServices services = new RestServices(batchClient, new ObjectMapper(), "definition", "queue");
        String html = services.generateStartedEmailContent(
                "test-uuid", "slocum_glider_delayed_qc.parquet,ocean_temperature.zarr",
                "non-specified", "non-specified", null, "Test Collection Title",
                "https://example.com/metadata", "Citation", null, "csv");

        for (String text : List.of("Dataset:", "Data Selection:", "Format:", "Suggested Citation:",
                "Usage Constraints:", "CSV", "slocum_glider_delayed_qc<br>ocean_temperature")) {
            assertTrue(html.contains(text), text);
        }
        assertTrue(html.contains("href=\"https://example.com/metadata\" style=\"color:#2571e9;text-decoration:underline;\">Test Collection Title</a>"));
        for (String text : List.of("slocum_glider_delayed_qc.parquet", "ocean_temperature.zarr",
                ">Metadata</p>", "UUID:", "Collection</p>", "{{")) {
            assertFalse(html.contains(text), text);
        }
    }

    @Test
    public void startedEmailEscapesTitleLinkAndKeysAndHandlesMissingValues() {
        RestServices services = new RestServices(batchClient, new ObjectMapper(), "definition", "queue");
        String html = services.generateStartedEmailContent(
                "uuid", "a&<b>.parquet", null, null, null, "A & B <x>",
                "https://example.com/?a=1&b=\"x\"", "", null, "csv");
        assertTrue(html.contains("A &amp; B &lt;x&gt;"));
        assertTrue(html.contains("a&amp;&lt;b&gt;"));
        assertTrue(html.contains("a=1&amp;b=&quot;x&quot;"));

        String fallback = services.generateStartedEmailContent(
                "uuid", " ", null, null, null, "Title", " ", "", null, "csv");
        assertTrue(fallback.contains(">Title</p>"));
        assertTrue(fallback.contains(">*</p>"));
    }

    /**
     * Regression guard for aodn/backlog#9241: the download confirmation email template used to
     * carry a hardcoded paragraph with citation requirements for the Ningaloo (TAN100) and Ocean
     * Reference Station 65m (ORS065) moorings, so it went out with every download regardless of
     * dataset. That paragraph has been removed.
     */
    @Test
    public void generatedEmailHasNoHardcodedMooringCitation() {
        RestServices services = new RestServices(batchClient, new ObjectMapper(), "test-job-definition", "test-job-queue");

        String html = services.generateStartedEmailContent(
                "test-uuid", "test-dname", "non-specified", "non-specified", "non-specified",
                "Test Ocean Data Collection", "https://portal.example.test/details/test-uuid",
                "Cite this dataset as X.", null, "netcdf");

        // The mooring-specific text must no longer appear in any email.
        for (String marker : List.of("TAN100", "ORS065", "Ningaloo", "Ocean Reference Station",
                "Sydney Water Corporation", "If using data from")) {
            assertFalse(html.contains(marker), "email should not contain hardcoded mooring text: " + marker);
        }

        // The suggested citation placeholder is still populated and no template markers leak.
        assertTrue(html.contains("Cite this dataset as X."), "suggested citation must still render");
        assertFalse(html.contains("{{suggestedCitation}}"), "suggestedCitation placeholder must be substituted");
    }

    private String startedEmail(List<String> usageConstraints) {
        RestServices services = new RestServices(batchClient, new ObjectMapper(), "definition", "queue");
        return services.generateStartedEmailContent("uuid", "a.parquet", null, null, null, "Title",
                null, "Citation", usageConstraints, "csv");
    }

    @Test
    public void startedEmailNeverShowsTheFixedImosText() {
        // Arrays.asList, since List.of does not allow null
        for (List<String> usageConstraints : Arrays.asList(List.of("Credit CSIRO"), List.<String>of(), null)) {
            String html = startedEmail(usageConstraints);

            assertFalse(html.contains("Any users of IMOS data"));
            assertFalse(html.contains("Data, products and services from IMOS"));
        }
    }

    @Test
    public void startedEmailShowsOneEscapedParagraphPerUsageConstraint() {
        String html = startedEmail(List.of("Credit CSIRO & partners", "Use <b>with</b> care"));

        int first = html.indexOf(">Credit CSIRO &amp; partners</p>");
        int second = html.indexOf(">Use &lt;b&gt;with&lt;/b&gt; care</p>");
        assertTrue(first >= 0 && second > first, "one paragraph per item, in order");
        assertFalse(html.contains(RestServices.USAGE_CONSTRAINTS_NOT_AVAILABLE));
        assertFalse(html.contains("{{"));
    }

    @Test
    public void startedEmailWithEmptyUsageConstraintsSaysNotAvailable() {
        String html = startedEmail(List.of());

        assertTrue(html.contains(">Usage Constraints not available</p>"));
    }

    @Test
    public void startedEmailWithMissingUsageConstraintsSaysNotAvailable() {
        String html = startedEmail(null);

        assertTrue(html.contains(">Usage Constraints not available</p>"));
    }

    private Map<String, String> downloadParameters(List<String> usageConstraints) throws JsonProcessingException {
        RestServices services = new RestServices(batchClient, new ObjectMapper(), "definition", "queue");
        return services.buildDownloadParameters(new DownloadRequest("test-uuid", "test.zarr", "2023-01-01",
                "2023-01-31", "non-specified", "test@example.com", "Title", "https://example.com/metadata",
                "Cite as", usageConstraints, "csv"));
    }

    @Test
    public void downloadParametersSendUsageConstraintsAsJson() throws JsonProcessingException {
        Map<String, String> parameters = downloadParameters(List.of("Credit CSIRO", "Use \"with\" care"));

        assertEquals("[\"Credit CSIRO\",\"Use \\\"with\\\" care\"]",
                parameters.get(DatasetDownloadEnums.Parameter.USAGE_CONSTRAINTS.getValue()));
    }

    @Test
    public void downloadParametersSendEmptyUsageConstraintsAsEmptyJsonList() throws JsonProcessingException {
        // "[]", never "": AWS Batch rejects an empty value
        Map<String, String> parameters = downloadParameters(List.of());

        assertEquals("[]", parameters.get(DatasetDownloadEnums.Parameter.USAGE_CONSTRAINTS.getValue()));
    }

    @Test
    public void downloadParametersSendMissingUsageConstraintsAsEmptyJsonList() throws JsonProcessingException {
        Map<String, String> parameters = downloadParameters(null);

        assertEquals("[]", parameters.get(DatasetDownloadEnums.Parameter.USAGE_CONSTRAINTS.getValue()));
    }
}
