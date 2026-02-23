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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void verifyDecodeBinaryCorrectlyForSSE() throws Exception {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        RestTemplate restTemplateMock = mock(RestTemplate.class);
        // Intended to set a very small chunk size to test the edge case
        DownloadWfsDataService service = new DownloadWfsDataService(
                wmsServer, wfsServer, restTemplateMock, entity, 10);

        byte[] originalBytes = new byte[] {
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
                0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
                (byte)0xFF, (byte)0xFE, (byte)0xFD, (byte)0xFC, (byte)0xFB, (byte)0xFA, (byte)0xF9, (byte)0xF8,
                (byte)0x80, (byte)0x81, (byte)0x82, (byte)0x83, 0x7F, 0x7E, 0x7D, 0x7C,
                0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
                0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F,
                (byte)0xA0, (byte)0xA1, (byte)0xA2, (byte)0xA3, (byte)0xA4, (byte)0xA5, (byte)0xA6, (byte)0xA7,
                (byte)0xB0, (byte)0xB1, (byte)0xB2, (byte)0xB3, (byte)0xB4, (byte)0xB5, (byte)0xB6, (byte)0xB7,
                0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F,
                0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F,
                (byte)0xC0, (byte)0xC1, (byte)0xC2, (byte)0xC3, (byte)0xC4, (byte)0xC5, (byte)0xC6, (byte)0xC7,
                (byte)0xD0, (byte)0xD1, (byte)0xD2, (byte)0xD3, (byte)0xD4, (byte)0xD5, (byte)0xD6, (byte)0xD7,
                0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E, 0x6F,
                0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F,
                (byte)0xE0, (byte)0xE1, (byte)0xE2, (byte)0xE3, (byte)0xE4, (byte)0xE5, (byte)0xE6, (byte)0xE7,
                (byte)0xF0, (byte)0xF1, (byte)0xF2, (byte)0xF3, (byte)0xF4, (byte)0xF5, (byte)0xF6, (byte)0xF7,
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
                0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
                (byte)0xFF, (byte)0xFE, (byte)0xFD, (byte)0xFC, (byte)0xFB, (byte)0xFA, (byte)0xF9, (byte)0xF8,
                (byte)0x80, (byte)0x81, (byte)0x82, (byte)0x83, 0x7F, 0x7E, 0x7D, 0x7C,
                0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
                0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F,
                (byte)0xA0, (byte)0xA1, (byte)0xA2, (byte)0xA3, (byte)0xA4, (byte)0xA5, (byte)0xA6, (byte)0xA7,
                (byte)0xB0, (byte)0xB1, (byte)0xB2, (byte)0xB3, (byte)0xB4, (byte)0xB5, (byte)0xB6, (byte)0xB7,
                0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F,
                0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F,
                (byte)0xC0, (byte)0xC1, (byte)0xC2, (byte)0xC3, (byte)0xC4, (byte)0xC5, (byte)0xC6, (byte)0xC7,
                (byte)0xD0, (byte)0xD1, (byte)0xD2, (byte)0xD3, (byte)0xD4, (byte)0xD5, (byte)0xD6, (byte)0xD7,
                0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E, 0x6F,
                0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F,
                (byte)0xE0, (byte)0xE1, (byte)0xE2, (byte)0xE3, (byte)0xE4, (byte)0xE5, (byte)0xE6, (byte)0xE7,
                (byte)0xF0, (byte)0xF1, (byte)0xF2, (byte)0xF3, (byte)0xF4, (byte)0xF5, (byte)0xF6, (byte)0xF7
        };

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
                        assertEquals("layer:test_uuid-123.shp", data.get("filename"));
                        countDownLatch.countDown();
                    }
                }
            });
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));


        service.executeWfsRequestWithSse(
                "http://mock/wfs?...", "uuid-123", "layer:test", "shape-zip",
                emitter, new AtomicBoolean());

        // Wait for processing (use Awaitility in real tests)
        countDownLatch.await();

        // Reconstruct like browser (atob + utf-8 decode)
        ByteArrayOutputStream reconstructed = new ByteArrayOutputStream();
        for (String b64 : base64Chunks) {
            reconstructed.write(Base64.getDecoder().decode(b64));
        }

        assertArrayEquals(originalBytes, reconstructed.toByteArray());
    }
}
