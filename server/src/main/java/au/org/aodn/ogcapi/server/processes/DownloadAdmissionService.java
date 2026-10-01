package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.exception.DownloadLimitExceededException;
import au.org.aodn.ogcapi.server.core.exception.DownloadSizeExceededException;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Decides whether a download can be submitted. Two checks run before anything goes to AWS Batch:
 * 1. Size: a download estimated at the size limit or more is rejected, the user has to reduce
 * the subset.
 * 2. Concurrency: a user already at the limit is rejected outright, the caller has to wait for
 * one of their own downloads to finish and try again.
 */
@Slf4j
@Service
public class DownloadAdmissionService {

    private final RestServices restServices;
    private final InFlightDownloadCounter counter;
    private final DownloadLimitProperties limits;
    private final DownloadSizeLimitProperties sizeLimit;

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Recipients with a submit currently in flight, not yet reflected in {@link #counter}
     * because the AWS call it is waiting on has not returned. Consulted together with the
     * counter whenever admission checks whether a recipient has room - without it, two
     * requests racing during the same in-flight submit could both see room and both go
     * through, letting a user briefly exceed the limit. Guarded by {@link #lock}.
     */
    private final Map<String, Integer> reserved = new HashMap<>();

    @Autowired
    public DownloadAdmissionService(
            RestServices restServices,
            InFlightDownloadCounter counter,
            DownloadLimitProperties limits,
            DownloadSizeLimitProperties sizeLimit) {
        this.restServices = restServices;
        this.counter = counter;
        this.limits = limits;
        this.sizeLimit = sizeLimit;
    }

    /**
     * Submit a download to AWS Batch and return its job id.
     *
     * @throws DownloadSizeExceededException the download is estimated at the size limit or more
     * @throws DownloadLimitExceededException the recipient already has {@code maxConcurrent}
     *                                         downloads running
     */
    public String submit(DownloadRequest request) throws JsonProcessingException {
        String key = InFlightDownloadCounter.recipientKey(request.recipient());

        if (sizeLimit.enabled()) {
            // Before the slot is reserved, so a slow estimate does not hold one.
            rejectIfTooLarge(request);
        }

        if (limits.enabled()) {
            // Outside the lock: the sweep is the only expensive step.
            counter.refreshIfStale();
            reserveOrReject(request, key);
        }

        try {
            Map<String, String> parameters = restServices.buildDownloadParameters(request);
            String jobName = RestServices.downloadJobName(request.recipient());
            String awsJobId = restServices.submitDownloadJob(jobName, parameters);
            counter.recordSubmitted(awsJobId, request.recipient());
            notifyStarted(request);
            return awsJobId;
        } finally {
            if (limits.enabled()) {
                releaseReservation(key);
            }
        }
    }

    private void rejectIfTooLarge(DownloadRequest request) {
        long estimatedBytes;
        try {
            estimatedBytes = restServices.estimateDownloadBytes(request);
        } catch (Exception e) {
            // Let it through, as the portal does when its own estimate fails. Blocking every
            // download because DAS cannot estimate would be worse than one job running out of disk.
            log.warn("Size estimate failed for uuid {}, submitting without the size check", request.uuid(), e);
            return;
        }

        long maxBytes = sizeLimit.maxSize().toBytes();
        if (estimatedBytes >= maxBytes) {
            log.info("Rejected download for uuid {}: estimated {} bytes, limit {} bytes",
                    request.uuid(), estimatedBytes, maxBytes);
            throw new DownloadSizeExceededException(estimatedBytes, maxBytes);
        }
    }

    private void reserveOrReject(DownloadRequest request, String key) {
        lock.lock();
        try {
            int inFlight = counter.countInFlight(request.recipient()) + reserved.getOrDefault(key, 0);
            if (inFlight >= limits.maxConcurrent()) {
                throw new DownloadLimitExceededException(limits.maxConcurrent());
            }
            reserved.merge(key, 1, Integer::sum);
        } finally {
            lock.unlock();
        }
    }

    private void releaseReservation(String key) {
        lock.lock();
        try {
            reserved.computeIfPresent(key, (k, count) -> count <= 1 ? null : count - 1);
        } finally {
            lock.unlock();
        }
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
