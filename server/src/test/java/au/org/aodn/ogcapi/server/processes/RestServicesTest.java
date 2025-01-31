package au.org.aodn.ogcapi.server.processes;

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

    @InjectMocks
    private RestServices restServices;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testDownloadDataSuccess() {
        // Arrange
        String jobId = "12345";
        SubmitJobResponse submitJobResponse = SubmitJobResponse.builder().jobId(jobId).build();
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(submitJobResponse);

        // Act
        ResponseEntity<String> response = restServices.downloadData(
                "id", "2021-01-01", "2021-01-31", "10.0", "20.0", "30.0", "40.0", "recipient@example.com");

        // Assert
        assertEquals(ResponseEntity.ok("Job submitted with ID: " + jobId), response);
        verify(batchClient, times(1)).submitJob(any(SubmitJobRequest.class));
    }

    @Test
    public void testDownloadDataFailure() {
        // Arrange
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenThrow(new RuntimeException("AWS Batch error"));

        // Act
        ResponseEntity<String> response = restServices.downloadData(
                "id", "2021-01-01", "2021-01-31", "10.0", "20.0", "30.0", "40.0", "recipient@example.com");

        // Assert
        assertEquals(ResponseEntity.badRequest().body("Error while getting dataset"), response);
        verify(batchClient, times(1)).submitJob(any(SubmitJobRequest.class));
    }
}
