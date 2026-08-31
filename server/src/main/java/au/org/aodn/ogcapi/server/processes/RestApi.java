package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.processes.api.ProcessesApi;
import au.org.aodn.ogcapi.processes.api.JobsApi;
import au.org.aodn.ogcapi.processes.model.Execute;
import au.org.aodn.ogcapi.processes.model.InlineResponse200;
import au.org.aodn.ogcapi.processes.model.ProcessList;
import au.org.aodn.ogcapi.processes.model.Results;
import au.org.aodn.ogcapi.processes.model.JobList;
import au.org.aodn.ogcapi.processes.model.StatusInfo;
import au.org.aodn.ogcapi.server.core.model.DownloadExecutionResponse;
import au.org.aodn.ogcapi.server.core.model.DownloadJobStatusInfo;
import au.org.aodn.ogcapi.server.core.model.InlineValue;
import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import au.org.aodn.ogcapi.server.core.model.enumeration.InlineResponseKeyEnum;
import au.org.aodn.ogcapi.server.core.model.enumeration.ProcessIdEnum;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController("ProcessesRestApi")
@RequestMapping(value = "/api/v1/ogc")
public class RestApi implements ProcessesApi, JobsApi {

    @Autowired
    private RestServices restServices;

    @Autowired
    private DownloadJobStatusService downloadJobStatusService;

    @Autowired
    private DownloadAdmissionService downloadAdmissionService;

    @Override
    // because the produces value in the interface declaration includes "/_" which may
    // cause exception thrown sometimes. So i re-declared the produces value here
    @RequestMapping(
            value = "/processes/{processID}/execution",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE},
            consumes = {MediaType.APPLICATION_JSON_VALUE},
            method = RequestMethod.POST
    )
    @ApiResponse(
            responseCode = "200",
            description = "Download job accepted.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DownloadExecutionResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "message": {"message": "Job submitted with ID: 123e4567-e89b-12d3-a456-426614174000"},
                              "status": {"message": "200"},
                              "jobID": "123e4567-e89b-12d3-a456-426614174000",
                              "queued": false
                            }
                            """)))
    public ResponseEntity<InlineResponse200> execute(
            @Parameter(in = ParameterIn.PATH, required = true, schema = @Schema())
            @PathVariable("processID")
            String processID,
            @Parameter(in = ParameterIn.DEFAULT, description = "Mandatory execute request JSON", required = true, schema = @Schema())
            @Valid
            @RequestBody Execute body) {

        ProcessIdEnum processId = ProcessIdEnum.fromString(processID);

        if (processId == ProcessIdEnum.DOWNLOAD_DATASET) {
            try {

                String uuid = DatasetDownloadEnums.Parameter.UUID.getStringInput(body);
                String key = DatasetDownloadEnums.Parameter.KEY.getStringInput(body);
                String startDate = DatasetDownloadEnums.Parameter.START_DATE.getStringInput(body);
                String endDate = DatasetDownloadEnums.Parameter.END_DATE.getStringInput(body);
                String recipient = DatasetDownloadEnums.Parameter.RECIPIENT.getStringInput(body);
                String collectionTitle = DatasetDownloadEnums.Parameter.COLLECTION_TITLE.getStringInput(body);
                String fullMetadataLink = DatasetDownloadEnums.Parameter.FULL_METADATA_LINK.getStringInput(body);
                String suggestedCitation = DatasetDownloadEnums.Parameter.SUGGESTED_CITATION.getStringInput(body);
                String outputFormat = DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getStringInput(body);
                Object multiPolygon = DatasetDownloadEnums.Parameter.MULTI_POLYGON.getObjectInput(body);

                DownloadRequest request = new DownloadRequest(uuid, key, startDate, endDate, multiPolygon,
                        recipient, collectionTitle, fullMetadataLink, suggestedCitation, outputFormat);

                // The per-user limit is applied here: a user already at their limit has this
                // request held and released later, and gets a job id to poll either way.
                //
                // The notify user email lives on this side rather than in data-access-service to
                // make the first email faster. It goes out with the submit wherever that happens,
                // so it is still sent only once AWS Batch has accepted the job and returned a job
                // id - otherwise we promise the user a file that will never be produced - and it
                // is not sent at all while a download is still waiting for a slot.
                DownloadAdmission admission = downloadAdmissionService.submitOrHold(request);

                // The message keeps its historical wording for a download that went straight
                // through, so existing clients that read it see no change.
                var value = new InlineValue(admission.queued()
                        ? "Job queued with ID: " + admission.jobId()
                        : "Job submitted with ID: " + admission.jobId());
                var status = new InlineValue(Integer.toString(HttpStatus.OK.value()));
                var results = new DownloadExecutionResponse(
                        value, status, admission.jobId(), admission.queued(), admission.queuePosition());

                return ResponseEntity.ok(results);

            } catch (Exception e) {

                // TODO: currently all the errors return badRequest. This should be changed to return the correct status code
                log.error(e.getMessage());
                var response = new Results();
                var status = new InlineValue(Integer.toString(HttpStatus.BAD_REQUEST.value()));
                var value = new InlineValue("Error while getting dataset");
                response.put(InlineResponseKeyEnum.MESSAGE.getValue(), value);
                response.put(InlineResponseKeyEnum.STATUS.getValue(), status);

                return ResponseEntity.ok(response);
            }
        } else {
            var response = new Results();
            var status = new InlineValue(Integer.toString(HttpStatus.BAD_REQUEST.value()));
            var value = new InlineValue("Unknown process ID: unknown-process-id");
            response.put(InlineResponseKeyEnum.MESSAGE.getValue(), value);
            response.put(InlineResponseKeyEnum.STATUS.getValue(), status);

            return ResponseEntity.ok(response);
        }
    }


    @Override
    public ResponseEntity<au.org.aodn.ogcapi.processes.model.Process> getProcessDescription(String processID) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<ProcessList> getProcesses() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    @ApiResponse(responseCode = "501", description = "Listing jobs is not implemented.")
    public ResponseEntity<JobList> getJobs() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    @ApiResponse(responseCode = "501", description = "Retrieving job results is not implemented.")
    public ResponseEntity<Results> getResult(String jobId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    @ApiResponse(
            responseCode = "200",
            description = "Download job status.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DownloadJobStatusInfo.class)))
    public ResponseEntity<StatusInfo> getStatus(String jobId) {
        return ResponseEntity.ok(downloadJobStatusService.getStatus(jobId));
    }

    @Override
    @ApiResponse(responseCode = "501", description = "Dismissing jobs is not implemented.")
    public ResponseEntity<StatusInfo> dismiss(String jobId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * WFS download endpoint with SSE support to handle long-running operations and prevent timeouts
     */
    @RequestMapping(
            value = "/processes/{processID}/execution",
            produces = {MediaType.TEXT_EVENT_STREAM_VALUE},
            consumes = {MediaType.APPLICATION_JSON_VALUE},
            method = RequestMethod.POST
    )
    public SseEmitter executeSse(
            @Parameter(in = ParameterIn.PATH, required = true, schema = @Schema())
            @PathVariable("processID") String processId,
            @Parameter(in = ParameterIn.DEFAULT, description = "Mandatory execute request JSON", required = true, schema = @Schema())
            @RequestBody Execute body) {

        try {
            String uuid = DatasetDownloadEnums.Parameter.UUID.getStringInput(body);
            String layerName = DatasetDownloadEnums.Parameter.LAYER_NAME.getStringInput(body);
            String key = DatasetDownloadEnums.Parameter.KEY.getStringInput(body);
            String startDate = DatasetDownloadEnums.Parameter.START_DATE.getStringInput(body);
            String endDate = DatasetDownloadEnums.Parameter.END_DATE.getStringInput(body);
            String outputFormat = DatasetDownloadEnums.Parameter.OUTPUT_FORMAT.getStringInput(body);
            Object multiPolygon = DatasetDownloadEnums.Parameter.MULTI_POLYGON.getObjectInput(body);
            List<String> fields = DatasetDownloadEnums.Parameter.FIELDS.getListInput(body);

            ProcessIdEnum id = ProcessIdEnum.fromString(processId);

            SseEmitter emitter;

            switch (id) {
                case DOWNLOAD_WFS_SSE: {
                    emitter = restServices.downloadWfsDataWithSse(
                            uuid,
                            startDate,
                            endDate,
                            multiPolygon,
                            fields,
                            layerName,
                            outputFormat
                    );
                    break;
                }
                case DOWNLOAD_WFS_ESTIMATE: {
                    emitter = restServices.estimateWfsDownloadWithSse(
                            uuid,
                            startDate,
                            endDate,
                            multiPolygon,
                            fields,
                            layerName,
                            outputFormat
                    );
                    break;
                }
                case DOWNLOAD_CO_ESTIMATE: {
                    emitter = restServices.estimateCloudOptimisedDownloadWithSse(
                            uuid,
                            key,
                            startDate,
                            endDate,
                            multiPolygon,
                            outputFormat
                    );
                    break;
                }
                default: {
                    emitter = new SseEmitter(0L);
                    emitter.completeWithError(new BadRequestException(
                            String.format("Unknown process Id for SSE type download [%s]", processId)
                    ));
                }
            }

            return emitter;

        } catch (Exception e) {
            log.error("Download wfs data failed with unhandled error {}", e.getMessage());
            final SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(e);
            return emitter;
        }
    }
}
