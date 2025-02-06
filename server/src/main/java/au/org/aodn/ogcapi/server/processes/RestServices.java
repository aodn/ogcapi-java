package au.org.aodn.ogcapi.server.processes;

import au.org.aodn.ogcapi.server.core.model.enumeration.DatasetDownloadEnums;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
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

        List<KeyValuePair> environmentVariables = parameters.entrySet().stream()
                .map(entry ->
                        KeyValuePair
                                .builder()
                                .name(entry.getKey())
                                .value(entry.getValue())
                                .build()
                )
                .toList();

        SubmitJobRequest submitJobRequest = SubmitJobRequest.builder()
                .jobName(jobName)
                .jobQueue(jobQueue)
                .jobDefinition(jobDefinition)
                .parameters(parameters)
                .build();

        SubmitJobResponse submitJobResponse = batchClient.submitJob(submitJobRequest);
        return submitJobResponse.jobId();
    }



    // TODO: This feature doesn't work yet. Will be implemented in the future as this one is not urgent
    public boolean isJobQueueValid(String jobQueueName) throws IOException {

        var remoteJobQueueDetail = getRemoteJobQueueBy(jobQueueName);
        var localJobQueueDetail = getLocalJobQueueDetailBy(jobQueueName);

        return remoteJobQueueDetail.equals(localJobQueueDetail);
    }

    public JobQueueDetail getRemoteJobQueueBy(String name) {
        var request = DescribeJobQueuesRequest
                .builder()
                .jobQueues(name)
                .build();

        var jobQueues = batchClient.describeJobQueues(request).jobQueues();

        if (jobQueues != null && jobQueues.size() == 1) {
            return jobQueues.get(0);
        }
        return null;
    }

    public JobQueueDetail getLocalJobQueueDetailBy(String jobQueueName) throws IOException {
        var configJsonPath = "server/src/main/java/au/org/aodn/ogcapi/server/processes/config/" + jobQueueName + ".json";
        var jsonFile = new File(configJsonPath);
        var jsonStr = objectMapper.writeValueAsString(objectMapper.readValue(jsonFile, Object.class));
        return objectMapper.readValue(jsonStr, JobQueueDetail.class);
    }

}
