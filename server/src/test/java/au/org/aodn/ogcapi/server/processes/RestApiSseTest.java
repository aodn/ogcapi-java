package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.exception.SseClientGoneException;
import au.org.aodn.ogcapi.server.core.service.sse.SseErrorHandler;
import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import au.org.aodn.ogcapi.server.core.model.enumeration.ProcessIdEnum;
import au.org.aodn.ogcapi.server.core.service.das.DasService;
import au.org.aodn.ogcapi.server.core.service.geoserver.wfs.DownloadWfsDataService;
import au.org.aodn.ogcapi.server.core.util.DasSseFrames;
import au.org.aodn.ogcapi.server.core.util.TestLogAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.services.batch.BatchClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Drives the SSE execution endpoint end-to-end (RestApi dispatch + RestServices
 * SSE flows) with the downstream services mocked, asserting the events written
 * to the stream.
 */
@ExtendWith(MockitoExtension.class)
public class RestApiSseTest {

    @Mock
    private BatchClient batchClient;

    @Mock
    private DownloadWfsDataService downloadWfsDataService;

    @Mock
    private DasService dasService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestServices restServices;

    @BeforeEach
    public void setUp() {
        restServices = new RestServices(batchClient, objectMapper, "test-job-definition", "test-job-queue");
        ReflectionTestUtils.setField(restServices, "downloadWfsDataService", downloadWfsDataService);
        ReflectionTestUtils.setField(restServices, "dasService", dasService);

        RestApi restApi = new RestApi();
        ReflectionTestUtils.setField(restApi, "restServices", restServices);

        mockMvc = MockMvcBuilders.standaloneSetup(restApi).build();
    }

    private MockHttpServletResponse postSse(String processId, Map<String, Object> inputs) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("inputs", inputs));
        return mockMvc.perform(post("/api/v1/ogc/processes/{processID}/execution", processId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn()
                .getResponse();
    }

    /**
     * The SSE work runs on another thread, so poll the mock response until the marker appears, or
     * time out and let the caller's assert fail with what was collected. Wait for the blank line
     * ending the event too, or callers could assert on a half-written payload.
     */
    private String awaitContent(MockHttpServletResponse response, String expectedMarker) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        String content = response.getContentAsString();
        while (System.currentTimeMillis() < deadline
                && (!content.contains(expectedMarker)
                || !content.substring(content.indexOf(expectedMarker)).contains("\n\n"))) {
            Thread.sleep(50);
            content = response.getContentAsString();
        }
        return content;
    }

    private static void assertEventOrder(String content, String earlierEvent, String laterEvent) {
        int earlier = content.indexOf("event:" + earlierEvent);
        int later = content.indexOf("event:" + laterEvent);
        assertTrue(earlier >= 0, "Missing event [" + earlierEvent + "] in: " + content);
        assertTrue(later > earlier, "Event [" + laterEvent + "] should come after [" + earlierEvent + "] in: " + content);
    }

    // ---------- estimateCOdownload ----------

    @Test
    public void testEstimateCODownloadForwardsBatchStyleParameters() throws Exception {
        String dasJson = "{\"estimated_output_bytes\":12345}";
        when(dasService.estimateCloudOptimisedDownloadSize(any(), anyMap(), any()))
                .thenReturn(dasJson);

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DatasetDownloadEnums.Parameter.UUID.getValue(), "test-uuid");
        inputs.put(DatasetDownloadEnums.Parameter.KEY.getValue(), "a.zarr, b.zarr");
        inputs.put(DatasetDownloadEnums.Parameter.START_DATE.getValue(), "2023-01-01");
        inputs.put(DatasetDownloadEnums.Parameter.END_DATE.getValue(), "2023-01-31");
        inputs.put(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue(), "non-specified");
        inputs.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), "netcdf");

        MockHttpServletResponse response = postSse(ProcessIdEnum.DOWNLOAD_CO_ESTIMATE.getValue(), inputs);
        String content = awaitContent(response, "event:estimate-complete");

        assertEventOrder(content, "connection-established", "estimate-complete");
        assertTrue(content.contains(dasJson), "das JSON should be forwarded unchanged in: " + content);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dasService).estimateCloudOptimisedDownloadSize(eq("test-uuid"), paramsCaptor.capture(), any());

        Map<String, String> params = paramsCaptor.getValue();
        // key is forwarded raw (CSV, untrimmed) - DAS splits it, matching the batch download.
        assertEquals("a.zarr, b.zarr", params.get(DatasetDownloadEnums.Parameter.KEY.getValue()));
        assertEquals("2023-01-01", params.get(DatasetDownloadEnums.Parameter.START_DATE.getValue()));
        assertEquals("2023-01-31", params.get(DatasetDownloadEnums.Parameter.END_DATE.getValue()));
        assertEquals("non-specified", params.get(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue()));
        assertEquals("netcdf", params.get(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue()));
    }

    @Test
    public void testEstimateCODownloadForwardsWildcardKeyRaw() throws Exception {
        when(dasService.estimateCloudOptimisedDownloadSize(any(), anyMap(), any()))
                .thenReturn("{}");

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DatasetDownloadEnums.Parameter.UUID.getValue(), "test-uuid");
        inputs.put(DatasetDownloadEnums.Parameter.KEY.getValue(), "*");
        inputs.put(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue(), "non-specified");
        inputs.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), "netcdf");

        MockHttpServletResponse response = postSse(ProcessIdEnum.DOWNLOAD_CO_ESTIMATE.getValue(), inputs);
        awaitContent(response, "event:estimate-complete");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dasService).estimateCloudOptimisedDownloadSize(eq("test-uuid"), paramsCaptor.capture(), any());
        assertEquals("*", paramsCaptor.getValue().get(DatasetDownloadEnums.Parameter.KEY.getValue()),
                "wildcard key is forwarded raw; DAS expands it to all keys");
    }

    @Test
    public void testEstimateCODownloadDasFailureEmitsEstimateFailed() throws Exception {
        when(dasService.estimateCloudOptimisedDownloadSize(any(), anyMap(), any()))
                .thenThrow(new RuntimeException("das returned 404"));

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DatasetDownloadEnums.Parameter.UUID.getValue(), "test-uuid");
        inputs.put(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue(), "non-specified");
        inputs.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), "netcdf");

        MockHttpServletResponse response = postSse(ProcessIdEnum.DOWNLOAD_CO_ESTIMATE.getValue(), inputs);
        String content = awaitContent(response, "event:estimate-failed");

        assertEventOrder(content, "connection-established", "estimate-failed");
        assertTrue(content.contains("das returned 404"), "Failure reason should be forwarded in: " + content);
    }

    /**
     * The regression this guards: a data-access-service that fails without ever opening its
     * stream sends no heartbeat, so with nothing else writing to the client the connection goes
     * quiet for as long as that failure takes, 30-60s for a gateway timeout.
     */
    @Test
    public void testEstimateCOKeepsTheClientAliveWhileDasSaysNothing() throws Exception {
        ReflectionTestUtils.setField(restServices, "estimateKeepAliveSeconds", 1L);

        CountDownLatch dasAnswers = new CountDownLatch(1);
        when(dasService.estimateCloudOptimisedDownloadSize(any(), anyMap(), any()))
                .thenAnswer(invocation -> {
                    // No heartbeat and no result, the way a gateway 504 for a downed DAS arrives.
                    assertTrue(dasAnswers.await(5, TimeUnit.SECONDS), "Test did not release the DAS call");
                    throw new RuntimeException("data-access-service returned 504 Gateway Timeout");
                });

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DatasetDownloadEnums.Parameter.UUID.getValue(), "test-uuid");
        inputs.put(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue(), "non-specified");
        inputs.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), "netcdf");

        MockHttpServletResponse response = postSse(ProcessIdEnum.DOWNLOAD_CO_ESTIMATE.getValue(), inputs);

        String whileWaiting = awaitContent(response, "event:keep-alive");
        assertEventOrder(whileWaiting, "connection-established", "keep-alive");
        assertFalse(whileWaiting.contains("event:estimate-failed"),
                "The estimate has not failed yet: " + whileWaiting);

        dasAnswers.countDown();

        String content = awaitContent(response, "event:estimate-failed");
        assertEventOrder(content, "keep-alive", "estimate-failed");
        assertTrue(content.contains("504 Gateway Timeout"), "Failure reason should be forwarded in: " + content);
    }

    /**
     * The regression this guards: every data-access-service heartbeat is probed on, and a probe
     * used to be a keep-alive event. DAS heartbeats at about the rate the ticker runs at, so the
     * client received the ticker's keep-alive and a heartbeat's a moment later and read the same
     * event twice. A probe writes an SSE comment now, which EventSource discards, so heartbeats
     * keep the connection busy without reaching the client at all.
     */
    @Test
    public void testEstimateCODasHeartbeatsDoNotReachTheClientAsEvents() throws Exception {
        // The ticker is left at its default interval, longer than this test runs, so every
        // keep-alive in the response would have to have come from a heartbeat.
        when(dasService.estimateCloudOptimisedDownloadSize(any(), anyMap(), any()))
                .thenAnswer(invocation -> {
                    DasSseFrames.FrameCallback onHeartbeat = invocation.getArgument(2);
                    for (int i = 0; i < 3; i++) {
                        onHeartbeat.onFrame();
                    }
                    return "{\"estimated_output_bytes\":12345}";
                });

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DatasetDownloadEnums.Parameter.UUID.getValue(), "test-uuid");
        inputs.put(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue(), "non-specified");
        inputs.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), "netcdf");

        MockHttpServletResponse response = postSse(ProcessIdEnum.DOWNLOAD_CO_ESTIMATE.getValue(), inputs);
        String content = awaitContent(response, "event:estimate-complete");

        assertFalse(content.contains("event:keep-alive"),
                "A heartbeat must not surface as an event the client reads: " + content);
        assertEquals(3, countOf(content, ":probe"),
                "Each heartbeat should still write to the client: " + content);
    }

    private static int countOf(String content, String needle) {
        int count = 0;
        for (int at = content.indexOf(needle); at >= 0; at = content.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    @Test
    public void testEstimateCODownloadClientDisconnectIsNotReportedAsAFailedEstimate() throws Exception {
        // A disconnect is what aborts the DAS call, and the estimate has an unchecked signature,
        // so the IOException that unwound it arrives nested. No client is left to tell, and
        // reporting estimate-failed here would log a failure that never happened.
        when(dasService.estimateCloudOptimisedDownloadSize(any(), anyMap(), any()))
                .thenThrow(new UncheckedIOException(
                        new SseClientGoneException("test-uuid", new IOException("Broken pipe"))));

        TestLogAppender logs = TestLogAppender.attachTo(SseErrorHandler.class);
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(DatasetDownloadEnums.Parameter.UUID.getValue(), "test-uuid");
            inputs.put(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue(), "non-specified");
            inputs.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), "netcdf");

            MockHttpServletResponse response = postSse(ProcessIdEnum.DOWNLOAD_CO_ESTIMATE.getValue(), inputs);

            String disconnectLog = awaitLogContaining(logs, "Client disconnected for UUID: test-uuid");
            assertTrue(disconnectLog.contains("Client disconnected"),
                    "The disconnect should be logged as a disconnect, got: " + disconnectLog);
            assertFalse(response.getContentAsString().contains("event:estimate-failed"),
                    "A departed client must not be told its estimate failed: " + response.getContentAsString());
        } finally {
            logs.detachFrom(SseErrorHandler.class);
        }
    }

    /**
     * The stream's work runs on another thread, so wait for the expected line rather than
     * assuming it has been logged by the time the request returns.
     */
    private String awaitLogContaining(TestLogAppender logs, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            String messages = logs.eventsAtLevel(Level.WARN).stream()
                    .map(event -> event.getMessage().getFormattedMessage())
                    .collect(Collectors.joining("\n"));
            if (messages.contains(expected)) {
                return messages;
            }
            Thread.sleep(50);
        }
        return "<nothing logged>";
    }

    @Test
    public void testEstimateCODownloadMissingUuidEmitsError() throws Exception {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), "netcdf");

        MockHttpServletResponse response = postSse(ProcessIdEnum.DOWNLOAD_CO_ESTIMATE.getValue(), inputs);
        String content = awaitContent(response, "event:error");

        assertTrue(content.contains("event:error"), "Validation failure should emit error event in: " + content);
        verifyNoInteractions(dasService);
    }

    // ---------- estimateWfsDownload ----------

    @Test
    public void testEstimateWfsDownloadHappyPath() throws Exception {
        when(downloadWfsDataService.estimateDownloadSize(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(BigInteger.valueOf(98765));

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DatasetDownloadEnums.Parameter.UUID.getValue(), "test-uuid");
        inputs.put(DatasetDownloadEnums.Parameter.LAYER_NAME.getValue(), "test-layer");
        inputs.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), "text/csv");

        MockHttpServletResponse response = postSse(ProcessIdEnum.DOWNLOAD_WFS_ESTIMATE.getValue(), inputs);
        String content = awaitContent(response, "event:estimate-complete");

        assertEventOrder(content, "connection-established", "estimate-complete");
        assertTrue(content.contains("98765"), "Estimated size should be in the payload: " + content);
        verify(downloadWfsDataService).estimateDownloadSize(
                eq("test-uuid"), eq("test-layer"), any(), any(), any(), any(), eq("text/csv"));
        verify(downloadWfsDataService, never()).streamWfsDataWithSse(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testEstimateWfsDownloadServiceFailureEmitsEstimateFailed() throws Exception {
        when(downloadWfsDataService.estimateDownloadSize(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("geoserver down"));

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DatasetDownloadEnums.Parameter.UUID.getValue(), "test-uuid");
        inputs.put(DatasetDownloadEnums.Parameter.LAYER_NAME.getValue(), "test-layer");
        inputs.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), "text/csv");

        MockHttpServletResponse response = postSse(ProcessIdEnum.DOWNLOAD_WFS_ESTIMATE.getValue(), inputs);
        String content = awaitContent(response, "event:estimate-failed");

        assertEventOrder(content, "connection-established", "estimate-failed");
    }

    @Test
    public void testEstimateWfsDownloadUnknownOutputFormatEmitsError() throws Exception {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DatasetDownloadEnums.Parameter.UUID.getValue(), "test-uuid");
        inputs.put(DatasetDownloadEnums.Parameter.LAYER_NAME.getValue(), "test-layer");
        inputs.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), "bogus-format");

        MockHttpServletResponse response = postSse(ProcessIdEnum.DOWNLOAD_WFS_ESTIMATE.getValue(), inputs);
        String content = awaitContent(response, "event:error");

        assertTrue(content.contains("event:error"), "Unknown output format should emit error event in: " + content);
        verifyNoInteractions(downloadWfsDataService);
    }

    // ---------- downloadWfs ----------

    @Test
    public void testDownloadWfsRoutesToStreaming() throws Exception {
        when(downloadWfsDataService.prepareWfsRequestUrl(any(), any(), any(), any(), any(), any(), any(), anyLong(), anyBoolean()))
                .thenReturn("http://geoserver/wfs?request=GetFeature");
        // Complete the stream when the (mocked) streaming call is reached, ending the SSE.
        doAnswer(invocation -> {
            ((SseEmitter) invocation.getArgument(4)).complete();
            return null;
        }).when(downloadWfsDataService).streamWfsDataWithSse(any(), any(), any(), any(), any(), any());

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DatasetDownloadEnums.Parameter.UUID.getValue(), "test-uuid");
        inputs.put(DatasetDownloadEnums.Parameter.LAYER_NAME.getValue(), "test-layer");
        inputs.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), "text/csv");

        MockHttpServletResponse response = postSse(ProcessIdEnum.DOWNLOAD_WFS_SSE.getValue(), inputs);
        String content = awaitContent(response, "event:connection-established");

        assertTrue(content.contains("event:connection-established"), "Missing connection event in: " + content);
        verify(downloadWfsDataService, timeout(5000)).streamWfsDataWithSse(
                eq("http://geoserver/wfs?request=GetFeature"), eq("test-uuid"), eq("test-layer"),
                eq("text/csv"), any(SseEmitter.class), any());
        verify(downloadWfsDataService, never()).estimateDownloadSize(any(), any(), any(), any(), any(), any(), any());
    }

    // ---------- dispatch ----------

    @Test
    public void testUnknownProcessIdHitsDefaultBranch() throws Exception {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(DatasetDownloadEnums.Parameter.UUID.getValue(), "test-uuid");
        inputs.put(DatasetDownloadEnums.Parameter.LAYER_NAME.getValue(), "test-layer");
        inputs.put(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue(), "text/csv");

        postSse("no-such-process", inputs);

        verifyNoInteractions(downloadWfsDataService, dasService);
    }
}
