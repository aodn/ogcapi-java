package au.org.aodn.ogcapi.server.processes;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownloadAdmissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-24T02:00:00Z");
    private static final String RECIPIENT = "person@example.com";
    private static final String RECIPIENT_JOB_NAME = "generating-data-file-for-person-example-com";
    private static final String OTHER_RECIPIENT = "someone.else@example.com";

    @Mock
    private RestServices restServices;

    @Mock
    private InFlightDownloadCounter counter;

    /** In-flight downloads per recipient, standing in for what the counter would report. */
    private final Map<String, AtomicInteger> inFlight = new HashMap<>();
    private final List<String> submittedJobNames = new ArrayList<>();

    private MutableTestClock clock;
    private DownloadAdmissionService service;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        lenient().when(restServices.buildDownloadParameters(any()))
                .thenAnswer(invocation -> new HashMap<String, String>());
        // A submit hands back a fresh AWS job id and, exactly as the real counter does,
        // immediately makes that job count towards its recipient.
        lenient().when(restServices.submitDownloadJob(anyString(), any())).thenAnswer(invocation -> {
            submittedJobNames.add(invocation.getArgument(0));
            return UUID.randomUUID().toString();
        });
        lenient().when(counter.countInFlight(anyString()))
                .thenAnswer(invocation -> current(invocation.getArgument(0)).get());
        lenient().doAnswer(invocation -> {
            current(invocation.getArgument(1)).incrementAndGet();
            return null;
        }).when(counter).recordSubmitted(anyString(), anyString());

        clock = new MutableTestClock(NOW);
        service = build(limits(true, 10));
    }

    private DownloadAdmissionService build(DownloadLimitProperties limits) {
        return new DownloadAdmissionService(restServices, counter, limits, clock);
    }

    private static DownloadLimitProperties limits(boolean enabled, int maxConcurrent) {
        return new DownloadLimitProperties(
                enabled, maxConcurrent, Duration.ofSeconds(15), Duration.ofHours(24), 1000);
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
    void underTheLimitSubmitsStraightAwayAndReturnsTheAwsJobId() throws Exception {
        String jobId = service.submitOrHold(request(RECIPIENT)).jobId();

        assertNotNull(jobId);
        assertNull(service.findHeld(jobId));
        verify(restServices).submitDownloadJob(eq(RECIPIENT_JOB_NAME), any());
        verify(restServices).notifyUser(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertEquals(List.of(RECIPIENT_JOB_NAME), submittedJobNames);
    }

    @Test
    void atTheLimitHoldsInsteadOfRejectingAndStillReturnsAJobId() throws Exception {
        current(RECIPIENT).set(10);

        String jobId = service.submitOrHold(request(RECIPIENT)).jobId();

        assertNotNull(jobId);
        // The id has to be a canonical lowercase UUID or the status endpoint will not accept it.
        assertEquals(jobId, UUID.fromString(jobId).toString());
        assertNotNull(service.findHeld(jobId));
        verify(restServices, never()).submitDownloadJob(anyString(), any());
        // Nothing has started, so the user must not be told their file is being produced.
        verify(restServices, never()).notifyUser(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void anImmediateSubmitIsReportedAsNotQueued() throws Exception {
        DownloadAdmission admission = service.submitOrHold(request(RECIPIENT));

        assertFalse(admission.queued());
        assertNull(admission.queuePosition());
    }

    @Test
    void aHeldDownloadReportsItsPlaceInTheUsersOwnQueue() throws Exception {
        current(RECIPIENT).set(10);

        DownloadAdmission first = service.submitOrHold(request(RECIPIENT));
        DownloadAdmission second = service.submitOrHold(request(RECIPIENT));
        DownloadAdmission third = service.submitOrHold(request(RECIPIENT));

        assertTrue(first.queued());
        assertEquals(1, first.queuePosition());
        assertEquals(2, second.queuePosition());
        assertEquals(3, third.queuePosition());
    }

    @Test
    void queuePositionCountsOnlyTheSameUsersDownloads() throws Exception {
        current(RECIPIENT).set(10);
        current(OTHER_RECIPIENT).set(10);

        service.submitOrHold(request(OTHER_RECIPIENT));
        service.submitOrHold(request(OTHER_RECIPIENT));
        DownloadAdmission mine = service.submitOrHold(request(RECIPIENT));

        // Two other people are waiting ahead in the shared queue, but they do not delay this
        // one: it is released when its own owner's slots free.
        assertEquals(1, mine.queuePosition());
    }

    @Test
    void queuePositionMovesUpAsTheUsersEarlierDownloadsAreReleased() throws Exception {
        current(RECIPIENT).set(10);
        service.submitOrHold(request(RECIPIENT));
        String second = service.submitOrHold(request(RECIPIENT)).jobId();
        assertEquals(2, service.findHeld(second).position());

        current(RECIPIENT).set(9);
        service.releaseHeldDownloads();

        assertEquals(1, service.findHeld(second).position(), "it should now be next");
    }

    @Test
    void theEleventhDownloadIsHeldAndTheFirstTenAreNot() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            ids.add(service.submitOrHold(request(RECIPIENT)).jobId());
        }

        verify(restServices, times(10)).submitDownloadJob(anyString(), any());
        for (int i = 0; i < 10; i++) {
            assertNull(service.findHeld(ids.get(i)), "download " + i + " should have been submitted");
        }
        assertNotNull(service.findHeld(ids.get(10)), "the eleventh download should be held");
    }

    @Test
    void aDifferentlyCasedAddressQueuesBehindTheSameUsersHeldDownloads() throws Exception {
        current(RECIPIENT).set(10);
        String first = service.submitOrHold(request(RECIPIENT)).jobId();

        // A slot frees, but the same person writing their address differently must still
        // queue behind their own earlier request rather than overtake it.
        current(RECIPIENT).set(0);
        String second = service.submitOrHold(request("Person@Example.COM")).jobId();

        assertNotNull(service.findHeld(first));
        assertNotNull(service.findHeld(second));
        verify(restServices, never()).submitDownloadJob(anyString(), any());
    }

    @Test
    void oneUserAtTheLimitDoesNotBlockAnother() throws Exception {
        current(RECIPIENT).set(10);

        String held = service.submitOrHold(request(RECIPIENT)).jobId();
        String submitted = service.submitOrHold(request(OTHER_RECIPIENT)).jobId();

        assertNotNull(service.findHeld(held));
        assertNull(service.findHeld(submitted));
    }

    @Test
    void aNewRequestNeverJumpsAheadOfTheSameUsersOwnQueue() throws Exception {
        current(RECIPIENT).set(10);
        String first = service.submitOrHold(request(RECIPIENT)).jobId();

        // A slot frees, but the second request still queues behind the first.
        current(RECIPIENT).set(0);
        String second = service.submitOrHold(request(RECIPIENT)).jobId();

        assertNotNull(service.findHeld(first));
        assertNotNull(service.findHeld(second));
        verify(restServices, never()).submitDownloadJob(anyString(), any());
    }

    @Test
    void theReleaseLoopSubmitsHeldDownloadsAsSlotsFree() throws Exception {
        current(RECIPIENT).set(10);
        String heldId = service.submitOrHold(request(RECIPIENT)).jobId();

        // Still full: nothing is released.
        service.releaseHeldDownloads();
        assertNotNull(service.findHeld(heldId));
        verify(restServices, never()).submitDownloadJob(anyString(), any());

        // A job finished, so the held one goes.
        current(RECIPIENT).set(9);
        service.releaseHeldDownloads();

        assertNull(service.findHeld(heldId));
        assertNotNull(service.awsJobIdOf(heldId));
        assertNotEquals(heldId, service.awsJobIdOf(heldId));
        verify(restServices).submitDownloadJob(anyString(), any());
        // The started email goes out now, when the download actually starts.
        verify(restServices).notifyUser(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void theReleaseLoopReleasesOnlyAsManyAsThereAreFreeSlots() throws Exception {
        current(RECIPIENT).set(10);
        List<String> heldIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            heldIds.add(service.submitOrHold(request(RECIPIENT)).jobId());
        }

        // Two slots free in one go; the other three must stay put.
        current(RECIPIENT).set(8);
        service.releaseHeldDownloads();

        verify(restServices, times(2)).submitDownloadJob(anyString(), any());
        assertNull(service.findHeld(heldIds.get(0)));
        assertNull(service.findHeld(heldIds.get(1)));
        assertNotNull(service.findHeld(heldIds.get(2)));
        assertNotNull(service.findHeld(heldIds.get(3)));
        assertNotNull(service.findHeld(heldIds.get(4)));
    }

    @Test
    void heldDownloadsAreReleasedInTheOrderTheyArrived() throws Exception {
        current(RECIPIENT).set(10);
        String first = service.submitOrHold(request(RECIPIENT)).jobId();
        String second = service.submitOrHold(request(RECIPIENT)).jobId();

        current(RECIPIENT).set(9);
        service.releaseHeldDownloads();

        assertNull(service.findHeld(first));
        assertNotNull(service.findHeld(second));
    }

    @Test
    void theReleaseLoopMakesNoAwsCallWhenNothingIsHeld() {
        service.releaseHeldDownloads();

        verify(counter, never()).refresh();
        verify(restServices, never()).submitDownloadJob(anyString(), any());
    }

    @Test
    void aDownloadHeldPastTheMaximumHoldAgeIsAbandoned() throws Exception {
        DownloadAdmissionService shortLived = build(new DownloadLimitProperties(
                true, 10, Duration.ofSeconds(15), Duration.ofMinutes(30), 1000));
        current(RECIPIENT).set(10);
        String heldId = shortLived.submitOrHold(request(RECIPIENT)).jobId();
        assertNotNull(shortLived.findHeld(heldId));

        clock.advance(Duration.ofMinutes(31));
        // Free the slot too, so the only reason it is not released is that it expired.
        current(RECIPIENT).set(0);
        shortLived.releaseHeldDownloads();

        assertNull(shortLived.findHeld(heldId));
        assertNull(shortLived.awsJobIdOf(heldId));
        verify(restServices, never()).submitDownloadJob(anyString(), any());
    }

    @Test
    void aFailedReleaseKeepsTheDownloadHeldForAnotherAttempt() throws Exception {
        current(RECIPIENT).set(10);
        String heldId = service.submitOrHold(request(RECIPIENT)).jobId();

        when(restServices.submitDownloadJob(anyString(), any()))
                .thenThrow(new IllegalStateException("AWS Batch rejected the job"));
        current(RECIPIENT).set(0);
        service.releaseHeldDownloads();

        assertNotNull(service.findHeld(heldId), "a failed submit must not lose the download");
        assertNull(service.awsJobIdOf(heldId));
    }

    @Test
    void aReleaseThatKeepsFailingIsEventuallyAbandoned() throws Exception {
        current(RECIPIENT).set(10);
        String heldId = service.submitOrHold(request(RECIPIENT)).jobId();

        when(restServices.submitDownloadJob(anyString(), any()))
                .thenThrow(new IllegalStateException("AWS Batch rejected the job"));
        current(RECIPIENT).set(0);
        service.releaseHeldDownloads();
        service.releaseHeldDownloads();
        service.releaseHeldDownloads();

        assertNull(service.findHeld(heldId));
    }

    @Test
    void theHoldQueueIsBounded() throws Exception {
        DownloadAdmissionService bounded = build(new DownloadLimitProperties(
                true, 1, Duration.ofSeconds(15), Duration.ofHours(24), 2));
        current(RECIPIENT).set(1);

        bounded.submitOrHold(request(RECIPIENT));
        bounded.submitOrHold(request(RECIPIENT));

        assertThrows(IllegalStateException.class, () -> bounded.submitOrHold(request(RECIPIENT)));
    }

    @Test
    void theLimitCanBeTurnedOffEntirely() throws Exception {
        DownloadAdmissionService disabled = build(limits(false, 10));
        current(RECIPIENT).set(500);

        String jobId = disabled.submitOrHold(request(RECIPIENT)).jobId();

        assertNull(disabled.findHeld(jobId));
        verify(restServices).submitDownloadJob(anyString(), any());
        verify(counter, never()).refreshIfStale();
    }

    @Test
    void theSweepHappensBeforeTheAdmissionDecision() throws Exception {
        service.submitOrHold(request(RECIPIENT));

        verify(counter).refreshIfStale();
    }

    @Test
    void aReleasedDownloadIsSubmittedWithTheSameNameAndRecipientItWasAcceptedWith() throws Exception {
        current(RECIPIENT).set(10);
        service.submitOrHold(request(RECIPIENT));

        current(RECIPIENT).set(0);
        service.releaseHeldDownloads();

        assertEquals(List.of(RECIPIENT_JOB_NAME), submittedJobNames);
        ArgumentCaptor<String> recipientCaptor = ArgumentCaptor.forClass(String.class);
        verify(restServices).notifyUser(recipientCaptor.capture(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
        assertEquals(RECIPIENT, recipientCaptor.getValue());
    }
}
