package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.server.core.service.AWSBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class AWSBatchServiceTest {

    @Mock
    private BatchClient batchClient;

    @InjectMocks
    private AWSBatchService awsBatchService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testSubmitJob() {
        // Arrange
        String jobId = "test-job-id";
        SubmitJobResponse submitJobResponse = SubmitJobResponse.builder().jobId(jobId).build();
        when(batchClient.submitJob(any(SubmitJobRequest.class))).thenReturn(submitJobResponse);

        // Act
        String result = awsBatchService.submitJob("test-job", "test-queue", "test-job-def", Collections.singletonMap("param1", "value1"));

        // Assert
        assertEquals(jobId, result);
    }
}