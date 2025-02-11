package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.processes.model.Execute;
import au.org.aodn.ogcapi.processes.model.InlineResponse200;
import au.org.aodn.ogcapi.processes.model.Results;
import au.org.aodn.ogcapi.server.core.model.InlineValue;
import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import au.org.aodn.ogcapi.server.core.model.enumeration.InlineResponseKeyEnum;
import au.org.aodn.ogcapi.server.core.model.enumeration.ProcessIdEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
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
        inputs.put(DatasetDownloadEnums.Condition.UUID.getValue(), "test-uuid");
        inputs.put(DatasetDownloadEnums.Condition.START_DATE.getValue(), "2023-01-01");
        inputs.put(DatasetDownloadEnums.Condition.END_DATE.getValue(), "2023-01-31");
        inputs.put(DatasetDownloadEnums.Condition.MULTI_POLYGON.getValue(), "test-multipolygon");
        inputs.put(DatasetDownloadEnums.Condition.RECIPIENT.getValue(), "test@example.com");
        executeRequest.setInputs(inputs);
    }

    @Test
    public void testExecuteDownloadDatasetSuccess() throws JsonProcessingException {
        when(restServices.downloadData(any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.ok("Job submitted with ID: test-job-id"));

        ResponseEntity<InlineResponse200> response = restApi.execute(ProcessIdEnum.DOWNLOAD_DATASET.getValue(), executeRequest);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(Results.class, response.getBody());
        Results results = (Results) response.getBody();
        assert results != null;
        InlineValue message = (InlineValue) results.get("message");
        assertEquals("Job submitted with ID: test-job-id", message.message());
    }

    @Test
    public void testExecuteDownloadDatasetError() throws JsonProcessingException {
        when(restServices.downloadData(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Error while getting dataset"));

        ResponseEntity<InlineResponse200> response = restApi.execute(ProcessIdEnum.DOWNLOAD_DATASET.getValue(), executeRequest);

        assertInstanceOf(Results.class, response.getBody());
        Results results = (Results) response.getBody();
        assert results != null;
        InlineValue error = (InlineValue) results.get(InlineResponseKeyEnum.MESSAGE.getValue());
        assertEquals("Error while getting dataset", error.message());
    }

    @Test
    public void testExecuteUnknownProcessId() {
        ResponseEntity<InlineResponse200> response = restApi.execute("unknown-process-id", executeRequest);

        assertInstanceOf(Results.class, response.getBody());
        Results results = (Results) response.getBody();
        assert results != null;
        InlineValue error = (InlineValue) results.get(InlineResponseKeyEnum.MESSAGE.getValue());
        assertEquals("Unknown process ID: unknown-process-id", error.message());
    }
}
