package au.org.aodn.ogcapi.server.core.common;

import au.org.aodn.ogcapi.server.common.RestApi;
import au.org.aodn.ogcapi.server.core.model.enumeration.OGCMediaTypeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestApiTest {

    @Test
    public void verifyApiGet() {
        RestApi api = new RestApi();
        ResponseEntity<Void> response = api.apiGet(OGCMediaTypeMapper.json.toString());

        assertEquals(response.getStatusCode(), HttpStatus.TEMPORARY_REDIRECT, "Incorrect redirect");
        assertEquals(response.getHeaders().getLocation().getPath(), "/api/v1/ogc/api-docs/v3", "Incorrect path");

        response = api.apiGet(OGCMediaTypeMapper.html.toString());

        assertEquals(response.getStatusCode(), HttpStatus.TEMPORARY_REDIRECT, "Incorrect redirect");
        assertEquals(response.getHeaders().getLocation().getPath(), "/api/v1/ogc/swagger-ui/index.html", "Incorrect path");
    }
}
