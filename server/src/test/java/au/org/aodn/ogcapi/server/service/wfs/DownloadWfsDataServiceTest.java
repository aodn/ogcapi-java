package au.org.aodn.ogcapi.server.service.wfs;

import au.org.aodn.ogcapi.server.core.service.wfs.DownloadWfsDataService;
import au.org.aodn.ogcapi.server.core.service.wfs.WfsServer;
import au.org.aodn.ogcapi.server.core.service.wms.WmsServer;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class DownloadWfsDataServiceTest {

    @Mock
    protected WfsServer wfsServer;

    @Mock
    protected WmsServer wmsServer;

    @Mock
    protected HttpEntity<?> entity;

    @Test
    void verifyDecodeTextCorrectlyForSSE() throws Exception {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        RestTemplate restTemplateMock = mock(RestTemplate.class);
        // Intended to set a very small chunk size to test the edge case
        DownloadWfsDataService service = new DownloadWfsDataService(
                wmsServer, wfsServer, restTemplateMock, entity, 10);

        String original = "id,name,age,city\n1,Alice,30,Sydney\n2,Bob,25,Melbourne\n3,„Café“,42,Perth\n";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);

        // Mock WFS response
        doAnswer(inv -> {
            ResponseExtractor<?> extractor = inv.getArgument(3);
            ClientHttpResponse resp = mock(ClientHttpResponse.class);
            when(resp.getBody()).thenReturn(new ByteArrayInputStream(originalBytes));
            extractor.extractData(resp);
            return null;
        }).when(restTemplateMock).execute(anyString(), eq(HttpMethod.GET), any(), any());

        SseEmitter emitter = spy(new SseEmitter(Long.MAX_VALUE));
        List<String> base64Chunks = new CopyOnWriteArrayList<>();

        doAnswer(answer -> {
            SseEmitter.SseEventBuilder builder = answer.getArgument(0);
            var d = builder.build();

            d.forEach(s -> {
                if(s.getData() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>)s.getData();

                    if(data.containsKey("data")) {
                        base64Chunks.add((String) data.get("data"));
                    }
                    if (data.containsKey("filename")) {
                        // All item proceeded, we can continue the verification
                        assertEquals("layer:test_uuid-123.csv", data.get("filename"));
                        countDownLatch.countDown();
                    }
                }
            });
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));


        service.executeWfsRequestWithSse(
                "http://mock/wfs?...", "uuid-123", "layer:test", "text/csv",
                emitter, new AtomicBoolean());

        // Wait for processing (use Awaitility in real tests)
        countDownLatch.await();

        // Reconstruct like browser (atob + utf-8 decode)
        ByteArrayOutputStream reconstructed = new ByteArrayOutputStream();
        for (String b64 : base64Chunks) {
            reconstructed.write(Base64.getDecoder().decode(b64));
        }

        String result = reconstructed.toString(StandardCharsets.UTF_8);

        assertEquals(original, result);
    }
}
