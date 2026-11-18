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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    public void submitDownloadJobSetsTheShareIdentifierOnTheRequest() {
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
    public void submitJobReplacesEmptySuggestedCitationWithNotAvailable() throws JsonProcessingException {
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
        assertEquals("Suggested Citation not available", captured.parameters().get(suggestedKey));
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

    // The output format the estimate sends to DAS for a download in the given format
    @SuppressWarnings("unchecked")
    private String estimateFormatFor(String outputFormat) throws JsonProcessingException {
        DasService dasService = mock(DasService.class);
        when(dasService.estimateCloudOptimisedDownloadSize(any(), anyMap(), any()))
                .thenReturn("{\"estimated_output_bytes\":1024}");
        RestServices services = new RestServices(batchClient, new ObjectMapper(), "test-job-definition", "test-job-queue");
        ReflectionTestUtils.setField(services, "dasService", dasService);

        services.estimateDownloadBytes(new DownloadRequest("test-uuid", "test.parquet", "2023-01-01", "2023-01-31",
                "non-specified", "test@example.com", "Test Ocean Data Collection",
                "https://portal.example.test/details/test-uuid", "Cite as", null, outputFormat));

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(dasService).estimateCloudOptimisedDownloadSize(eq("test-uuid"), captor.capture(), any());
        return captor.getValue().get(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue());
    }

    @Test
    public void estimateDownloadBytesUsesTheCsvEstimateForExcel() throws JsonProcessingException {
        // DAS gives no size for Excel, so ask for CSV, as the portal does
        assertEquals("csv", estimateFormatFor("excel"));
    }

    @Test
    public void estimateDownloadBytesKeepsOtherFormats() throws JsonProcessingException {
        assertEquals("csv", estimateFormatFor("csv"));
        assertEquals("netcdf", estimateFormatFor("netcdf"));
    }

    @Test
    public void downloadParametersKeepExcel() throws JsonProcessingException {
        // Only the estimate uses CSV, the download itself still asks for Excel
        RestServices services = new RestServices(batchClient, new ObjectMapper(), "definition", "queue");
        Map<String, String> parameters = services.buildDownloadParameters(new DownloadRequest("test-uuid",
                "test.parquet", "2023-01-01", "2023-01-31", "non-specified", "test@example.com", "Title",
                "https://example.com/metadata", "Cite as", null, "excel"));

        assertEquals("excel", parameters.get(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue()));
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
