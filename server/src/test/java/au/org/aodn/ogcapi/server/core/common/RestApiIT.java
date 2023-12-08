package au.org.aodn.ogcapi.server.core.common;

import au.org.aodn.ogcapi.features.model.Collections;
import au.org.aodn.ogcapi.server.core.BaseTestClass;
import org.junit.AfterClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class RestApiIT extends BaseTestClass {

    @AfterClass
    public void afterClass() {
        super.shutdownElasticSearch();
    }

    @AfterEach
    public void afterEach() throws IOException {
        super.clearElasticIndex();
    }

    @Test
    public void verifyClusterIsHealthy() throws IOException {
        super.assertClusterHealthResponse();
    }

    @Test
    public void verifyApiCollectionsQuery() throws IOException {
        super.insertJsonToElasticIndex("516811d7-cd1e-207a-e0440003ba8c79dd.json");

        // Call rest api directly and get query result
        ResponseEntity<Collections> collections = testRestTemplate.getForEntity(getBasePath() + "/collections?q=reproduction", Collections.class);

        assertEquals(1, collections.getBody().getCollections().size(), "Contain 1 item");
    }
}
