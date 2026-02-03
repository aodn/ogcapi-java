package au.org.aodn.ogcapi.server.core.configuration;

import au.org.aodn.ogcapi.server.core.model.enumeration.ErrorCode;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.cluster.ElasticsearchClusterClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ActuatorConfigTest {

    @Mock
    private ElasticsearchClient client;

    @Mock
    private ElasticsearchClusterClient cluster;

    @Mock
    private ElasticsearchIndicesClient indexes;

    @Mock
    private HealthResponse clusterHealth;

    @Mock
    private BooleanResponse booleanHealthTrue;

    @Mock
    private BooleanResponse booleanHealthFalse;

    @InjectMocks
    private ActuatorConfig config;

    private static final String INDEX       = "test-core";
    private static final String CO_INDEX    = "test-co-core";
    private static final String VOCAB_INDEX = "test-vocabs";

    @BeforeEach
    void init() {
        when(client.cluster()).thenReturn(cluster);
        when(client.indices()).thenReturn(indexes);
        when(booleanHealthTrue.value()).thenReturn(true);
        when(booleanHealthFalse.value()).thenReturn(false);
    }

    @AfterEach
    void reset() {
        Mockito.reset(client, cluster, clusterHealth, booleanHealthTrue, booleanHealthFalse);
    }

    @Test
    @SuppressWarnings("unchecked")
    void greenCluster_allIndicesExist_returnsUp() throws Exception {
        when(client.cluster().health(any(Function.class))).thenReturn(clusterHealth);

        when(clusterHealth.status()).thenReturn(HealthStatus.Green);
        when(client.indices().exists(any(ExistsRequest.class))).thenReturn(booleanHealthTrue);

        Health health = config.ogcApiHealth(VOCAB_INDEX, INDEX, CO_INDEX, client).health();
        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void redCluster_returnsUnavailable() throws Exception {
        when(client.cluster().health(any(Function.class))).thenReturn(clusterHealth);
        when(clusterHealth.status()).thenReturn(HealthStatus.Red);

        Health health = config.ogcApiHealth(VOCAB_INDEX, INDEX, CO_INDEX, client).health();

        assertEquals(ErrorCode.ELASTICSEARCH_UNAVAILABLE.getStatus(), health.getStatus().toString());
        assertTrue(health.getDetails().containsValue(ErrorCode.ELASTICSEARCH_UNAVAILABLE.getMessage()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void yellowCluster_returnsUnavailable() throws Exception {
        when(client.cluster().health(any(Function.class))).thenReturn(clusterHealth);
        when(clusterHealth.status()).thenReturn(HealthStatus.Yellow);

        Health health = config.ogcApiHealth(VOCAB_INDEX, INDEX, CO_INDEX, client).health();
        assertEquals(ErrorCode.ELASTICSEARCH_UNAVAILABLE.getStatus(), health.getStatus().toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void exceptionThrown_returnsUnavailableWithException() throws Exception {
        when(client.cluster().health(any(Function.class))).thenThrow(new RuntimeException("connection failed"));

        Health health = config.ogcApiHealth(VOCAB_INDEX, INDEX, CO_INDEX, client).health();

        assertEquals(ErrorCode.ELASTICSEARCH_UNAVAILABLE.getStatus(), health.getStatus().toString());
        // Exception details in error section
        assertTrue(health.getDetails().containsKey("error"));
    }

    // Helpers
    @SuppressWarnings("unchecked")
    private void greenCluster() throws Exception {
        when(client.cluster().health(any(Function.class))).thenReturn(clusterHealth);
        when(clusterHealth.status()).thenReturn(HealthStatus.Green);
    }

    @Test
    void missingCoreIndex_returnsMissingCoreIndex() throws Exception {
        greenCluster();

        when(client.indices().exists(any(ExistsRequest.class))).thenAnswer(inv -> {
            ExistsRequest req = inv.getArgument(0);
            return req.index().contains(INDEX) ? booleanHealthFalse : booleanHealthTrue;
        });

        Health health = config.ogcApiHealth(VOCAB_INDEX, INDEX, CO_INDEX, client).health();
        assertEquals(ErrorCode.MISSING_CORE_INDEX.getCode(), health.getDetails().get("code").toString());
    }

    @Test
    void missingVocabIndex_returnsMissingVocabIndex() throws Exception {
        greenCluster();

        when(client.indices().exists(any(ExistsRequest.class))).thenAnswer(inv -> {
            ExistsRequest req = inv.getArgument(0);
            return req.index().contains(VOCAB_INDEX) ? booleanHealthFalse : booleanHealthTrue;
        });

        Health health = config.ogcApiHealth(VOCAB_INDEX, INDEX, CO_INDEX, client).health();
        assertEquals(ErrorCode.MISSING_VOCAB_INDEX.getCode(), health.getDetails().get("code").toString());
    }
}