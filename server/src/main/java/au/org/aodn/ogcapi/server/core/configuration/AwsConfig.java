package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.service.AWSBatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.batch.BatchClient;

@Configuration
public class AwsConfig {

    @Value("${aws.region}")
    private String awsRegion;

    @Bean
    public BatchClient batchClient() {
        return BatchClient
                .builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();
    }

    @Bean
    public AWSBatchService awsBatchService(BatchClient batchClient, ObjectMapper objectMapper) {
        return new AWSBatchService(batchClient, objectMapper);
    }
}
