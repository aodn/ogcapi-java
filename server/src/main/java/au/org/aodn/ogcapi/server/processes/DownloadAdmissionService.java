package au.org.aodn.ogcapi.server.processes;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Caps how many downloads one user can have running at once. A user at the limit is not
 * rejected: the request is held here and submitted to AWS Batch as soon as one of that
 * user's slots frees, so the caller still gets a job id it can poll.
 *
 * <p>The hold queue is in memory only. A restart therefore loses whatever was waiting, and a
 * second replica would enforce its own limit rather than a shared one.
 */
@Slf4j
@Service
public class DownloadAdmissionService {

    static final String QUEUED_MESSAGE = "Download job queued, waiting for a free slot";

    private static final int MAX_SUBMIT_ATTEMPTS = 3;

    /**
     * How long the id of a released download keeps resolving to its AWS job. AWS Batch drops
     * the job record itself after about a day, so a longer retention would only translate an
     * id into a job that no longer exists.
     */
    static final Duration RELEASED_RETENTION = Duration.ofHours(24);

    private final RestServices restServices;
    private final InFlightDownloadCounter counter;
    private final DownloadLimitProperties limits;
    private final Clock clock;

    /**
     * Guards the admit-or-hold decision and the release loop against each other. The AWS
     * sweep is deliberately done before this is taken; the submit itself is inside it, so
     * that reading the count and acting on it cannot interleave and over-admit.
     */
    private final ReentrantLock lock = new ReentrantLock();

    /** FIFO across all users. Guarded by {@link #lock}. */
    private final Deque<HeldDownload> held = new ArrayDeque<>();

    private final Map<String, HeldDownload> heldById = new ConcurrentHashMap<>();
    private final Map<String, Released> released = new ConcurrentHashMap<>();

    record Released(String awsJobId, Instant releasedAt) {
    }

    @Autowired
    public DownloadAdmissionService(
            RestServices restServices,
            InFlightDownloadCounter counter,
            DownloadLimitProperties limits) {
        this(restServices, counter, limits, Clock.systemUTC());
    }

    DownloadAdmissionService(
            RestServices restServices,
            InFlightDownloadCounter counter,
            DownloadLimitProperties limits,
            Clock clock) {
        this.restServices = restServices;
        this.counter = counter;
        this.limits = limits;
        this.clock = clock;
    }

    /**
     * Accept a download. Returns the AWS Batch job id when it was submitted straight away, or
     * a locally minted id when the user was at their limit and the request is now waiting.
     */
    public DownloadAdmission submitOrHold(DownloadRequest request) throws JsonProcessingException {
        Map<String, String> parameters = restServices.buildDownloadParameters(request);
        String jobName = RestServices.downloadJobName(request.recipient());

        if (!limits.enabled()) {
            String awsJobId = restServices.submitDownloadJob(jobName, parameters);
            notifyStarted(request);
            return DownloadAdmission.submitted(awsJobId);
        }

        // Outside the lock: the sweep is the only expensive step and every user shares it.
        counter.refreshIfStale();

        DownloadAdmission admission;
        lock.lock();
        try {
            if (hasHeldFor(request.recipient())) {
                // Never jump ahead of this user's own waiting requests.
                admission = hold(request, jobName, parameters);
            } else if (counter.countInFlight(request.recipient()) < limits.maxConcurrent()) {
                admission = DownloadAdmission.submitted(submit(jobName, parameters, request.recipient()));
            } else {
                admission = hold(request, jobName, parameters);
            }
        } finally {
            lock.unlock();
        }

        if (!admission.queued()) {
            // Outside the lock: this is a synchronous SES call, and it is best effort anyway.
            notifyStarted(request);
        }
        return admission;
    }

    /**
     * The still-waiting download with this id and where it sits in its owner's queue, or null
     * if this id is not one of ours. Both come from one look under the lock so the position
     * cannot be taken from a queue that has already moved on.
     */
    public HeldView findHeld(String jobId) {
        lock.lock();
        try {
            HeldDownload download = heldById.get(jobId);
            return download == null ? null : new HeldView(download, positionOf(download));
        } finally {
            lock.unlock();
        }
    }

    /** A waiting download together with its place in its owner's queue. */
    public record HeldView(HeldDownload download, int position) {
    }

    /**
     * How many of this user's waiting downloads, counting this one, sit at or ahead of it.
     * Only that user's own queue matters: their downloads are released as their own slots
     * free, so what is waiting for other people says nothing about this one.
     */
    private int positionOf(HeldDownload target) {
        String key = InFlightDownloadCounter.recipientKey(target.request().recipient());
        int position = 0;
        for (HeldDownload job : held) {
            if (InFlightDownloadCounter.recipientKey(job.request().recipient()).equals(key)) {
                position++;
                if (job.jobId().equals(target.jobId())) {
                    return position;
                }
            }
        }
        return position;
    }

    /** The AWS Batch job a released download became, or null if this id was never held. */
    public String awsJobIdOf(String jobId) {
        Released record = released.get(jobId);
        return record == null ? null : record.awsJobId();
    }

    /**
     * Submit whatever is waiting and now fits. Nothing held means no AWS call at all, which
     * is both the steady state and what keeps the scheduler away from live AWS Batch in tests.
     */
    @Scheduled(fixedDelayString = "${aws.batch.job.user-limit.release-interval:15s}")
    public void releaseHeldDownloads() {
        pruneReleased();
        if (!limits.enabled() || heldById.isEmpty()) {
            return;
        }

        counter.refresh();

        List<DownloadRequest> toNotify = new ArrayList<>();
        lock.lock();
        try {
            expireStale();

            Deque<HeldDownload> blocked = new ArrayDeque<>();
            HeldDownload job;
            while ((job = held.pollFirst()) != null) {
                String recipient = job.request().recipient();
                if (counter.countInFlight(recipient) >= limits.maxConcurrent()) {
                    blocked.addLast(job);
                    continue;
                }
                try {
                    String awsJobId = submit(job.jobName(), job.parameters(), recipient);
                    heldById.remove(job.jobId());
                    released.put(job.jobId(), new Released(awsJobId, clock.instant()));
                    toNotify.add(job.request());
                    log.info("Released held download {} as AWS Batch job {}", job.jobId(), awsJobId);
                } catch (Exception e) {
                    HeldDownload retried = job.withAttempt();
                    if (retried.attempts() >= MAX_SUBMIT_ATTEMPTS) {
                        heldById.remove(job.jobId());
                        log.error("Abandoning held download {} after {} failed submissions",
                                job.jobId(), retried.attempts(), e);
                    } else {
                        log.warn("Could not release held download {}, will retry", job.jobId(), e);
                        heldById.put(retried.jobId(), retried);
                        blocked.addLast(retried);
                    }
                }
            }
            held.addAll(blocked);
        } finally {
            lock.unlock();
        }

        toNotify.forEach(this::notifyStarted);
    }

    private String submit(String jobName, Map<String, String> parameters, String recipient) {
        String awsJobId = restServices.submitDownloadJob(jobName, parameters);
        counter.recordSubmitted(awsJobId, recipient);
        return awsJobId;
    }

    private boolean hasHeldFor(String recipient) {
        String key = InFlightDownloadCounter.recipientKey(recipient);
        return held.stream()
                .anyMatch(job -> InFlightDownloadCounter.recipientKey(job.request().recipient()).equals(key));
    }

    private DownloadAdmission hold(DownloadRequest request, String jobName, Map<String, String> parameters) {
        if (held.size() >= limits.maxHeldTotal()) {
            // A safety valve, not an expected outcome: the alternative is growing the queue
            // until the process runs out of memory.
            throw new IllegalStateException(
                    "Download hold queue is full (" + limits.maxHeldTotal() + " waiting)");
        }
        String jobId = UUID.randomUUID().toString();
        HeldDownload job = new HeldDownload(jobId, request, jobName, parameters, clock.instant(), 0);
        held.addLast(job);
        heldById.put(jobId, job);
        log.info("Holding download {} for a free slot, {} now waiting", jobId, held.size());
        return DownloadAdmission.queued(jobId, positionOf(job));
    }

    private void expireStale() {
        Instant cutoff = clock.instant().minus(limits.maxHoldAge());
        held.removeIf(job -> {
            if (job.acceptedAt().isBefore(cutoff)) {
                heldById.remove(job.jobId());
                log.warn("Abandoning download {} held since {}", job.jobId(), job.acceptedAt());
                return true;
            }
            return false;
        });
    }

    private void pruneReleased() {
        Instant cutoff = clock.instant().minus(RELEASED_RETENTION);
        released.entrySet().removeIf(entry -> entry.getValue().releasedAt().isBefore(cutoff));
    }

    private void notifyStarted(DownloadRequest request) {
        restServices.notifyUser(
                request.recipient(),
                request.uuid(),
                request.key(),
                request.startDate(),
                request.endDate(),
                request.multiPolygon(),
                request.collectionTitle(),
                request.fullMetadataLink(),
                request.suggestedCitation(),
                request.outputFormat());
    }
}
