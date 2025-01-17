package au.org.aodn.ogcapi.server.processes;


import au.org.aodn.ogcapi.server.core.service.AWSBatchService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController("ProcessesRestApi")
@RequestMapping(value = "/api/v1/ogc/processes")
public class RestApi  {

    @Autowired
    private AWSBatchService awsBatchService;

    @PostMapping(path="/download/{collectionId}")
    public ResponseEntity downloadData(
            @PathVariable("collectionId") String collectionId,
            @Parameter(in = ParameterIn.QUERY, description = "start date") @Valid String startDate,
            @Parameter(in = ParameterIn.QUERY, description = "end date") @Valid String endDate,
            @Parameter(in = ParameterIn.QUERY, description = "bounding box") @Valid String bbox
    ){
        try {

            System.out.println("AWS_ACCESS_KEY_ID: " + System.getenv("AWS_ACCESS_KEY_ID"));
            System.out.println("AWS_SECRET_ACCESS_KEY: " + System.getenv("AWS_SECRET_ACCESS_KEY"));

            Map<String, String> parameters = new HashMap<>();
            parameters.put("collectionId", collectionId);
            parameters.put("startDate", startDate);
            parameters.put("endDate", endDate);
            parameters.put("bbox", bbox);
            String jobId = awsBatchService.submitJob(
                    "test-downloading-job",
                    "test-downloading-job-queue",
                    "test-download-job-definition",
                    parameters);

            return ResponseEntity.ok("Job submitted with ID: " + jobId);
        } catch (Exception e) {
            System.out.println("Error while getting dataset");
            System.out.println(e.getMessage());
            return ResponseEntity.badRequest().body("Invalid parameters");
        }


    }

}
