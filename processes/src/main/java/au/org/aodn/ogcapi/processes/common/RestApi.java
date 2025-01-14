package au.org.aodn.ogcapi.processes.common;

import au.org.aodn.ogcapi.processes.api.JobsApi;
import au.org.aodn.ogcapi.processes.api.ProcessesApi;
import au.org.aodn.ogcapi.processes.model.Process;
import au.org.aodn.ogcapi.processes.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("CommonRestApi")
@RequestMapping(value = "/api/v1/ogc")
public class RestApi implements ProcessesApi, JobsApi {
    @Override
    public ResponseEntity<InlineResponse200> execute(String processID, Execute body) {
        return null;
    }

    @Override
    public ResponseEntity<Process> getProcessDescription(String processID) {
        return null;
    }

    @Override
    public ResponseEntity<ProcessList> getProcesses() {
        return null;
    }

    @Override
    public ResponseEntity<StatusInfo> dismiss(String jobId) {
        return null;
    }

    @Override
    public ResponseEntity<JobList> getJobs() {
        return null;
    }

    @Override
    public ResponseEntity<Results> getResult(String jobId) {
        return null;
    }

    @Override
    public ResponseEntity<StatusInfo> getStatus(String jobId) {
        return null;
    }
}
