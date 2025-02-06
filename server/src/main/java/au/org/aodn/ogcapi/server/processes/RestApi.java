package au.org.aodn.ogcapi.server.processes;


import au.org.aodn.ogcapi.processes.api.ProcessesApi;
import au.org.aodn.ogcapi.processes.model.Execute;
import au.org.aodn.ogcapi.processes.model.InlineResponse200;
import au.org.aodn.ogcapi.processes.model.ProcessList;
import au.org.aodn.ogcapi.processes.model.Results;
import au.org.aodn.ogcapi.server.core.model.InlineValue;
import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import au.org.aodn.ogcapi.server.core.model.enumeration.ProcessIdEnum;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

        if (processID.equals(ProcessIdEnum.DOWNLOAD_DATASET.getValue())) {
            try {
                var response = restServices.downloadData(
                        (String) body.getInputs().get(DatasetDownloadEnums.Condition.UUID.getValue()),
                        (String) body.getInputs().get(DatasetDownloadEnums.Condition.START_DATE.getValue()),
                        (String) body.getInputs().get(DatasetDownloadEnums.Condition.END_DATE.getValue()),
                        body.getInputs().get(DatasetDownloadEnums.Condition.MULTI_POLYGON.getValue()),
                        (String) body.getInputs().get(DatasetDownloadEnums.Condition.RECIPIENT.getValue())
                );

                var value = new InlineValue(response.getBody());
                var results = new Results();
                results.put("message", value);

                return ResponseEntity.ok(results);

            } catch (Exception e) {

                // TODO: currently all the errors return badRequest. This should be changed to return the correct status code
                log.error(e.getMessage());
                var response = new Results();
                var value = new InlineValue("Error while getting dataset");
                response.put("error", value);

                return ResponseEntity.badRequest().body(response);
            }
        }

        var response = new Results();
        var value = new InlineValue("Unknown process ID: " + processID);
        response.put("error", value);

        return ResponseEntity.badRequest().body(response);
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
