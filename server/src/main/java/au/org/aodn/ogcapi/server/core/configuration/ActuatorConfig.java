package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.model.enumeration.ErrorCode;
import au.org.aodn.ogcapi.server.core.service.das.DasService;
import au.org.aodn.ogcapi.server.core.service.geonetwork.Geonetwork;
import au.org.aodn.ogcapi.server.core.service.indexer.EsIndexer;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class ActuatorConfig {

    private static final String VERSION_TAG = "depServiceVersion";



    // Define the InfoContributor bean to fill in the missing info in /manage/info endpoint
    @Bean
    public InfoContributor externalApiInfoContributor(
            DasService das,
            @Value("${data-discovery-ai.host}") final String ddaHost,
            @Value("${data-discovery-ai.info-path}") final String ddaInfoPath,
            EsIndexer esIndexer,
            Geonetwork geonetwork
    ) {
        return builder -> {
            try {
                Map<String, Map<?,?>> versions = new HashMap<>();
                versions.put(das.getName(),
                        Map.of("version", das.getVersion(), "description", das.getDescription()));
                versions.put(esIndexer.getName(),
                        Map.of("version", esIndexer.getVersion(), "description", esIndexer.getDescription()));
                versions.put(geonetwork.getName(),
                        Map.of("version", geonetwork.getVersion(), "description", geonetwork.getDescription()));

                builder.withDetail(VERSION_TAG, versions);
            } catch (Exception e) {
                builder.withDetail(VERSION_TAG, Map.of(
                        "status", "ERROR Query",
                        "error", e.getMessage()
                ));
            }
        };
    }

    @Bean
    public HealthIndicator ogcApiHealth(
            @Value("${elasticsearch.vocabs_index.name}") String vocabIndexName,
            @Value("${elasticsearch.index.name}") String indexName,
            @Value("${elasticsearch.cloud_optimized_index.name}") String coIndexName,
            ElasticsearchClient client) {
        return () -> {
            try {
                // Is elastic up and run?
                String status = client.cluster().health(r -> r).status().toString();
                // Yellow state means partially available (e.g. one shard is down),
                // so it still work, only when Red then it will not work
                if (HealthStatus.Red.name().equalsIgnoreCase(status)) {
                    return Health.status(ErrorCode.ELASTICSEARCH_UNAVAILABLE.getStatus())
                            .withDetail("reason", ErrorCode.ELASTICSEARCH_UNAVAILABLE.getMessage())
                            .withDetail("code", ErrorCode.ELASTICSEARCH_UNAVAILABLE.getCode())
                            .build();
                }

                // Now if our target index exist?
                if(!client.indices().exists(ExistsRequest.of(b -> b.index(indexName))).value()) {
                    return Health.status(ErrorCode.MISSING_CORE_INDEX.getStatus())
                            .withDetail("reason", ErrorCode.MISSING_CORE_INDEX.getMessage())
                            .withDetail("code", ErrorCode.MISSING_CORE_INDEX.getCode())
                            .build();
                }

                if(!client.indices().exists(ExistsRequest.of(b -> b.index(coIndexName))).value()) {
                    return Health.status(ErrorCode.MISSING_CO_CORE_INDEX.getStatus())
                            .withDetail("reason", ErrorCode.MISSING_CO_CORE_INDEX.getMessage())
                            .withDetail("code", ErrorCode.MISSING_CO_CORE_INDEX.getCode())
                            .build();
                }

                if(!client.indices().exists(ExistsRequest.of(b -> b.index(vocabIndexName))).value()) {
                    return Health.status(ErrorCode.MISSING_VOCAB_INDEX.getStatus())
                            .withDetail("reason", ErrorCode.MISSING_VOCAB_INDEX.getMessage())
                            .withDetail("code", ErrorCode.MISSING_VOCAB_INDEX.getCode())
                            .build();
                }

                return Health.up().build();
            } catch (Exception e) {
                return Health.status(ErrorCode.ELASTICSEARCH_UNAVAILABLE.getStatus())
                        .withDetail("reason", ErrorCode.ELASTICSEARCH_UNAVAILABLE.getMessage())
                        .withDetail("code", ErrorCode.ELASTICSEARCH_UNAVAILABLE.getCode())
                        .withException(e)
                        .build();
            }
        };
    }
}
