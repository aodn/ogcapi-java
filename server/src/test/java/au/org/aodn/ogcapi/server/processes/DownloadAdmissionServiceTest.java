package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.exception.DownloadLimitExceededException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DownloadAdmissionServiceTest {

    private static final String RECIPIENT = "person@example.com";
    private static final String RECIPIENT_JOB_NAME = "generating-data-file-for-person-example-com";
    private static final String OTHER_RECIPIENT = "someone.else@example.com";

    @Mock
    private RestServices restServices;

    @Mock
    private InFlightDownloadCounter counter;

    /** In-flight downloads per recipient, standing in for what the counter would report. */
    private final Map<String, AtomicInteger> inFlight = new HashMap<>();

    private DownloadAdmissionService service;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        lenient().when(restServices.buildDownloadParameters(any()))
                .thenAnswer(invocation -> new HashMap<String, String>());
        lenient().when(restServices.submitDownloadJob(anyString(), any()))
                .thenAnswer(invocation -> UUID.randomUUID().toString());
        lenient().when(counter.countInFlight(anyString()))
                .thenAnswer(invocation -> current(invocation.getArgument(0)).get());

        service = build(limits(true, 10));
    }

    private DownloadAdmissionService build(DownloadLimitProperties limits) {
        return new DownloadAdmissionService(restServices, counter, limits);
    }

    private static DownloadLimitProperties limits(boolean enabled, int maxConcurrent) {
        return new DownloadLimitProperties(enabled, maxConcurrent, Duration.ofSeconds(15));
    }

    private AtomicInteger current(String recipient) {
        // Key the way the real counter does, so these tests cannot accidentally rely on
        // case-sensitive bookkeeping the production class does not have.
        return inFlight.computeIfAbsent(
                InFlightDownloadCounter.recipientKey(recipient), key -> new AtomicInteger());
    }

    private DownloadRequest request(String recipient) {
        return new DownloadRequest("collection-id", "key.zarr", "2023-01-01", "2023-01-31",
                "non-specified", recipient, "Test Collection",
                "https://portal.example.test/details/collection-id", "Cite as", "netcdf");
    }

    @Test
    void underTheLimitSubmitsAndReturnsTheAwsJobId() throws Exception {
        String jobId = service.submit(request(RECIPIENT));

        assertNotNull(jobId);
        verify(restServices).submitDownloadJob(eq(RECIPIENT_JOB_NAME), any());
        verify(restServices).notifyUser(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void atTheLimitIsRejectedOutright() throws Exception {
        current(RECIPIENT).set(10);

        assertThrows(DownloadLimitExceededException.class, () -> service.submit(request(RECIPIENT)));

        verify(restServices, never()).submitDownloadJob(anyString(), any());
        // Nothing was submitted, so the user must not be told their file is being produced.
        verify(restServices, never()).notifyUser(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void theRejectionMessageNamesTheConfiguredLimit() {
        current(RECIPIENT).set(10);

        DownloadLimitExceededException exception = assertThrows(
                DownloadLimitExceededException.class, () -> service.submit(request(RECIPIENT)));

        assertEquals("You already have 10 downloads in progress. "
                + "Wait for one of them to complete before starting another.", exception.getMessage());
    }

    @Test
    void oneUserAtTheLimitDoesNotBlockAnother() throws Exception {
        current(RECIPIENT).set(10);
        current(OTHER_RECIPIENT).set(0);

        assertThrows(DownloadLimitExceededException.class, () -> service.submit(request(RECIPIENT)));
        String jobId = service.submit(request(OTHER_RECIPIENT));

        assertNotNull(jobId);
    }

    @Test
    void aDifferentlyCasedAddressIsCountedAgainstTheSameLimit() throws Exception {
        current(RECIPIENT).set(10);

        assertThrows(DownloadLimitExceededException.class,
                () -> service.submit(request("Person@Example.COM")));
    }

    @Test
    void theSweepHappensBeforeTheAdmissionDecision() throws Exception {
        service.submit(request(RECIPIENT));

        verify(counter).refreshIfStale();
    }

    @Test
    void aSuccessfulSubmitIsRecordedAgainstTheRecipientForTheGraceWindow() throws Exception {
        String jobId = service.submit(request(RECIPIENT));

        verify(counter).recordSubmitted(jobId, RECIPIENT);
    }

    @Test
    void theLimitCanBeTurnedOffEntirely() throws Exception {
        DownloadAdmissionService disabled = build(limits(false, 10));
        current(RECIPIENT).set(500);

        String jobId = disabled.submit(request(RECIPIENT));

        assertNotNull(jobId);
        verify(restServices).submitDownloadJob(anyString(), any());
        verify(counter, never()).refreshIfStale();
    }

    @Test
    void aFailedSubmitDoesNotLeakAReservedSlot() throws Exception {
        // One slot free; the counter will not move because the submit below never actually
        // reaches AWS.
        current(RECIPIENT).set(9);
        org.mockito.Mockito.when(restServices.submitDownloadJob(anyString(), any()))
                .thenThrow(new IllegalStateException("AWS Batch rejected the job"));

        assertThrows(IllegalStateException.class, () -> service.submit(request(RECIPIENT)));

        // If the reservation taken for the failed attempt were never released, this retry
        // would see 9 (counter) + 1 (leaked reservation) = 10 and be rejected even though the
        // failed attempt never actually started a download.
        org.mockito.Mockito.reset(restServices);
        lenient().when(restServices.buildDownloadParameters(any())).thenReturn(new HashMap<>());
        lenient().when(restServices.submitDownloadJob(anyString(), any()))
                .thenReturn(UUID.randomUUID().toString());

        assertNotNull(service.submit(request(RECIPIENT)));
    }
}
