package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import au.org.aodn.ogcapi.server.core.service.AWSBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RestApiTest {

    @Mock
    private AWSBatchService awsBatchService;

    @InjectMocks
    private RestApi restApi;

    private Map<String, String> parameters;

    @BeforeEach
    public void setUp() {
        parameters = new HashMap<>();
        parameters.put(DatasetDownloadEnums.Condition.UUID.getValue(), "collectionId");
        parameters.put(DatasetDownloadEnums.Condition.START_DATE.getValue(), "2023-01-01");
        parameters.put(DatasetDownloadEnums.Condition.END_DATE.getValue(), "2023-12-31");
        parameters.put(DatasetDownloadEnums.Condition.MIN_LATITUDE.getValue(), "10.0");
        parameters.put(DatasetDownloadEnums.Condition.MIN_LONGITUDE.getValue(), "20.0");
        parameters.put(DatasetDownloadEnums.Condition.MAX_LATITUDE.getValue(), "30.0");
        parameters.put(DatasetDownloadEnums.Condition.MAX_LONGITUDE.getValue(), "40.0");
        parameters.put(DatasetDownloadEnums.Condition.RECIPIENT.getValue(), "test@example.com");
    }

    @Test
    public void testDownloadData_Success() {
        when(awsBatchService.submitJob(any(), any(), any(), any())).thenReturn("jobId");

        ResponseEntity<?> response = restApi.downloadData(
                "collectionId", "2023-01-01", "2023-12-31", "10.0", "20.0", "30.0", "40.0", "test@example.com");

        assertTrue( response.getStatusCode().is2xxSuccessful());
        assertEquals("Job submitted with ID: jobId", response.getBody());
        verify(awsBatchService, times(1)).submitJob(any(), any(), any(), any());
    }

    @Test
    public void testDownloadData_Error()  {
        when(awsBatchService.submitJob(any(), any(), any(), any())).thenThrow(new RuntimeException("Error"));

        ResponseEntity<?> response = restApi.downloadData(
                "collectionId", "2023-01-01", "2023-12-31", "10.0", "20.0", "30.0", "40.0", "test@example.com");

        assertTrue( response.getStatusCode().is4xxClientError());
        assertEquals("Error while getting dataset", response.getBody());
        verify(awsBatchService, times(1)).submitJob(any(), any(), any(), any());
    }
}
