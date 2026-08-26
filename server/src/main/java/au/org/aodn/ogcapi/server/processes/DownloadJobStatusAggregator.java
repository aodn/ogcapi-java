package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.processes.model.StatusCode;
import software.amazon.awssdk.services.batch.model.JobStatus;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Maps an already-fetched AWS Batch workflow snapshot to its public OGC status.
 * This class deliberately makes no AWS calls so the workflow rules remain deterministic.
 */
public class DownloadJobStatusAggregator {

    public enum WorkflowMode {
        EXPLICIT_ZARR,
        CHILD_DISCOVERY_REQUIRED
    }

    public record Snapshot(
            JobStatus initial,
            JobStatus prepare,
            JobStatus collect,
            WorkflowMode workflowMode,
            boolean discoveryWindowExpired
    ) {
        public Snapshot {
            Objects.requireNonNull(initial, "initial status is required");
            Objects.requireNonNull(workflowMode, "workflow mode is required");
        }
    }

    public StatusCode aggregate(Snapshot snapshot) {
        if (Stream.of(snapshot.initial(), snapshot.prepare(), snapshot.collect())
                .anyMatch(status -> status == JobStatus.FAILED)) {
            return StatusCode.FAILED;
        }

        if (Stream.of(snapshot.initial(), snapshot.prepare(), snapshot.collect())
                .filter(Objects::nonNull)
                .anyMatch(status -> status == JobStatus.UNKNOWN_TO_SDK_VERSION)) {
            throw new IllegalStateException("Unsupported AWS Batch job status");
        }

        boolean hasPrepare = snapshot.prepare() != null;
        boolean hasCollect = snapshot.collect() != null;
        if (hasPrepare != hasCollect) {
            if (snapshot.discoveryWindowExpired()) {
                throw new IllegalStateException("Only one child workflow job was found after the discovery window");
            }
            return StatusCode.RUNNING;
        }

        if (snapshot.collect() == JobStatus.SUCCEEDED) {
            return StatusCode.SUCCESSFUL;
        }

        if (hasPrepare) {
            return StatusCode.RUNNING;
        }

        return switch (snapshot.initial()) {
            case SUBMITTED, PENDING, RUNNABLE -> StatusCode.ACCEPTED;
            case STARTING, RUNNING -> StatusCode.RUNNING;
            case SUCCEEDED -> {
                if (snapshot.workflowMode() == WorkflowMode.EXPLICIT_ZARR
                        || snapshot.discoveryWindowExpired()) {
                    yield StatusCode.SUCCESSFUL;
                }
                yield StatusCode.RUNNING;
            }
            case FAILED -> StatusCode.FAILED;
            default -> throw new IllegalStateException("Unsupported AWS Batch job status");
        };
    }
}
