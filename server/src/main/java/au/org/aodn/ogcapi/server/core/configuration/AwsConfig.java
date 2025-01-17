package au.org.aodn.ogcapi.server.core.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.services.batch.BatchClient;

@Configuration
public class AwsConfig {

    @Bean
    public BatchClient batchClient() {
        return BatchClient
                .builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();
    }
}
