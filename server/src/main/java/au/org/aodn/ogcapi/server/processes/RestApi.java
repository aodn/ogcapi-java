package au.org.aodn.ogcapi.server.processes;


import au.org.aodn.ogcapi.processes.api.ProcessesApi;
import au.org.aodn.ogcapi.processes.model.Execute;
import au.org.aodn.ogcapi.processes.model.InlineResponse200;
import au.org.aodn.ogcapi.processes.model.ProcessList;
import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import au.org.aodn.ogcapi.server.core.service.AWSBatchService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController("ProcessesRestApi")
@RequestMapping(value = "/api/v1/ogc/processes")
public class RestApi implements ProcessesApi {

    @Autowired
    private AWSBatchService awsBatchService;

    @PostMapping(path = "/download/{collectionId}")
    public ResponseEntity<String> downloadData(
            @PathVariable("collectionId") String collectionId,
            @Parameter(in = ParameterIn.QUERY, description = "start date")
            @Valid
            @RequestParam(name = "start_date")
            String startDate,

            @Parameter(in = ParameterIn.QUERY, description = "end date")
            @Valid
            @RequestParam(name = "end_date")
            String endDate,

            @Parameter(in = ParameterIn.QUERY, description = "minimum latitude")
            @Valid
            @RequestParam(name = "min_lat")
            String minLat,

            @Parameter(in = ParameterIn.QUERY, description = "minimum longitude")
            @Valid
            @RequestParam(name = "min_lon")
            String minLon,

            @Parameter(in = ParameterIn.QUERY, description = "maximum latitude")
            @Valid
            @RequestParam(name = "max_lat")
            String maxLat,

            @Parameter(in = ParameterIn.QUERY, description = "maximum longitude")
            @Valid
            @RequestParam(name = "max_lon")
            String maxLon,

            @Parameter(in = ParameterIn.QUERY, description = "recipient")
            @Valid
            @RequestParam(name = "recipient")
            String recipient
    ) {
        try {

            Map<String, String> parameters = new HashMap<>();
            parameters.put(DatasetDownloadEnums.Condition.UUID.getValue(), collectionId);
            parameters.put(DatasetDownloadEnums.Condition.START_DATE.getValue(), startDate);
            parameters.put(DatasetDownloadEnums.Condition.END_DATE.getValue(), endDate);
            parameters.put(DatasetDownloadEnums.Condition.MIN_LATITUDE.getValue(), minLat);
            parameters.put(DatasetDownloadEnums.Condition.MIN_LONGITUDE.getValue(), minLon);
            parameters.put(DatasetDownloadEnums.Condition.MAX_LATITUDE.getValue(), maxLat);
            parameters.put(DatasetDownloadEnums.Condition.MAX_LONGITUDE.getValue(), maxLon);
            parameters.put(DatasetDownloadEnums.Condition.RECIPIENT.getValue(), recipient);


            String jobId = awsBatchService.submitJob(
                    "generating-data-file-for-" + recipient.replaceAll("[^a-zA-Z0-9-_]", "-"),
                    DatasetDownloadEnums.JobQueue.GENERATING_CSV_DATA_FILE.getValue(),
                    DatasetDownloadEnums.JobDefinition.GENERATE_CSV_DATA_FILE.getValue(),
                    parameters);
            log.info("Job submitted with ID: " + jobId);
            return ResponseEntity.ok("Job submitted with ID: " + jobId);
        } catch (Exception e) {


            log.error("Error while getting dataset");
            log.error(e.getMessage());
            return ResponseEntity.badRequest().body("Error while getting dataset");
        }
    }

    @Override
    @RequestMapping(value = "/processes/{processID}/execution",
            produces = { "application/json", "text/html" },
            consumes = { "application/json" },
            method = RequestMethod.POST)
    public ResponseEntity<InlineResponse200> execute(
            @Parameter(in = ParameterIn.PATH, required=true, schema=@Schema())
            @PathVariable("processID")
            String processID,
            @Parameter(in = ParameterIn.DEFAULT, description = "Mandatory execute request JSON", required=true, schema=@Schema())
            @Valid
            @RequestBody Execute body){
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }


    @Override
    public ResponseEntity<au.org.aodn.ogcapi.processes.model.Process> getProcessDescription(String processID) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<ProcessList> getProcesses() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
