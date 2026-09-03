package au.org.aodn.ogcapi.server.processes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.DescribeJobsRequest;
import software.amazon.awssdk.services.batch.model.DescribeJobsResponse;
import software.amazon.awssdk.services.batch.model.JobDetail;
import software.amazon.awssdk.services.batch.model.JobStatus;
import software.amazon.awssdk.services.batch.model.JobSummary;
import software.amazon.awssdk.services.batch.model.ListJobsRequest;
import software.amazon.awssdk.services.batch.model.ListJobsResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InFlightDownloadCounterTest {

    private static final String QUEUE_NAME = "initial-queue";
    private static final String CHILD_QUEUE_NAME = "child-queue";
    private static final String QUEUE_ARN = "arn:aws:batch:ap-southeast-2:123456789012:job-queue/" + QUEUE_NAME;
    private static final String DEFINITION_NAME = "download-definition";
    private static final String DEFINITION_ARN =
            "arn:aws:batch:ap-southeast-2:123456789012:job-definition/" + DEFINITION_NAME + ":7";
    private static final Instant NOW = Instant.parse("2026-08-24T02:00:00Z");

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    @Mock
    private BatchClient batchClient;

    /** Jobs DescribeJobs will answer with, by job id. */
    private final Map<String, JobDetail> describedJobs = new HashMap<>();

    /** Non-terminal job summaries per queue, as ListJobs would page them out. */
    private final Map<String, List<JobSummary>> listedJobs = new HashMap<>();

    private MutableTestClock clock;
    private InFlightDownloadCounter counter;

    @BeforeEach
    void setUp() {
        lenient().when(batchClient.describeJobs(any(DescribeJobsRequest.class))).thenAnswer(invocation -> {
            DescribeJobsRequest request = invocation.getArgument(0);
            List<JobDetail> jobs = request.jobs().stream()
                    .map(describedJobs::get)
                    .filter(job -> job != null)
                    .toList();
            return DescribeJobsResponse.builder().jobs(jobs).build();
        });
        // The counter lists by status and never by name filter, which is what distinguishes
        // it from the status service. Every summary is reported under RUNNING so one entry
        // per queue is enough to describe the fixture.
        lenient().when(batchClient.listJobs(any(ListJobsRequest.class))).thenAnswer(invocation -> {
            ListJobsRequest request = invocation.getArgument(0);
            if (request.jobStatus() != JobStatus.RUNNING) {
                return ListJobsResponse.builder().build();
            }
            return ListJobsResponse.builder()
                    .jobSummaryList(listedJobs.getOrDefault(request.jobQueue(), List.of()))
                    .build();
        });

        clock = new MutableTestClock(NOW);
        counter = newCounter();
    }

    private InFlightDownloadCounter newCounter() {
        return new InFlightDownloadCounter(
                batchClient,
                new BatchJobProperties(QUEUE_NAME, DEFINITION_NAME, CHILD_QUEUE_NAME),
                new DownloadLimitProperties(true, 10, Duration.ofSeconds(15)),
                clock);
    }

    private void master(String jobId, String recipient) {
        describedJobs.put(jobId, JobDetail.builder()
                .jobId(jobId)
                .jobName(RestServices.downloadJobName(recipient))
                .jobQueue(QUEUE_ARN)
                .jobDefinition(DEFINITION_ARN)
                .status(JobStatus.RUNNING)
                .parameters(Map.of("type", "sub-setting", "recipient", recipient))
                .build());
    }

    private void onQueue(String queue, String jobId, String jobName) {
        listedJobs.computeIfAbsent(queue, key -> new ArrayList<>())
                .add(JobSummary.builder().jobId(jobId).jobName(jobName).build());
    }

    @Test
    void countsMasterJobsStillOnTheDownloadQueue() {
        master("m1", ALICE);
        master("m2", ALICE);
        master("m3", BOB);
        onQueue(QUEUE_NAME, "m1", RestServices.downloadJobName(ALICE));
        onQueue(QUEUE_NAME, "m2", RestServices.downloadJobName(ALICE));
        onQueue(QUEUE_NAME, "m3", RestServices.downloadJobName(BOB));

        counter.refresh();

        assertEquals(2, counter.countInFlight(ALICE));
        assertEquals(1, counter.countInFlight(BOB));
    }

    @Test
    void countsAWorkflowWhoseMasterHasFinishedButWhoseChildrenAreStillRunning() {
        // The master succeeded and left the download queue; only its prepare child is live.
        master("m1", ALICE);
        onQueue(CHILD_QUEUE_NAME, "c1", "prepare-data-for-job-m1");

        counter.refresh();

        assertEquals(1, counter.countInFlight(ALICE));
    }

    @Test
    void countsAWorkflowOnlyOnceWhenBothItsChildrenAreRunning() {
        master("m1", ALICE);
        onQueue(CHILD_QUEUE_NAME, "c1", "prepare-data-for-job-m1");
        onQueue(CHILD_QUEUE_NAME, "c2", "collect-data-for-job-m1");

        counter.refresh();

        assertEquals(1, counter.countInFlight(ALICE));
    }

    @Test
    void countsAWorkflowOnlyOnceWhenTheMasterAndItsChildAreBothLive() {
        master("m1", ALICE);
        onQueue(QUEUE_NAME, "m1", RestServices.downloadJobName(ALICE));
        onQueue(CHILD_QUEUE_NAME, "c1", "prepare-data-for-job-m1");

        counter.refresh();

        assertEquals(1, counter.countInFlight(ALICE));
    }

    @Test
    void ignoresJobsThatAreNotDownloadMasters() {
        // Something else entirely, sharing the queue.
        describedJobs.put("x1", JobDetail.builder()
                .jobId("x1")
                .jobName("some-other-workload")
                .jobQueue(QUEUE_ARN)
                .jobDefinition(DEFINITION_ARN)
                .status(JobStatus.RUNNING)
                .parameters(Map.of("type", "something-else", "recipient", ALICE))
                .build());
        onQueue(QUEUE_NAME, "x1", "some-other-workload");

        counter.refresh();

        assertEquals(0, counter.countInFlight(ALICE));
    }

    @Test
    void takesTheOwnerFromTheRecipientParameterNotTheSanitisedJobName() {
        // Both addresses sanitise to generating-data-file-for-a-b-x-com, so counting by job
        // name would merge two different users into one.
        String first = "a.b@x.com";
        String second = "a-b@x-com";
        assertEquals(RestServices.downloadJobName(first), RestServices.downloadJobName(second));

        master("m1", first);
        master("m2", second);
        onQueue(QUEUE_NAME, "m1", RestServices.downloadJobName(first));
        onQueue(QUEUE_NAME, "m2", RestServices.downloadJobName(second));

        counter.refresh();

        assertEquals(1, counter.countInFlight(first));
        assertEquals(1, counter.countInFlight(second));
    }

    @Test
    void theSameAddressInDifferentCaseOrWithSpacesIsOneUser() {
        // Otherwise a single capital letter silently buys a second allowance of slots.
        master("m1", "Alice@Example.com");
        master("m2", "alice@example.com");
        onQueue(QUEUE_NAME, "m1", RestServices.downloadJobName("Alice@Example.com"));
        onQueue(QUEUE_NAME, "m2", RestServices.downloadJobName("alice@example.com"));

        counter.refresh();

        assertEquals(2, counter.countInFlight(ALICE));
        assertEquals(2, counter.countInFlight("ALICE@EXAMPLE.COM"));
        assertEquals(2, counter.countInFlight("  alice@example.com  "));
    }

    @Test
    void aJustSubmittedJobCountsBeforeAnySweepCanSeeIt() {
        counter.refresh();
        assertEquals(0, counter.countInFlight(ALICE));

        counter.recordSubmitted("m-new", ALICE);

        assertEquals(1, counter.countInFlight(ALICE));
    }

    @Test
    void aJustSubmittedJobIsNotCountedTwiceOnceTheSweepSeesIt() {
        counter.recordSubmitted("m1", ALICE);
        master("m1", ALICE);
        onQueue(QUEUE_NAME, "m1", RestServices.downloadJobName(ALICE));

        counter.refresh();

        assertEquals(1, counter.countInFlight(ALICE));
    }

    @Test
    void aRecentSubmissionStopsCountingOnceItsGraceHasPassed() {
        counter.recordSubmitted("m1", ALICE);
        assertEquals(1, counter.countInFlight(ALICE));

        // Past the grace the sweep is authoritative again, so a job AWS no longer reports as
        // non-terminal stops holding a slot.
        clock.advance(InFlightDownloadCounter.SUBMIT_GRACE.plusSeconds(1));

        assertEquals(0, counter.countInFlight(ALICE));
    }

    @Test
    void everyNonTerminalStatusIsListed() {
        counter.refresh();

        ArgumentCaptor<ListJobsRequest> captor = ArgumentCaptor.forClass(ListJobsRequest.class);
        verify(batchClient, atLeastOnce()).listJobs(captor.capture());
        List<JobStatus> statuses = captor.getAllValues().stream()
                .filter(request -> QUEUE_NAME.equals(request.jobQueue()))
                .map(ListJobsRequest::jobStatus)
                .toList();
        assertTrue(statuses.containsAll(List.of(
                JobStatus.SUBMITTED, JobStatus.PENDING, JobStatus.RUNNABLE,
                JobStatus.STARTING, JobStatus.RUNNING)));
        // Terminal states must never be swept: a finished download frees its slot.
        assertTrue(statuses.stream().noneMatch(status ->
                status == JobStatus.SUCCEEDED || status == JobStatus.FAILED));
        // Counting never filters by job name; that is the status service's query.
        assertTrue(captor.getAllValues().stream().allMatch(request -> request.filters().isEmpty()));
    }

    @Test
    void aFailedSweepKeepsServingThePreviousCount() {
        master("m1", ALICE);
        onQueue(QUEUE_NAME, "m1", RestServices.downloadJobName(ALICE));
        counter.refresh();
        assertEquals(1, counter.countInFlight(ALICE));

        doThrow(new RuntimeException("AWS is having a moment"))
                .when(batchClient).listJobs(any(ListJobsRequest.class));
        counter.refresh();

        assertEquals(1, counter.countInFlight(ALICE), "a failed sweep must not zero the count");
    }

    @Test
    void aFreshSnapshotIsNotSweptAgain() {
        counter.refresh();
        clearInvocations(batchClient);

        counter.refreshIfStale();

        verify(batchClient, never()).listJobs(any(ListJobsRequest.class));
    }

    @Test
    void childNamesResolveToTheirMasterJob() {
        assertEquals("abc", InFlightDownloadCounter.masterIdOf("prepare-data-for-job-abc"));
        assertEquals("abc", InFlightDownloadCounter.masterIdOf("collect-data-for-job-abc"));
        assertNull(InFlightDownloadCounter.masterIdOf("generating-data-file-for-someone"));
        assertNull(InFlightDownloadCounter.masterIdOf("prepare-data-for-job-"));
        assertNull(InFlightDownloadCounter.masterIdOf(null));
    }
}
