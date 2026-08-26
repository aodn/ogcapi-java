package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.processes.model.StatusCode;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.batch.model.JobStatus;

import static au.org.aodn.ogcapi.server.processes.DownloadJobStatusAggregator.WorkflowMode.CHILD_DISCOVERY_REQUIRED;
import static au.org.aodn.ogcapi.server.processes.DownloadJobStatusAggregator.WorkflowMode.EXPLICIT_ZARR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DownloadJobStatusAggregatorTest {

    private final DownloadJobStatusAggregator aggregator = new DownloadJobStatusAggregator();

    @Test
    void mapsInitialPreExecutionStatesToAccepted() {
        for (JobStatus status : new JobStatus[]{JobStatus.SUBMITTED, JobStatus.PENDING, JobStatus.RUNNABLE}) {
            assertEquals(StatusCode.ACCEPTED, aggregate(status, null, null, CHILD_DISCOVERY_REQUIRED, false));
        }
    }

    @Test
    void mapsInitialExecutionStatesToRunning() {
        for (JobStatus status : new JobStatus[]{JobStatus.STARTING, JobStatus.RUNNING}) {
            assertEquals(StatusCode.RUNNING, aggregate(status, null, null, CHILD_DISCOVERY_REQUIRED, false));
        }
    }

    @Test
    void mapsInitialFailureToFailed() {
        assertEquals(StatusCode.FAILED,
                aggregate(JobStatus.FAILED, null, null, CHILD_DISCOVERY_REQUIRED, false));
    }

    @Test
    void mapsDependentPendingRunnableAndRunningStatesToRunning() {
        for (JobStatus prepare : new JobStatus[]{JobStatus.PENDING, JobStatus.RUNNABLE, JobStatus.RUNNING}) {
            assertEquals(StatusCode.RUNNING,
                    aggregate(JobStatus.SUCCEEDED, prepare, JobStatus.PENDING, CHILD_DISCOVERY_REQUIRED, false));
        }
        for (JobStatus collect : new JobStatus[]{JobStatus.PENDING, JobStatus.RUNNABLE, JobStatus.RUNNING}) {
            assertEquals(StatusCode.RUNNING,
                    aggregate(JobStatus.SUCCEEDED, JobStatus.SUCCEEDED, collect, CHILD_DISCOVERY_REQUIRED, false));
        }
    }

    @Test
    void mapsCollectSuccessToSuccessful() {
        assertEquals(StatusCode.SUCCESSFUL,
                aggregate(JobStatus.SUCCEEDED, JobStatus.SUCCEEDED, JobStatus.SUCCEEDED,
                        CHILD_DISCOVERY_REQUIRED, true));
    }

    @Test
    void failureTakesPrecedenceOverCollectSuccess() {
        assertEquals(StatusCode.FAILED,
                aggregate(JobStatus.SUCCEEDED, JobStatus.FAILED, JobStatus.SUCCEEDED,
                        CHILD_DISCOVERY_REQUIRED, true));
    }

    @Test
    void explicitZarrWithoutChildrenSucceedsImmediately() {
        assertEquals(StatusCode.SUCCESSFUL,
                aggregate(JobStatus.SUCCEEDED, null, null, EXPLICIT_ZARR, false));
    }

    @Test
    void ambiguousChildlessWorkflowRunsInsideWindowAndSucceedsAfterward() {
        assertEquals(StatusCode.RUNNING,
                aggregate(JobStatus.SUCCEEDED, null, null, CHILD_DISCOVERY_REQUIRED, false));
        assertEquals(StatusCode.SUCCESSFUL,
                aggregate(JobStatus.SUCCEEDED, null, null, CHILD_DISCOVERY_REQUIRED, true));
    }

    @Test
    void singleChildRunsInsideWindowAndIsInconsistentAfterward() {
        assertEquals(StatusCode.RUNNING,
                aggregate(JobStatus.SUCCEEDED, JobStatus.SUCCEEDED, null,
                        CHILD_DISCOVERY_REQUIRED, false));
        assertEquals(StatusCode.RUNNING,
                aggregate(JobStatus.SUCCEEDED, null, JobStatus.PENDING,
                        CHILD_DISCOVERY_REQUIRED, false));

        assertThrows(IllegalStateException.class,
                () -> aggregate(JobStatus.SUCCEEDED, JobStatus.SUCCEEDED, null,
                        CHILD_DISCOVERY_REQUIRED, true));
        assertThrows(IllegalStateException.class,
                () -> aggregate(JobStatus.SUCCEEDED, null, JobStatus.PENDING,
                        CHILD_DISCOVERY_REQUIRED, true));
        assertThrows(IllegalStateException.class,
                () -> aggregate(JobStatus.SUCCEEDED, null, JobStatus.SUCCEEDED,
                        CHILD_DISCOVERY_REQUIRED, true));
    }

    @Test
    void childFailureIsTerminalEvenWhenOnlyOneChildIsVisible() {
        assertEquals(StatusCode.FAILED,
                aggregate(JobStatus.SUCCEEDED, JobStatus.FAILED, null,
                        CHILD_DISCOVERY_REQUIRED, false));
        assertEquals(StatusCode.FAILED,
                aggregate(JobStatus.SUCCEEDED, null, JobStatus.FAILED,
                        CHILD_DISCOVERY_REQUIRED, true));
    }

    private StatusCode aggregate(
            JobStatus initial,
            JobStatus prepare,
            JobStatus collect,
            DownloadJobStatusAggregator.WorkflowMode mode,
            boolean expired) {
        return aggregator.aggregate(new DownloadJobStatusAggregator.Snapshot(
                initial, prepare, collect, mode, expired));
    }
}
