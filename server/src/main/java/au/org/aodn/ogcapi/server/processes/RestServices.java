package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.SubmitJobRequest;
import software.amazon.awssdk.services.batch.model.SubmitJobResponse;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class RestServices {

    private final BatchClient batchClient;
    private final ObjectMapper objectMapper;

    public RestServices(BatchClient batchClient, ObjectMapper objectMapper) {
        this.batchClient = batchClient;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<String> downloadData(
            String id,
            String startDate,
            String endDate,
            Object polygons,
            String recipient
    ) throws JsonProcessingException {

            Map<String, String> parameters = new HashMap<>();
            parameters.put(DatasetDownloadEnums.Condition.UUID.getValue(), id);
            parameters.put(DatasetDownloadEnums.Condition.START_DATE.getValue(), startDate);
            parameters.put(DatasetDownloadEnums.Condition.END_DATE.getValue(), endDate);
            parameters.put(DatasetDownloadEnums.Condition.MULTI_POLYGON.getValue(), objectMapper.writeValueAsString(polygons));
            parameters.put(DatasetDownloadEnums.Condition.RECIPIENT.getValue(), recipient);


            String jobId = submitJob(
                    "generating-data-file-for-" + recipient.replaceAll("[^a-zA-Z0-9-_]", "-"),
                    DatasetDownloadEnums.JobQueue.GENERATING_CSV_DATA_FILE.getValue(),
                    DatasetDownloadEnums.JobDefinition.GENERATE_CSV_DATA_FILE.getValue(),
                    parameters);
            log.info("Job submitted with ID: " + jobId);
            return ResponseEntity.ok("Job submitted with ID: " + jobId);
    }

    private String submitJob(String jobName, String jobQueue, String jobDefinition, Map<String, String> parameters) {

        SubmitJobRequest submitJobRequest = SubmitJobRequest.builder()
                .jobName(jobName)
                .jobQueue(jobQueue)
                .jobDefinition(jobDefinition)
                .parameters(parameters)
                .build();

        SubmitJobResponse submitJobResponse = batchClient.submitJob(submitJobRequest);
        return submitJobResponse.jobId();
    }
}
