package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.processes.model.Execute;
import au.org.aodn.ogcapi.processes.model.InlineResponse200;
import au.org.aodn.ogcapi.processes.model.Results;
import au.org.aodn.ogcapi.server.core.model.InlineValue;
import au.org.aodn.ogcapi.server.core.model.enumeration.ProcessIdEnum;
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

    private Execute executeRequest;

    @BeforeEach
    public void setUp() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("collectionId", "collectionId");
        inputs.put("start_date", "2023-01-01");
        inputs.put("end_date", "2023-12-31");
        inputs.put("min_lat", "10.0");
        inputs.put("min_lon", "20.0");
        inputs.put("max_lat", "30.0");
        inputs.put("max_lon", "40.0");
        inputs.put("recipient", "test@example.com");

        executeRequest = new Execute();
        executeRequest.setInputs(inputs);
    }

    @Test
    public void testExecute() {
        when(awsBatchService.submitJob(any(), any(), any(), any())).thenReturn("jobId");

        ResponseEntity<InlineResponse200> response = restApi.execute(ProcessIdEnum.DOWNLOAD_DATASET.getValue(), executeRequest);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        Results results = (Results) response.getBody();
        assert results != null;
        InlineValue message = (InlineValue) results.get("message");
        assertEquals("Job submitted with ID: jobId", message.message());
        verify(awsBatchService, times(1)).submitJob(any(), any(), any(), any());
    }

    @Test
    public void testExecute_UnknownProcessId() {
        ResponseEntity<InlineResponse200> response = restApi.execute("unknownProcessId", executeRequest);

        assertTrue(response.getStatusCode().is4xxClientError());
        Results results = (Results) response.getBody();
        assert results != null;
        InlineValue error = (InlineValue) results.get("error");
        assertEquals("Unknown process ID: unknownProcessId", error.message());
    }
}
