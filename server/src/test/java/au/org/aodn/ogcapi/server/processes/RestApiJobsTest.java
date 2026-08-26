package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.processes.model.StatusCode;
import au.org.aodn.ogcapi.processes.model.StatusInfo;
import au.org.aodn.ogcapi.server.core.exception.DownloadJobNotFoundException;
import au.org.aodn.ogcapi.server.core.exception.DownloadJobStatusException;
import au.org.aodn.ogcapi.server.core.exception.GlobalExceptionHandler;
import au.org.aodn.ogcapi.server.core.model.DownloadJobStatusInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RestApiJobsTest {

    private static final String JOB_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Mock
    private RestServices restServices;

    @Mock
    private DownloadJobStatusService downloadJobStatusService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RestApi restApi = new RestApi();
        ReflectionTestUtils.setField(restApi, "restServices", restServices);
        ReflectionTestUtils.setField(restApi, "downloadJobStatusService", downloadJobStatusService);
        mockMvc = MockMvcBuilders.standaloneSetup(restApi)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void postKeepsExistingFieldsAndAddsPureJobId() throws Exception {
        when(restServices.downloadData(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(JOB_ID);
        String body = objectMapper.writeValueAsString(Map.of("inputs", Map.of(
                "uuid", "collection-id",
                "recipient", "person@example.com")));

        mockMvc.perform(post("/api/v1/ogc/processes/download/execution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.message").value("Job submitted with ID: " + JOB_ID))
                .andExpect(jsonPath("$.status.message").value("200"))
                .andExpect(jsonPath("$.jobID").value(JOB_ID));
    }

    @Test
    void getStatusSerializesExtendedStatusInfo() throws Exception {
        DownloadJobStatusInfo statusInfo = new DownloadJobStatusInfo();
        statusInfo.setProcessID("download-dataset");
        statusInfo.setType(StatusInfo.TypeEnum.PROCESS);
        statusInfo.setJobID(JOB_ID);
        statusInfo.setStatus(StatusCode.RUNNING);
        statusInfo.setMessage("Download job is running");
        statusInfo.setCollection("Test Ocean Data Collection");
        statusInfo.setDataSelection("satellite_wind_altimeter_delayed_qc.zarr");
        statusInfo.setFormat("netcdf");
        statusInfo.setMetadataUrl("https://portal.example.test/details/collection-id");
        when(downloadJobStatusService.getStatus(JOB_ID)).thenReturn(statusInfo);

        mockMvc.perform(get("/api/v1/ogc/jobs/{jobId}", JOB_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processID").value("download-dataset"))
                .andExpect(jsonPath("$.type").value("process"))
                .andExpect(jsonPath("$.jobID").value(JOB_ID))
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.collection").value("Test Ocean Data Collection"))
                .andExpect(jsonPath("$.dataSelection").value("satellite_wind_altimeter_delayed_qc.zarr"))
                .andExpect(jsonPath("$.format").value("netcdf"))
                .andExpect(jsonPath("$.metadataUrl").value("https://portal.example.test/details/collection-id"))
                .andExpect(jsonPath("$.progress").doesNotExist());
    }

    @Test
    void getStatusReturnsGenericNotFoundAndServerErrors() throws Exception {
        when(downloadJobStatusService.getStatus("missing")).thenThrow(new DownloadJobNotFoundException());
        mockMvc.perform(get("/api/v1/ogc/jobs/missing").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Download job not found"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("AWS"))));

        when(downloadJobStatusService.getStatus("broken")).thenThrow(new DownloadJobStatusException());
        mockMvc.perform(get("/api/v1/ogc/jobs/broken").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to retrieve download job status"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void unsupportedJobsOperationsReturnNotImplemented() throws Exception {
        mockMvc.perform(get("/api/v1/ogc/jobs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotImplemented());
        mockMvc.perform(get("/api/v1/ogc/jobs/{jobId}/results", JOB_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotImplemented());
        mockMvc.perform(delete("/api/v1/ogc/jobs/{jobId}", JOB_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotImplemented());
    }
}
