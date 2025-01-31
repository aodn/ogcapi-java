package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.processes.model.Execute;
import au.org.aodn.ogcapi.processes.model.InlineResponse200;
import au.org.aodn.ogcapi.processes.model.Results;
import au.org.aodn.ogcapi.server.core.model.InlineValue;
import au.org.aodn.ogcapi.server.core.model.enumeration.ProcessIdEnum;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RestApiTest {

    @Mock
    private RestServices restServices;

    @InjectMocks
    private RestApi restApi;

    private Execute executeRequest;

    @BeforeEach
    public void setUp() {
        executeRequest = new Execute();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("collectionId", "test-collection-id");
        inputs.put("start_date", "2023-01-01");
        inputs.put("end_date", "2023-01-31");
        inputs.put("min_lat", "-10.0");
        inputs.put("min_lon", "110.0");
        inputs.put("max_lat", "10.0");
        inputs.put("max_lon", "150.0");
        inputs.put("recipient", "test@example.com");
        executeRequest.setInputs(inputs);
    }

    @Test
    public void testExecuteDownloadDatasetSuccess() {
        when(restServices.downloadData(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ResponseEntity.ok("Job submitted with ID: test-job-id"));

        ResponseEntity<InlineResponse200> response = restApi.execute(ProcessIdEnum.DOWNLOAD_DATASET.getValue(), executeRequest);

        assertEquals(200, response.getStatusCode().value());
        Results results = (Results) response.getBody();
        assert results != null;
        InlineValue message = (InlineValue) results.get("message");
        assertEquals("Job submitted with ID: test-job-id", message.message());
    }

    @Test
    public void testExecuteDownloadDatasetError() {
        when(restServices.downloadData(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Error while getting dataset"));

        ResponseEntity<InlineResponse200> response = restApi.execute(ProcessIdEnum.DOWNLOAD_DATASET.getValue(), executeRequest);

        assertEquals(400, response.getStatusCode().value());
        Results results = (Results) response.getBody();
        assert results != null;
        InlineValue error = (InlineValue) results.get("error");
        assertEquals("Error while getting dataset", error.message());
    }

    @Test
    public void testExecuteUnknownProcessId() {
        ResponseEntity<InlineResponse200> response = restApi.execute("unknown-process-id", executeRequest);

        assertEquals(400, response.getStatusCode().value());
        Results results = (Results) response.getBody();
        assert results != null;
        InlineValue error = (InlineValue) results.get("error");
        assertEquals("Unknown process ID: unknown-process-id", error.message());
    }
}
