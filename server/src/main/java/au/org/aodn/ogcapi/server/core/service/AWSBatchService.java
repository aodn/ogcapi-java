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

    public AWSBatchService(BatchClient batchClient) {
        this.batchClient = batchClient;
        this.objectMapper = new ObjectMapper();
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

    public JobQueueDetail getJobQueueBy(String name) {
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

    public boolean isJobQueueValid(String jobQueueName) throws IOException {

        var remoteJobQueue = getJobQueueBy(jobQueueName);
        var remoteJobQueueConfig = objectMapper.writeValueAsString(remoteJobQueue);
        var localJobQueueConfig = getLocalJobQueueConfigBy(jobQueueName);

        return remoteJobQueueConfig.equals(localJobQueueConfig);
    }

    public String getLocalJobQueueConfigBy(String jobQueueName) throws IOException {
        // TODO: implement the path later
        var configJsonPath = "";
        var jsonFile = new File(configJsonPath);

        return objectMapper.writeValueAsString(objectMapper.readValue(jsonFile, Map.class));
    }

}
