package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.model.enumeration.ErrorCode;
import au.org.aodn.ogcapi.server.core.service.ApplicationInfo;
import au.org.aodn.ogcapi.server.core.service.das.DasService;
import au.org.aodn.ogcapi.server.core.service.dda.DdaService;
import au.org.aodn.ogcapi.server.core.service.geonetwork.Geonetwork;
import au.org.aodn.ogcapi.server.core.service.indexer.EsIndexer;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class ActuatorConfig {

    private static final String DEP_SERVICE = "depService";
    private static final String APPLICATION = "application";

    /**
     * Max wait for all services in one info request.
     * It is shorter than the 15 seconds timeout of the Portal admin panel, so a slow service
     * is left out and the panel still gets an answer.
     */
    protected static final Duration INFO_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Threads for the info fetch.
     * 1. Daemon threads, so they never stop the shutdown.
     * 2. The size is limited, because the info endpoint needs no login and each request
     *    makes one call to each service.
     */
    protected static final ExecutorService INFO_EXECUTOR = new ThreadPoolExecutor(
            0, 16, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable, "service-info");
                thread.setDaemon(true);
                return thread;
            });

    // Define the InfoContributor bean to fill in the missing info in /manage/info endpoint
    @Bean
    public InfoContributor externalApiInfoContributor(
            DasService das,
            DdaService dda,
            EsIndexer esIndexer,
            Geonetwork geonetwork
    ) {
        return createInfoContributor(List.of(das, dda, esIndexer, geonetwork), INFO_TIMEOUT);
    }

    /**
     * Build the depService part of the info. Nothing is cached, each info request fetches again.
     * 1. One fetch per service, name, version and description are read from that one result.
     * 2. The fetches run at the same time, so one slow service does not add up with the others.
     * 3. A service that is down, too slow or without a name is left out, the others stay.
     */
    protected InfoContributor createInfoContributor(List<ApplicationInfo> services, Duration timeout) {
        return builder -> {
            try {
                List<Callable<Map<String, Map<?,?>>>> fetches = new ArrayList<>();
                for (ApplicationInfo service : services) {
                    fetches.add(service::getAppInfo);
                }

                Map<String, Map<?,?>> versions = new HashMap<>();

                for (Future<Map<String, Map<?,?>>> fetch
                        : INFO_EXECUTOR.invokeAll(fetches, timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    // Cancelled means the service did not answer in time
                    if (!fetch.isCancelled()) {
                        addService(versions, fetch);
                    }
                }

                builder.withDetail(DEP_SERVICE, versions);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                builder.withDetail(DEP_SERVICE, Map.of("status", "ERROR Query", "error", "Interrupted"));
            } catch (Exception e) {
                builder.withDetail(DEP_SERVICE, Map.of(
                        "status", "ERROR Query",
                        "error", String.valueOf(e.getMessage())
                ));
            }
        };
    }

    /**
     * Add one service to the result. A missing version or description is left out,
     * it must not break the other services.
     */
    private static void addService(Map<String, Map<?,?>> versions, Future<Map<String, Map<?,?>>> fetch)
            throws InterruptedException {
        try {
            Map<String, Map<?,?>> info = fetch.get();
            Map<?,?> application = info != null ? info.get(APPLICATION) : null;
            Object name = application != null ? application.get("name") : null;

            if (name != null) {
                Map<String, String> detail = new HashMap<>();
                for (String key : List.of("version", "description")) {
                    Object value = application.get(key);
                    if (value != null) {
                        detail.put(key, value.toString());
                    }
                }
                versions.put(name.toString(), detail);
            }
        } catch (ExecutionException e) {
            log.warn("Fail to read service info, cause: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }
    }

    @Bean
    public HealthIndicator ogcApiHealth(
            @Value("${elasticsearch.vocabs_index.name}") String vocabIndexName,
            @Value("${elasticsearch.index.name}") String indexName,
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
