package au.org.aodn.ogcapi.server.processes;


import au.org.aodn.ogcapi.processes.api.ProcessesApi;
import au.org.aodn.ogcapi.processes.model.Execute;
import au.org.aodn.ogcapi.processes.model.InlineResponse200;
import au.org.aodn.ogcapi.processes.model.ProcessList;
import au.org.aodn.ogcapi.processes.model.Results;
import au.org.aodn.ogcapi.server.core.model.InlineValue;
import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import au.org.aodn.ogcapi.server.core.model.enumeration.InlineResponseKeyEnum;
import au.org.aodn.ogcapi.server.core.model.enumeration.ProcessIdEnum;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@Slf4j
@RestController("ProcessesRestApi")
@RequestMapping(value = "/api/v1/ogc")
public class RestApi implements ProcessesApi {

    @Autowired
    private RestServices restServices;

    @Override
    // because the produces value in the interface declaration includes "/_" which may
    // cause exception thrown sometimes. So i re-declared the produces value here
    @RequestMapping(value = "/processes/{processID}/execution",
            produces = {"application/json", "text/html"},
            consumes = {"application/json"},
            method = RequestMethod.POST)
    public ResponseEntity<InlineResponse200> execute(
            @Parameter(in = ParameterIn.PATH, required = true, schema = @Schema())
            @PathVariable("processID")
            String processID,
            @Parameter(in = ParameterIn.DEFAULT, description = "Mandatory execute request JSON", required = true, schema = @Schema())
            @Valid
            @RequestBody Execute body) {

        if (processID.equals(ProcessIdEnum.DOWNLOAD_DATASET.getValue())) {

            try {

                var uuid = (String) body.getInputs().get(DatasetDownloadEnums.Parameter.UUID.getValue());
                var startDate = (String) body.getInputs().get(DatasetDownloadEnums.Parameter.START_DATE.getValue());
                var endDate = (String) body.getInputs().get(DatasetDownloadEnums.Parameter.END_DATE.getValue());
                var multiPolygon = body.getInputs().get(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue());
                var recipient = (String) body.getInputs().get(DatasetDownloadEnums.Parameter.RECIPIENT.getValue());

                // move the notify user email from data-access-service to here to make the first email faster
                restServices.notifyUser(recipient, uuid, startDate, endDate);

                var response = restServices.downloadData(uuid, startDate, endDate, multiPolygon, recipient);

                var value = new InlineValue(response.getBody());
                var status = new InlineValue(Integer.toString(HttpStatus.OK.value()));
                var results = new Results();
                results.put(InlineResponseKeyEnum.MESSAGE.getValue(), value);
                results.put(InlineResponseKeyEnum.STATUS.getValue(), status);

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

//    /**
//     * WFS download endpoint that streams data directly to client
//     */
//    @RequestMapping(value = "/processes/downloadWfs/execution",
//            produces = {"text/csv", "application/octet-stream"},
//            consumes = {"application/json"},
//            method = RequestMethod.POST)
//    public ResponseEntity<StreamingResponseBody> downloadWfs(
//            @Parameter(in = ParameterIn.DEFAULT, description = "Mandatory execute request JSON", required = true, schema = @Schema())
////            @Valid
//            @RequestBody Execute body) {
//
//        try {
//            var uuid = (String) body.getInputs().get(DatasetDownloadEnums.Parameter.UUID.getValue());
//            var startDate = (String) body.getInputs().get(DatasetDownloadEnums.Parameter.START_DATE.getValue());
//            var endDate = (String) body.getInputs().get(DatasetDownloadEnums.Parameter.END_DATE.getValue());
//            var multiPolygon = body.getInputs().get(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue());
//            var fields = (List<String>) body.getInputs().get(DatasetDownloadEnums.Parameter.FIELDS.getValue());
//            var layerName = (String) body.getInputs().get(DatasetDownloadEnums.Parameter.LAYER_NAME.getValue());
//
//            // Check if layer name is provided
//            if (layerName == null || layerName.trim().isEmpty()) {
//                return ResponseEntity.badRequest().build();
//            }
//
//            // Stream WFS data directly to client
//            return restServices.downloadWfsData(uuid, startDate, endDate, multiPolygon, fields, layerName);
//
//        } catch (Exception e) {
//            log.error("Error processing WFS download request: {}", e.getMessage(), e);
//            return ResponseEntity.internalServerError().build();
//        }
//    }

    /**
     * WFS download endpoint with SSE support to handle long-running operations and prevent timeouts
     */
    @RequestMapping(value = "/processes/downloadWfs/execution",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            consumes = {"application/json"},
            method = RequestMethod.POST)
    public SseEmitter downloadWfsSse(
            @Parameter(in = ParameterIn.DEFAULT, description = "Mandatory execute request JSON", required = true, schema = @Schema())
//            @Valid
            @RequestBody Execute body) {

        final SseEmitter emitter = new SseEmitter(0L);

        try {
            var uuid = (String) body.getInputs().get(DatasetDownloadEnums.Parameter.UUID.getValue());
            var startDate = (String) body.getInputs().get(DatasetDownloadEnums.Parameter.START_DATE.getValue());
            var endDate = (String) body.getInputs().get(DatasetDownloadEnums.Parameter.END_DATE.getValue());
            var multiPolygon = body.getInputs().get(DatasetDownloadEnums.Parameter.MULTI_POLYGON.getValue());
            var fields = (List<String>) body.getInputs().get(DatasetDownloadEnums.Parameter.FIELDS.getValue());
            var layerName = (String) body.getInputs().get(DatasetDownloadEnums.Parameter.LAYER_NAME.getValue());

            // Check if layer name is provided
            if (layerName == null || layerName.trim().isEmpty()) {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("Layer name is required"));
                emitter.completeWithError(new IllegalArgumentException("Layer name is required"));
                return emitter;
            }

            return restServices.downloadWfsDataWithSse(
                    uuid, startDate, endDate, multiPolygon, fields, layerName, emitter
            );

        } catch (Exception e) {
            log.error("Error processing async WFS download request", e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("Error processing request: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error sending error event via SSE", ex);
                emitter.completeWithError(ex);
            }
        }

        return emitter;
    }
}
