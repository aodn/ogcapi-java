package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.processes.RestServices;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.batch.BatchClient;

@Configuration
public class AwsConfig {

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.batch.job.definition}")
    private String batchJobDefinition;

    @Value("${aws.batch.job.queue}")
    private String batchJobQueue;

    @Bean
    public BatchClient batchClient() {
        return BatchClient
                .builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public RestServices awsBatchService(BatchClient batchClient, ObjectMapper objectMapper) {
        return new RestServices(batchClient, objectMapper, batchJobDefinition, batchJobQueue);
    }
}
