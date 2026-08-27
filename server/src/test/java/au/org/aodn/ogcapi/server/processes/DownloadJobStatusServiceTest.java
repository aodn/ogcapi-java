package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.processes.model.StatusCode;
import au.org.aodn.ogcapi.processes.model.StatusInfo;
import au.org.aodn.ogcapi.server.core.exception.DownloadJobNotFoundException;
import au.org.aodn.ogcapi.server.core.exception.DownloadJobStatusException;
import au.org.aodn.ogcapi.server.core.model.DownloadJobStatusInfo;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownloadJobStatusServiceTest {

    private static final String JOB_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String QUEUE_NAME = "initial-queue";
    private static final String CHILD_QUEUE_NAME = "child-queue";
    private static final String QUEUE_ARN = "arn:aws:batch:ap-southeast-2:123456789012:job-queue/" + QUEUE_NAME;
    private static final String CHILD_QUEUE_ARN = "arn:aws:batch:ap-southeast-2:123456789012:job-queue/" + CHILD_QUEUE_NAME;
    private static final String DEFINITION_NAME = "download-definition";
    private static final String DEFINITION_ARN = "arn:aws:batch:ap-southeast-2:123456789012:job-definition/" + DEFINITION_NAME + ":7";
    private static final Instant NOW = Instant.parse("2026-08-24T02:00:00Z");

    @Mock
    private BatchClient batchClient;

    private final Map<String, JobDetail> describedJobs = new HashMap<>();
    private final Map<String, ListJobsResponse> listedJobs = new HashMap<>();
    private DownloadJobStatusService service;

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
        lenient().when(batchClient.listJobs(any(ListJobsRequest.class))).thenAnswer(invocation -> {
            ListJobsRequest request = invocation.getArgument(0);
            String name = request.filters().get(0).values().get(0);
            String key = name + "|" + request.nextToken();
            return listedJobs.getOrDefault(key, ListJobsResponse.builder().build());
        });

        service = new DownloadJobStatusService(
                batchClient,
                new BatchJobProperties(QUEUE_NAME, DEFINITION_NAME, CHILD_QUEUE_NAME),
                new DownloadJobStatusAggregator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void invalidJobIdIsTheSameGenericNotFoundWithoutCallingAws() {
        DownloadJobNotFoundException exception = assertThrows(
                DownloadJobNotFoundException.class, () -> service.getStatus("not-a-uuid"));

        assertEquals("Download job not found", exception.getMessage());
        verify(batchClient, never()).describeJobs(any(DescribeJobsRequest.class));
        verify(batchClient, never()).listJobs(any(ListJobsRequest.class));
    }

    @Test
    void missingOrExpiredInitialJobIsGenericNotFound() {
        DownloadJobNotFoundException exception = assertThrows(
                DownloadJobNotFoundException.class, () -> service.getStatus(JOB_ID));
        assertEquals("Download job not found", exception.getMessage());
    }

    @Test
    void acceptsQueueAndDefinitionNamesAgainstAwsArnsAndIgnoresDefinitionRevision() {
        describedJobs.put(JOB_ID, initial(JobStatus.SUCCEEDED, "one.zarr", NOW.minusSeconds(5)));

        DownloadJobStatusInfo result = service.getStatus(JOB_ID);

        assertEquals(StatusCode.SUCCESSFUL, result.getStatus());
        assertEquals(DownloadJobStatusService.PROCESS_ID, result.getProcessID());
        assertEquals(StatusInfo.TypeEnum.PROCESS, result.getType());
        assertEquals(JOB_ID, result.getJobID());
        assertEquals("Test Ocean Data Collection", result.getCollection());
        assertEquals("one.zarr", result.getDataSelection());
        assertEquals("netcdf", result.getFormat());
        assertEquals("https://portal.example.test/details/collection-id", result.getMetadataUrl());
    }

    @Test
    void optionalDisplayMetadataIsOmittedWhenItWasNotStored() {
        describedJobs.put(JOB_ID, initial(JobStatus.SUCCEEDED, "one.zarr", NOW.minusSeconds(5))
                .toBuilder()
                .parameters(Map.of("type", "sub-setting", "key", "one.zarr"))
                .build());

        DownloadJobStatusInfo result = service.getStatus(JOB_ID);

        assertEquals("one.zarr", result.getDataSelection());
        assertNull(result.getCollection());
        assertNull(result.getFormat());
        assertNull(result.getMetadataUrl());
    }

    @Test
    void configuredVersionedArnsRequireExactMatches() {
        service = new DownloadJobStatusService(
                batchClient,
                new BatchJobProperties(QUEUE_ARN, DEFINITION_ARN, CHILD_QUEUE_NAME),
                new DownloadJobStatusAggregator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        describedJobs.put(JOB_ID, initial(JobStatus.SUCCEEDED, "one.zarr", NOW.minusSeconds(5)));
        assertEquals(StatusCode.SUCCESSFUL, service.getStatus(JOB_ID).getStatus());

        describedJobs.put(JOB_ID, initial(JobStatus.SUCCEEDED, "one.zarr", NOW.minusSeconds(5))
                .toBuilder().jobDefinition(DEFINITION_ARN.replace(":7", ":8")).build());
        assertThrows(DownloadJobNotFoundException.class, () -> service.getStatus(JOB_ID));
    }

    @Test
    void rejectsJobsFromAnotherQueueDefinitionOrProcessWithTheSameNotFound() {
        List<JobDetail> invalidJobs = List.of(
                initial(JobStatus.RUNNING, "*", null).toBuilder().jobQueue("other-queue").build(),
                initial(JobStatus.RUNNING, "*", null).toBuilder().jobDefinition("other-definition:1").build(),
                initial(JobStatus.RUNNING, "*", null).toBuilder()
                        .parameters(Map.of("type", "another-process", "key", "*")).build());

        for (JobDetail invalid : invalidJobs) {
            describedJobs.put(JOB_ID, invalid);
            assertThrows(DownloadJobNotFoundException.class, () -> service.getStatus(JOB_ID));
        }
    }

    @Test
    void awsDescribeFailureBecomesGenericStatusError() {
        doThrow(new RuntimeException("credentials and internal endpoint"))
                .when(batchClient).describeJobs(any(DescribeJobsRequest.class));
        DownloadJobStatusException describeError = assertThrows(
                DownloadJobStatusException.class, () -> service.getStatus(JOB_ID));
        assertEquals("Unable to retrieve download job status", describeError.getMessage());
    }

    @Test
    void awsListFailureBecomesGenericStatusError() {
        describedJobs.put(JOB_ID, initial(JobStatus.RUNNING, "*", null));
        doThrow(new RuntimeException("secret list failure"))
                .when(batchClient).listJobs(any(ListJobsRequest.class));
        DownloadJobStatusException listError = assertThrows(
                DownloadJobStatusException.class, () -> service.getStatus(JOB_ID));
        assertEquals("Unable to retrieve download job status", listError.getMessage());
    }

    @Test
    void zeroChildrenUsesZarrAndDiscoveryWindowRules() {
        describedJobs.put(JOB_ID, initial(JobStatus.SUCCEEDED, "a.zarr, b.zarr", NOW.minusSeconds(1)));
        assertEquals(StatusCode.SUCCESSFUL, service.getStatus(JOB_ID).getStatus());

        describedJobs.put(JOB_ID, initial(JobStatus.SUCCEEDED, "*", NOW.minusSeconds(29)));
        assertEquals(StatusCode.RUNNING, service.getStatus(JOB_ID).getStatus());

        describedJobs.put(JOB_ID, initial(JobStatus.SUCCEEDED, "dataset.parquet", NOW.minusSeconds(30)));
        assertEquals(StatusCode.SUCCESSFUL, service.getStatus(JOB_ID).getStatus());
    }

    @Test
    void listJobsPaginatesAndAppliesExactCaseSensitiveNameCheck() {
        JobDetail initial = initial(JobStatus.SUCCEEDED, "dataset.parquet", NOW.minusSeconds(5));
        describedJobs.put(JOB_ID, initial);
        String prepareName = prepareName();
        String prepareId = "223e4567-e89b-12d3-a456-426614174000";
        describedJobs.put(prepareId, child(prepareId, prepareName, "sub-setting-data-preparation", JobStatus.PENDING));

        listedJobs.put(prepareName + "|null", listResponse("page-2",
                summary("323e4567-e89b-12d3-a456-426614174000", prepareName.toUpperCase())));
        listedJobs.put(prepareName + "|page-2", listResponse(null, summary(prepareId, prepareName)));

        StatusInfo result = service.getStatus(JOB_ID);

        assertEquals(StatusCode.RUNNING, result.getStatus());
        ArgumentCaptor<ListJobsRequest> captor = ArgumentCaptor.forClass(ListJobsRequest.class);
        verify(batchClient, org.mockito.Mockito.atLeast(3)).listJobs(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(request -> "page-2".equals(request.nextToken())));
    }

    @Test
    void duplicateSummariesAreDeduplicatedAndInvalidCandidatesAreDiscarded() {
        describedJobs.put(JOB_ID, initial(JobStatus.SUCCEEDED, "*", NOW.minusSeconds(5)));
        String prepareName = prepareName();
        String validId = "223e4567-e89b-12d3-a456-426614174000";
        String wrongMasterId = "323e4567-e89b-12d3-a456-426614174000";
        String wrongTypeId = "423e4567-e89b-12d3-a456-426614174000";
        describedJobs.put(validId, child(validId, prepareName, "sub-setting-data-preparation", JobStatus.RUNNING));
        describedJobs.put(wrongMasterId, child(wrongMasterId, prepareName, "sub-setting-data-preparation", JobStatus.RUNNING)
                .toBuilder().parameters(Map.of("master_job_id", "another-job", "type", "sub-setting-data-preparation")).build());
        describedJobs.put(wrongTypeId, child(wrongTypeId, prepareName, "wrong-type", JobStatus.RUNNING));
        listedJobs.put(prepareName + "|null", listResponse(null,
                summary(validId, prepareName),
                summary(validId, prepareName),
                summary(wrongMasterId, prepareName),
                summary(wrongTypeId, prepareName)));

        assertEquals(StatusCode.RUNNING, service.getStatus(JOB_ID).getStatus());
    }

    @Test
    void multipleDistinctValidCandidatesAreAmbiguous() {
        describedJobs.put(JOB_ID, initial(JobStatus.SUCCEEDED, "*", NOW.minusSeconds(5)));
        String prepareName = prepareName();
        String first = "223e4567-e89b-12d3-a456-426614174000";
        String second = "323e4567-e89b-12d3-a456-426614174000";
        describedJobs.put(first, child(first, prepareName, "sub-setting-data-preparation", JobStatus.RUNNING));
        describedJobs.put(second, child(second, prepareName, "sub-setting-data-preparation", JobStatus.RUNNING));
        listedJobs.put(prepareName + "|null", listResponse(null,
                summary(first, prepareName), summary(second, prepareName)));

        DownloadJobStatusException exception = assertThrows(
                DownloadJobStatusException.class, () -> service.getStatus(JOB_ID));
        assertEquals("Unable to retrieve download job status", exception.getMessage());
    }

    @Test
    void usesConfiguredChildQueueAndBuildsTerminalDatesWithoutSensitiveFields() {
        long createdAt = NOW.minusSeconds(100).toEpochMilli();
        JobDetail initial = initial(JobStatus.SUCCEEDED, "dataset.parquet", NOW.minusSeconds(60))
                .toBuilder().createdAt(createdAt).startedAt(NOW.minusSeconds(90).toEpochMilli()).build();
        describedJobs.put(JOB_ID, initial);

        String prepareId = "223e4567-e89b-12d3-a456-426614174000";
        String collectId = "323e4567-e89b-12d3-a456-426614174000";
        JobDetail prepare = child(prepareId, prepareName(), "sub-setting-data-preparation", JobStatus.SUCCEEDED)
                .toBuilder().startedAt(NOW.minusSeconds(50).toEpochMilli()).stoppedAt(NOW.minusSeconds(40).toEpochMilli()).build();
        JobDetail collect = child(collectId, collectName(), "sub-setting-data-collection", JobStatus.SUCCEEDED)
                .toBuilder().startedAt(NOW.minusSeconds(30).toEpochMilli()).stoppedAt(NOW.minusSeconds(10).toEpochMilli())
                .statusReason("s3://private-bucket/internal-key").build();
        describedJobs.put(prepareId, prepare);
        describedJobs.put(collectId, collect);
        listedJobs.put(prepareName() + "|null", listResponse(null, summary(prepareId, prepareName())));
        listedJobs.put(collectName() + "|null", listResponse(null, summary(collectId, collectName())));

        StatusInfo result = service.getStatus(JOB_ID);

        assertEquals(StatusCode.SUCCESSFUL, result.getStatus());
        assertEquals(new Date(createdAt), result.getCreated());
        assertEquals(new Date(NOW.minusSeconds(90).toEpochMilli()), result.getStarted());
        assertEquals(new Date(NOW.minusSeconds(10).toEpochMilli()), result.getFinished());
        assertNull(result.getProgress());
        assertNull(result.getUpdated());

        ArgumentCaptor<ListJobsRequest> captor = ArgumentCaptor.forClass(ListJobsRequest.class);
        verify(batchClient, org.mockito.Mockito.atLeast(2)).listJobs(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(request -> CHILD_QUEUE_NAME.equals(request.jobQueue())));
    }

    @Test
    void queueAndDefinitionNormalizersHandleNamesAndArns() {
        assertTrue(DownloadJobStatusService.matchesQueue(QUEUE_NAME, QUEUE_ARN));
        assertTrue(DownloadJobStatusService.matchesQueue(QUEUE_ARN, QUEUE_ARN));
        assertTrue(DownloadJobStatusService.matchesJobDefinition(DEFINITION_NAME, DEFINITION_ARN));
        assertTrue(DownloadJobStatusService.matchesJobDefinition(DEFINITION_NAME + ":3", DEFINITION_ARN));
        assertTrue(DownloadJobStatusService.matchesJobDefinition(DEFINITION_ARN, DEFINITION_ARN));
    }

    private JobDetail initial(JobStatus status, String key, Instant stoppedAt) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("type", "sub-setting");
        parameters.put("collection_title", "Test Ocean Data Collection");
        parameters.put("output_format", "netcdf");
        parameters.put("full_metadata_link", "https://portal.example.test/details/collection-id");
        if (key != null) {
            parameters.put("key", key);
        }
        return JobDetail.builder()
                .jobId(JOB_ID)
                .jobName("initial")
                .jobQueue(QUEUE_ARN)
                .jobDefinition(DEFINITION_ARN)
                .status(status)
                .parameters(parameters)
                .createdAt(NOW.minusSeconds(120).toEpochMilli())
                .stoppedAt(stoppedAt == null ? null : stoppedAt.toEpochMilli())
                .build();
    }

    private JobDetail child(String id, String name, String type, JobStatus status) {
        return JobDetail.builder()
                .jobId(id)
                .jobName(name)
                .jobQueue(CHILD_QUEUE_ARN)
                .status(status)
                .parameters(Map.of("master_job_id", JOB_ID, "type", type))
                .build();
    }

    private JobSummary summary(String id, String name) {
        return JobSummary.builder().jobId(id).jobName(name).build();
    }

    private ListJobsResponse listResponse(String nextToken, JobSummary... jobs) {
        return ListJobsResponse.builder().jobSummaryList(jobs).nextToken(nextToken).build();
    }

    private String prepareName() {
        return "prepare-data-for-job-" + JOB_ID;
    }

    private String collectName() {
        return "collect-data-for-job-" + JOB_ID;
    }
}
