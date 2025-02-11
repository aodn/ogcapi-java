package au.org.aodn.ogcapi.server.processes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.SubmitJobRequest;
import software.amazon.awssdk.services.batch.model.SubmitJobResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    public void testDownloadDataSuccess() throws JsonProcessingException {
        // Arrange
        String jobId = "12345";
        SubmitJobResponse submitJobResponse = SubmitJobResponse.builder().jobId(jobId).build();
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(submitJobResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn("test-multipolygon");

        // Act
        ResponseEntity<String> response = restServices.downloadData(
                "test-uuid", "2023-01-01", "2023-01-31", "test-multipolygon", "test@example.com");

        // Assert
        assertEquals(ResponseEntity.ok("Job submitted with ID: " + jobId), response);
        verify(batchClient, times(1)).submitJob(any(SubmitJobRequest.class));
    }

    @Test
    public void testDownloadDataJsonProcessingException() throws JsonProcessingException {
        // Arrange
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error") {});

        // Act & Assert
        try {
            restServices.downloadData("test-uuid", "2023-01-01", "2023-01-31", "test-multipolygon", "test@example.com");
        } catch (JsonProcessingException e) {
            assertEquals("Error", e.getMessage());
        }
    }
}
