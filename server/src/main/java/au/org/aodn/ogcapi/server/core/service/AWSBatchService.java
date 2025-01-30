package au.org.aodn.ogcapi.server.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service("AWSBatchService")
@Slf4j
public class AWSBatchService {

    private final BatchClient batchClient;
    private final ObjectMapper objectMapper;

    public AWSBatchService(BatchClient batchClient, ObjectMapper objectMapper) {
        this.batchClient = batchClient;
        this.objectMapper = objectMapper;
    }

    public String submitJob(String jobName, String jobQueue, String jobDefinition, Map<String, String> parameters) {

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
                .containerOverrides(co -> co.environment(environmentVariables))
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
