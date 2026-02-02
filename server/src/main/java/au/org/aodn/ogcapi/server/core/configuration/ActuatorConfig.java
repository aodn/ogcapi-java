package au.org.aodn.ogcapi.server.core.configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActuatorConfig {

    @Bean
    public HealthIndicator elasticsearchHealth(
            @Value("${elasticsearch.vocabs_index.name}") String vocabIndexName,
            @Value("${elasticsearch.index.name}") String indexName,
            @Value("${elasticsearch.cloud_optimized_index.name}") String coIndexName,
            ElasticsearchClient client) {
        return () -> {
            try {
                // Is elastic up and run?
                String status = client.cluster().health(r -> r).status().toString();
                if ("yellow".equalsIgnoreCase(status)) {
                    return Health.status("degraded")
                            .withDetail("reason", "elastic status yellow")
                            .build();
                }
                if ("red".equalsIgnoreCase(status)) {
                    return Health.status("degraded")
                            .withDetail("reason", "elastic status red")
                            .build();
                }

                // Now if our target index exist?
                if(!client.indices().exists(ExistsRequest.of(b -> b.index(indexName))).value()) {
                    return Health.down()
                            .withDetail("reason", "require index missing")
                            .build();
                }

                if(!client.indices().exists(ExistsRequest.of(b -> b.index(coIndexName))).value()) {
                    return Health.status("degraded")
                            .withDetail("reason", "require cloud optimized index missing")
                            .build();
                }

                if(!client.indices().exists(ExistsRequest.of(b -> b.index(vocabIndexName))).value()) {
                    return Health.status("degraded")
                            .withDetail("reason", "require vocab index missing")
                            .build();
                }

                return Health.up().build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("reason", "elastic connection error")
                        .withException(e)
                        .build();
            }
        };
    }
}
