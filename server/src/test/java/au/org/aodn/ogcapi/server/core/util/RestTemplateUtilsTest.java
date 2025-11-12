package au.org.aodn.ogcapi.server.core.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RestTemplateUtilsTest {

    @Mock
    private RestTemplate restTemplate;

    private RestTemplateUtils restTemplateUtils;

    @BeforeEach
    void setUp() {
        Mockito.reset(restTemplate);
        restTemplateUtils = new RestTemplateUtils(restTemplate);
    }

    @Test
    public void testHandleRedirectSameHostRedirectFollowed() throws URISyntaxException {
        // Arrange
        String sourceUrl = "http://example.com/path";
        String redirectUrl = "https://example.com/redirected";
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(new URI(redirectUrl));
        ResponseEntity<String> response = new ResponseEntity<>(null, headers, HttpStatus.MOVED_PERMANENTLY);
        ResponseEntity<String> redirectResponse = new ResponseEntity<>("Redirected content", HttpStatus.OK);

        when(restTemplate.exchange(eq(redirectUrl.replace("%20", " ")), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(redirectResponse);

        // Act
        ResponseEntity<String> result = restTemplateUtils.handleRedirect(sourceUrl, response, String.class, new HttpEntity<>(null));

        // Assert
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("Redirected content", result.getBody());
        verify(restTemplate, times(1)).exchange(eq(redirectUrl.replace("%20", " ")), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    public void testHandleRedirectDifferentHostRedirectNotFollowed() throws URISyntaxException {
        // Arrange
        String sourceUrl = "http://example.com/path";
        String redirectUrl = "https://different.com/redirected";
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(new URI(redirectUrl));
        ResponseEntity<String> response = new ResponseEntity<>(null, headers, HttpStatus.FOUND);

        // Act
        ResponseEntity<String> result = restTemplateUtils.handleRedirect(sourceUrl, response, String.class, new HttpEntity<>(null));

        // Assert
        assertEquals(response, result);
        verify(restTemplate, never()).getForEntity(anyString(), eq(String.class), anyMap());
    }
    /**
     * You cannot modify or encode value in CQL_FILTER as geoserver will not work on encoded value in filter
     */
    @Test
    public void verifyCQLFilterPreserved() throws URISyntaxException {
        String sourceUrl = "http://example.com/path?CQL_FILTER=time DURING 2015-01-01T00:00:00.000Z/2015-01-10T00:00:00.000Z";
        // The redirect call from server may already encode the param, we need to make sure %20 space is decoded
        String redirectUrl = "https://example.com/path?CQL_FILTER=time%20DURING%202015-01-01T00:00:00.000Z/2015-01-10T00:00:00.000Z";
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(new URI(redirectUrl));
        ResponseEntity<String> response = new ResponseEntity<>(null, headers, HttpStatus.MOVED_PERMANENTLY);

        // Act
        restTemplateUtils.handleRedirect(sourceUrl, response, String.class, new HttpEntity<>(null));

        // Assert
        verify(restTemplate, times(1))
                .exchange(eq(redirectUrl.replace("%20", " ")), eq(HttpMethod.GET), any(), eq(String.class), anyMap());
    }
}
