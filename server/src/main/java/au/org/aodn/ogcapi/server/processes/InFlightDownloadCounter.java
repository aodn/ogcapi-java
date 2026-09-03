package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.DescribeJobsRequest;
import software.amazon.awssdk.services.batch.model.JobDetail;
import software.amazon.awssdk.services.batch.model.JobStatus;
import software.amazon.awssdk.services.batch.model.JobSummary;
import software.amazon.awssdk.services.batch.model.ListJobsRequest;
import software.amazon.awssdk.services.batch.model.ListJobsResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts how many downloads each user has in flight, from one sweep of the AWS Batch queues
 * that every user shares rather than a query per request.
 *
 * <p>A download occupies a slot while its aggregated status is {@code accepted} or
 * {@code running}. In every case {@link DownloadJobStatusAggregator} produces, that is
 * equivalent to "the master job, or one of its prepare/collect children, is in a non-terminal
 * Batch status", with one gap: a master that has succeeded before its children appear is in
 * neither sweep yet still aggregates to running. Jobs submitted within {@link #SUBMIT_GRACE}
 * are therefore counted from memory whatever the sweep saw, which also covers everything
 * submitted since the last sweep.
 *
 * <p>The owning email is read back from the {@code recipient} job parameter, never from the
 * job name: {@link RestServices#downloadJobName(String)} sanitises the address, so two
 * different addresses can produce the same name.
 */
@Slf4j
@Service
public class InFlightDownloadCounter {

    private static final List<JobStatus> NON_TERMINAL = List.of(
            JobStatus.SUBMITTED,
            JobStatus.PENDING,
            JobStatus.RUNNABLE,
            JobStatus.STARTING,
            JobStatus.RUNNING);

    /**
     * How long a freshly submitted job keeps counting from memory. It has to outlast the
     * child discovery window of the status service, which is exactly the period in which a
     * succeeded master with no children yet still aggregates to running.
     */
    static final Duration SUBMIT_GRACE = DownloadJobStatusService.CHILD_DISCOVERY_WINDOW.plusSeconds(90);

    private static final int PAGE_SIZE = 100;

    private final BatchClient batchClient;
    private final BatchJobProperties properties;
    private final DownloadLimitProperties limits;
    private final Clock clock;

    /** AWS job id to the submission that produced it, retained for {@link #SUBMIT_GRACE}. */
    private final Map<String, Submission> recentSubmissions = new ConcurrentHashMap<>();

    private volatile Snapshot snapshot = Snapshot.empty();

    @Autowired
    public InFlightDownloadCounter(
            BatchClient batchClient,
            BatchJobProperties properties,
            DownloadLimitProperties limits) {
        this(batchClient, properties, limits, Clock.systemUTC());
    }

    InFlightDownloadCounter(
            BatchClient batchClient,
            BatchJobProperties properties,
            DownloadLimitProperties limits,
            Clock clock) {
        this.batchClient = batchClient;
        this.properties = properties;
        this.limits = limits;
        this.clock = clock;
    }

    private record Submission(String recipient, Instant submittedAt) {
    }

    /**
     * @param countsByRecipient in-flight downloads per recipient email
     * @param countedMasterIds  the master job ids behind those counts, so a job the sweep
     *                          already counted is not counted again from recent submissions
     */
    private record Snapshot(Map<String, Integer> countsByRecipient, Set<String> countedMasterIds, Instant takenAt) {
        static Snapshot empty() {
            return new Snapshot(Map.of(), Set.of(), Instant.EPOCH);
        }
    }

    /**
     * Refresh the shared snapshot if it has aged past the release interval. Call this before
     * taking any admission lock: it is the only part of counting that talks to AWS.
     *
     * <p>Synchronized so that when several callers arrive after the snapshot has expired, only
     * the first actually sweeps; the rest block briefly on this monitor and then see the fresh
     * snapshot the first caller just took, rather than each repeating the sweep themselves.
     */
    public synchronized void refreshIfStale() {
        if (isStale()) {
            refresh();
        }
    }

    private boolean isStale() {
        return Duration.between(snapshot.takenAt(), clock.instant()).compareTo(limits.refreshInterval()) >= 0;
    }

    /** Sweep the queues now, whatever the age of the current snapshot. */
    public synchronized void refresh() {
        try {
            snapshot = sweep();
        } catch (Exception e) {
            // Keep serving the previous snapshot. A failed sweep must not fail the download
            // request that triggered it; a stale count at worst admits a job that should have
            // been held, and the next successful sweep corrects it.
            log.error("Failed to sweep AWS Batch for in-flight downloads, reusing the previous snapshot", e);
        }
    }

    /**
     * In-flight downloads for one recipient: what the last sweep saw, plus anything submitted
     * too recently for that sweep to have picked it up.
     */
    public int countInFlight(String recipient) {
        pruneRecentSubmissions();
        String key = recipientKey(recipient);
        Snapshot current = snapshot;
        int count = current.countsByRecipient().getOrDefault(key, 0);
        for (Map.Entry<String, Submission> entry : recentSubmissions.entrySet()) {
            if (entry.getValue().recipient().equals(key)
                    && !current.countedMasterIds().contains(entry.getKey())) {
                count++;
            }
        }
        return count;
    }

    /** Record a job we just submitted so it counts immediately, before any sweep can see it. */
    public void recordSubmitted(String awsJobId, String recipient) {
        recentSubmissions.put(awsJobId, new Submission(recipientKey(recipient), clock.instant()));
    }

    /**
     * The key one user is counted under. Email addresses are case-insensitive in the part
     * that matters here and arrive however the user typed them, so without this a capital
     * letter would silently buy a second allowance of slots.
     *
     * <p>Only ever a counting key. The address SES writes to, and the {@code recipient} job
     * parameter data-access-service reads, stay exactly as the user supplied them.
     */
    static String recipientKey(String recipient) {
        return recipient == null ? null : recipient.trim().toLowerCase(Locale.ROOT);
    }

    private void pruneRecentSubmissions() {
        Instant cutoff = clock.instant().minus(SUBMIT_GRACE);
        recentSubmissions.entrySet().removeIf(entry -> entry.getValue().submittedAt().isBefore(cutoff));
    }

    private Snapshot sweep() {
        // The download queue and the child queue are the same by default, so sweep each
        // distinct queue once rather than once per role.
        Set<String> queues = new LinkedHashSet<>();
        queues.add(properties.queue());
        queues.add(properties.childQueue());

        Set<String> candidateMasterIds = new LinkedHashSet<>();
        for (String queue : queues) {
            for (JobSummary summary : listNonTerminal(queue)) {
                if (queue.equals(properties.queue()) && summary.jobId() != null && !summary.jobId().isBlank()) {
                    // Anything non-terminal on the download queue is a candidate master. The
                    // describe below discards whatever turns out not to be one of ours.
                    candidateMasterIds.add(summary.jobId());
                }
                String masterId = masterIdOf(summary.jobName());
                if (masterId != null) {
                    candidateMasterIds.add(masterId);
                }
            }
        }

        Map<String, Integer> counts = new HashMap<>();
        Set<String> counted = new LinkedHashSet<>();
        for (JobDetail job : describeJobs(candidateMasterIds)) {
            if (!isDownloadMaster(job)) {
                continue;
            }
            String recipient = job.parameters().get(DatasetDownloadEnums.Parameter.RECIPIENT.getValue());
            if (recipient == null || recipient.isBlank()) {
                continue;
            }
            counts.merge(recipientKey(recipient), 1, Integer::sum);
            counted.add(job.jobId());
        }
        return new Snapshot(counts, counted, clock.instant());
    }

    /**
     * The master job id a prepare/collect child belongs to, or null when this is not one of
     * the data-access-service child jobs. The names are the same contract
     * {@link DownloadJobStatusService} relies on.
     */
    static String masterIdOf(String jobName) {
        if (jobName == null) {
            return null;
        }
        if (jobName.startsWith(DownloadJobStatusService.PREPARE_NAME_PREFIX)) {
            return blankToNull(jobName.substring(DownloadJobStatusService.PREPARE_NAME_PREFIX.length()));
        }
        if (jobName.startsWith(DownloadJobStatusService.COLLECT_NAME_PREFIX)) {
            return blankToNull(jobName.substring(DownloadJobStatusService.COLLECT_NAME_PREFIX.length()));
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private boolean isDownloadMaster(JobDetail job) {
        return DownloadJobStatusService.matchesQueue(properties.queue(), job.jobQueue())
                && DownloadJobStatusService.matchesJobDefinition(properties.definition(), job.jobDefinition())
                && DatasetDownloadEnums.Type.SUB_SETTING.getValue()
                .equals(job.parameters().get(DatasetDownloadEnums.Parameter.TYPE.getValue()));
    }

    /**
     * Every non-terminal job on a queue. ListJobs returns only RUNNABLE jobs when given
     * neither a filter nor a status, so the statuses are enumerated explicitly.
     */
    private List<JobSummary> listNonTerminal(String queue) {
        List<JobSummary> result = new ArrayList<>();
        for (JobStatus status : NON_TERMINAL) {
            String nextToken = null;
            do {
                ListJobsResponse response = batchClient.listJobs(ListJobsRequest.builder()
                        .jobQueue(queue)
                        .jobStatus(status)
                        .maxResults(PAGE_SIZE)
                        .nextToken(nextToken)
                        .build());
                result.addAll(response.jobSummaryList());
                nextToken = response.nextToken();
            } while (nextToken != null);
        }
        return result;
    }

    private List<JobDetail> describeJobs(Set<String> jobIds) {
        List<String> ids = new ArrayList<>(jobIds);
        List<JobDetail> result = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += PAGE_SIZE) {
            int end = Math.min(start + PAGE_SIZE, ids.size());
            result.addAll(batchClient.describeJobs(DescribeJobsRequest.builder()
                    .jobs(ids.subList(start, end))
                    .build()).jobs());
        }
        return result;
    }
}
