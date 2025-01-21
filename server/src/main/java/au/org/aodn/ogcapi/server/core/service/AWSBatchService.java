package au.org.aodn.ogcapi.server.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.KeyValuePair;
import software.amazon.awssdk.services.batch.model.SubmitJobRequest;
import software.amazon.awssdk.services.batch.model.SubmitJobResponse;

import java.util.List;
import java.util.Map;

@Service("AWSBatchService")
@Slf4j
public class AWSBatchService {

    private final BatchClient batchClient;

    public AWSBatchService(BatchClient batchClient) {
        this.batchClient = batchClient;
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

}
