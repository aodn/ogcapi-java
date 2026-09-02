package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.processes.model.StatusCode;
import au.org.aodn.ogcapi.processes.model.StatusInfo;
import au.org.aodn.ogcapi.server.core.exception.DownloadJobNotFoundException;
import au.org.aodn.ogcapi.server.core.exception.DownloadJobStatusException;
import au.org.aodn.ogcapi.server.core.model.DownloadJobStatusInfo;
import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.DescribeJobsRequest;
import software.amazon.awssdk.services.batch.model.JobDetail;
import software.amazon.awssdk.services.batch.model.JobStatus;
import software.amazon.awssdk.services.batch.model.KeyValuesPair;
import software.amazon.awssdk.services.batch.model.ListJobsRequest;
import software.amazon.awssdk.services.batch.model.ListJobsResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DownloadJobStatusService {

    static final String PROCESS_ID = "download-dataset";
    static final Duration CHILD_DISCOVERY_WINDOW = Duration.ofSeconds(30);
    // These exact names are an internal contract with data-access-service. Any DAS
    // naming change must be applied here at the same time.
    static final String PREPARE_NAME_PREFIX = "prepare-data-for-job-";
    static final String COLLECT_NAME_PREFIX = "collect-data-for-job-";

    private static final String INITIAL_TYPE = "sub-setting";
    private static final String PREPARE_TYPE = "sub-setting-data-preparation";
    private static final String COLLECT_TYPE = "sub-setting-data-collection";
    private static final String MASTER_JOB_ID = "master_job_id";
    private static final int LIST_PAGE_SIZE = 100;
    private static final int DESCRIBE_PAGE_SIZE = 100;

    private final BatchClient batchClient;
    private final BatchJobProperties properties;
    private final DownloadJobStatusAggregator aggregator;
    private final Clock clock;

    @Autowired
    public DownloadJobStatusService(
            BatchClient batchClient,
            BatchJobProperties properties,
            DownloadJobStatusAggregator aggregator) {
        this(batchClient, properties, aggregator, Clock.systemUTC());
    }

    DownloadJobStatusService(
            BatchClient batchClient,
            BatchJobProperties properties,
            DownloadJobStatusAggregator aggregator,
            Clock clock) {
        this.batchClient = batchClient;
        this.properties = properties;
        this.aggregator = aggregator;
        this.clock = clock;
    }

    public DownloadJobStatusInfo getStatus(String jobId) {
        validateJobId(jobId);
        try {
            JobDetail initial = describeInitialJob(jobId);

            if (initial.status() == JobStatus.FAILED) {
                StatusCode status = aggregator.aggregate(new DownloadJobStatusAggregator.Snapshot(
                        initial.status(), null, null,
                        DownloadJobStatusAggregator.WorkflowMode.CHILD_DISCOVERY_REQUIRED,
                        false));
                return toStatusInfo(jobId, status, initial, null, null);
            }

            JobDetail prepare = findChildJob(
                    PREPARE_NAME_PREFIX + jobId, jobId, PREPARE_TYPE);
            JobDetail collect = findChildJob(
                    COLLECT_NAME_PREFIX + jobId, jobId, COLLECT_TYPE);

            boolean discoveryWindowExpired = discoveryWindowExpired(initial);
            DownloadJobStatusAggregator.WorkflowMode workflowMode = isExplicitZarr(initial.parameters())
                    ? DownloadJobStatusAggregator.WorkflowMode.EXPLICIT_ZARR
                    : DownloadJobStatusAggregator.WorkflowMode.CHILD_DISCOVERY_REQUIRED;

            StatusCode status = aggregator.aggregate(new DownloadJobStatusAggregator.Snapshot(
                    initial.status(),
                    statusOf(prepare),
                    statusOf(collect),
                    workflowMode,
                    discoveryWindowExpired));

            return toStatusInfo(jobId, status, initial, prepare, collect);
        } catch (DownloadJobNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to reconstruct AWS Batch workflow for download job {}", jobId, e);
            throw new DownloadJobStatusException(e);
        }
    }

    private void validateJobId(String jobId) {
        try {
            UUID parsed = UUID.fromString(jobId);
            if (!parsed.toString().equalsIgnoreCase(jobId)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
        } catch (Exception e) {
            throw new DownloadJobNotFoundException();
        }
    }

    private JobDetail describeInitialJob(String jobId) {
        List<JobDetail> jobs = batchClient.describeJobs(DescribeJobsRequest.builder().jobs(jobId).build()).jobs();
        if (jobs.size() != 1) {
            throw new DownloadJobNotFoundException();
        }

        JobDetail job = jobs.get(0);
        if (!jobId.equalsIgnoreCase(job.jobId())
                || !matchesQueue(properties.queue(), job.jobQueue())
                || !matchesJobDefinition(properties.definition(), job.jobDefinition())
                || !INITIAL_TYPE.equals(job.parameters().get(DatasetDownloadEnums.Parameter.TYPE.getValue()))) {
            throw new DownloadJobNotFoundException();
        }
        return job;
    }

    private JobDetail findChildJob(String exactJobName, String masterJobId, String expectedType) {
        Set<String> candidateIds = listCandidateIds(exactJobName);
        if (candidateIds.isEmpty()) {
            return null;
        }

        List<JobDetail> valid = new ArrayList<>(describeJobs(candidateIds).stream()
                .filter(job -> exactJobName.equals(job.jobName()))
                .filter(job -> matchesQueue(properties.childQueue(), job.jobQueue()))
                .filter(job -> masterJobId.equals(job.parameters().get(MASTER_JOB_ID)))
                .filter(job -> expectedType.equals(job.parameters().get(DatasetDownloadEnums.Parameter.TYPE.getValue())))
                .collect(Collectors.toMap(
                        JobDetail::jobId,
                        Function.identity(),
                        (first, duplicate) -> first,
                        LinkedHashMap::new))
                .values());

        if (valid.size() > 1) {
            log.error("Ambiguous child workflow job {}: valid AWS job ids {}", exactJobName,
                    valid.stream().map(JobDetail::jobId).toList());
            throw new DownloadJobStatusException();
        }
        return valid.isEmpty() ? null : valid.get(0);
    }

    private Set<String> listCandidateIds(String exactJobName) {
        Set<String> result = new LinkedHashSet<>();
        String nextToken = null;
        do {
            ListJobsRequest request = ListJobsRequest.builder()
                    .jobQueue(properties.childQueue())
                    .filters(KeyValuesPair.builder().name("JOB_NAME").values(exactJobName).build())
                    .maxResults(LIST_PAGE_SIZE)
                    .nextToken(nextToken)
                    .build();
            ListJobsResponse response = batchClient.listJobs(request);
            response.jobSummaryList().stream()
                    .filter(summary -> exactJobName.equals(summary.jobName()))
                    .map(summary -> summary.jobId())
                    .filter(id -> id != null && !id.isBlank())
                    .forEach(result::add);
            nextToken = response.nextToken();
        } while (nextToken != null);
        return result;
    }

    private List<JobDetail> describeJobs(Set<String> candidateIds) {
        List<String> ids = new ArrayList<>(candidateIds);
        List<JobDetail> result = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += DESCRIBE_PAGE_SIZE) {
            int end = Math.min(start + DESCRIBE_PAGE_SIZE, ids.size());
            result.addAll(batchClient.describeJobs(DescribeJobsRequest.builder()
                    .jobs(ids.subList(start, end))
                    .build()).jobs());
        }
        return result;
    }

    private boolean discoveryWindowExpired(JobDetail initial) {
        Long stoppedAt = initial.stoppedAt();
        return stoppedAt != null && stoppedAt > 0
                && !clock.instant().isBefore(Instant.ofEpochMilli(stoppedAt).plus(CHILD_DISCOVERY_WINDOW));
    }

    private boolean isExplicitZarr(Map<String, String> parameters) {
        String keys = parameters.get(DatasetDownloadEnums.Parameter.KEY.getValue());
        if (keys == null || keys.isBlank()) {
            return false;
        }
        String[] splitKeys = keys.split(",", -1);
        for (String key : splitKeys) {
            String trimmed = key.trim();
            if (trimmed.isEmpty() || !trimmed.endsWith(".zarr")) {
                return false;
            }
        }
        return true;
    }

    private DownloadJobStatusInfo toStatusInfo(
            String jobId,
            StatusCode status,
            JobDetail initial,
            JobDetail prepare,
            JobDetail collect) {
        DownloadJobStatusInfo result = new DownloadJobStatusInfo();
        result.setProcessID(PROCESS_ID);
        result.setType(StatusInfo.TypeEnum.PROCESS);
        result.setJobID(jobId);
        result.setStatus(status);
        result.setMessage(messageFor(status));

        Map<String, String> parameters = initial.parameters();
        result.setCollection(nonBlank(parameters.get(DatasetDownloadEnums.Parameter.COLLECTION_TITLE.getValue())));
        result.setDataSelection(nonBlank(parameters.get(DatasetDownloadEnums.Parameter.KEY.getValue())));
        result.setFormat(nonBlank(parameters.get(DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getValue())));
        result.setMetadataUrl(nonBlank(parameters.get(DatasetDownloadEnums.Parameter.FULL_METADATA_LINK.getValue())));
        result.setCreated(toDate(initial.createdAt()));
        result.setStarted(firstStartedAt(initial, prepare, collect));
        result.setFinished(finishedAt(status, initial, prepare, collect));
        return result;
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Date firstStartedAt(JobDetail... jobs) {
        Long first = null;
        for (JobDetail job : jobs) {
            if (job != null && job.startedAt() != null && job.startedAt() > 0
                    && (first == null || job.startedAt() < first)) {
                first = job.startedAt();
            }
        }
        return toDate(first);
    }

    private Date finishedAt(StatusCode status, JobDetail initial, JobDetail prepare, JobDetail collect) {
        if (status == StatusCode.SUCCESSFUL) {
            return toDate(collect != null && collect.status() == JobStatus.SUCCEEDED
                    ? collect.stoppedAt()
                    : initial.stoppedAt());
        }
        if (status == StatusCode.FAILED) {
            for (JobDetail job : new JobDetail[]{initial, prepare, collect}) {
                if (job != null && job.status() == JobStatus.FAILED) {
                    return toDate(job.stoppedAt());
                }
            }
        }
        return null;
    }

    private Date toDate(Long epochMillis) {
        return epochMillis == null || epochMillis <= 0 ? null : new Date(epochMillis);
    }

    private JobStatus statusOf(JobDetail job) {
        return job == null ? null : job.status();
    }

    private String messageFor(StatusCode status) {
        return switch (status) {
            case ACCEPTED -> "Download job accepted";
            case RUNNING -> "Download job is running";
            case SUCCESSFUL -> "Download job completed successfully";
            case FAILED -> "Download job failed";
            case DISMISSED -> "Download job dismissed";
        };
    }

    static boolean matchesQueue(String configured, String actual) {
        if (configured == null || actual == null) {
            return false;
        }
        if (configured.startsWith("arn:")) {
            return configured.equals(actual);
        }
        return configured.equals(resourceName(actual, "job-queue/"));
    }

    static boolean matchesJobDefinition(String configured, String actual) {
        if (configured == null || actual == null) {
            return false;
        }
        if (configured.startsWith("arn:")) {
            return configured.equals(actual);
        }
        return withoutRevision(configured).equals(withoutRevision(resourceName(actual, "job-definition/")));
    }

    private static String resourceName(String value, String marker) {
        int markerIndex = value.indexOf(marker);
        return markerIndex >= 0 ? value.substring(markerIndex + marker.length()) : value;
    }

    private static String withoutRevision(String value) {
        int revision = value.lastIndexOf(':');
        return revision >= 0 ? value.substring(0, revision) : value;
    }
}
