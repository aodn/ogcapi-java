package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.processes.model.Execute;
import au.org.aodn.ogcapi.processes.model.InlineResponse200;
import au.org.aodn.ogcapi.processes.model.Results;
import au.org.aodn.ogcapi.server.core.exception.DownloadLimitExceededException;
import au.org.aodn.ogcapi.server.core.model.DownloadExecutionResponse;
import au.org.aodn.ogcapi.server.core.model.InlineValue;
import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import au.org.aodn.ogcapi.server.core.model.enumeration.InlineResponseKeyEnum;
import au.org.aodn.ogcapi.server.core.model.enumeration.ProcessIdEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RestApiTest {

    @Mock
    private RestServices restServices;

    @Mock
    private DownloadAdmissionService downloadAdmissionService;

    @InjectMocks
    private RestApi restApi;

    private Execute executeRequest;

    @BeforeEach
    public void setUp() {
        executeRequest = new Execute();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DatasetDownloadEnums.Parameter.UUID.getValue(), "test-uuid");
        inputs.put(DatasetDownloadEnums.Parameter.START_DATE.getValue(), "2023-01-01");
        inputs.put(DatasetDownloadEnums.Parameter.END_DATE.getValue(), "2023-01-31");
        inputs.put(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue(), "test-multipolygon");
        inputs.put(DatasetDownloadEnums.Parameter.RECIPIENT.getValue(), "test@example.com");
        inputs.put(DatasetDownloadEnums.Parameter.TYPE.getValue(), DatasetDownloadEnums.Type.SUB_SETTING.getValue());
        executeRequest.setInputs(inputs);
    }

    @Test
    public void testExecuteDownloadDatasetSuccess() throws JsonProcessingException {
        when(downloadAdmissionService.submit(any()))
                .thenReturn("test-job-id");

        ResponseEntity<InlineResponse200> response = restApi.execute(ProcessIdEnum.DOWNLOAD_DATASET.getValue(), executeRequest);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(DownloadExecutionResponse.class, response.getBody());
        DownloadExecutionResponse results = (DownloadExecutionResponse) response.getBody();
        assert results != null;
        assertEquals("Job submitted with ID: test-job-id", results.message().message());
        assertEquals("200", results.status().message());
        assertEquals("test-job-id", results.jobId());
    }

    @Test
    public void testExecutePassesEveryRequestInputToAdmission() throws JsonProcessingException {
        when(downloadAdmissionService.submit(any()))
                .thenReturn("test-job-id");

        restApi.execute(ProcessIdEnum.DOWNLOAD_DATASET.getValue(), executeRequest);

        ArgumentCaptor<DownloadRequest> captor = ArgumentCaptor.forClass(DownloadRequest.class);
        verify(downloadAdmissionService).submit(captor.capture());
        DownloadRequest request = captor.getValue();
        assertEquals("test-uuid", request.uuid());
        assertEquals("2023-01-01", request.startDate());
        assertEquals("2023-01-31", request.endDate());
        assertEquals("test-multipolygon", request.multiPolygon());
        assertEquals("test@example.com", request.recipient());
    }

    @Test
    public void testExecuteDownloadDatasetError() throws JsonProcessingException {
        when(downloadAdmissionService.submit(any()))
                .thenThrow(new RuntimeException("Error while getting dataset"));

        ResponseEntity<InlineResponse200> response = restApi.execute(ProcessIdEnum.DOWNLOAD_DATASET.getValue(), executeRequest);

        assertInstanceOf(Results.class, response.getBody());
        Results results = (Results) response.getBody();
        assert results != null;
        InlineValue error = (InlineValue) results.get(InlineResponseKeyEnum.MESSAGE.getValue());
        assertEquals("Error while getting dataset", error.message());

        // No job was submitted, so the user must not be told their data is being processed.
        // The admission service owns that email now, so nothing here may send one either.
        verify(restServices, never()).notifyUser(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testExecuteAtTheDownloadLimitLetsTheExceptionPropagate() throws JsonProcessingException {
        DownloadLimitExceededException limitExceeded = new DownloadLimitExceededException(10);
        when(downloadAdmissionService.submit(any())).thenThrow(limitExceeded);

        // Unlike a generic failure, this must reach GlobalExceptionHandler as a real 429 -
        // not be swallowed into the 200-wrapped "Error while getting dataset" response.
        DownloadLimitExceededException thrown = assertThrows(DownloadLimitExceededException.class,
                () -> restApi.execute(ProcessIdEnum.DOWNLOAD_DATASET.getValue(), executeRequest));
        assertEquals(limitExceeded.getMessage(), thrown.getMessage());
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
