package au.org.aodn.ogcapi.server.core.service;

import au.org.aodn.ogcapi.server.core.configuration.DASConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("DataAccessService")
public class DasService {

    @Autowired
    protected DASConfig dasConfig;

    @Autowired
    protected RestTemplate httpClient;

    private HttpEntity<?> httpEntity;

    @PostConstruct
    public void init() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set("X-API-KEY", dasConfig.secret);
        httpEntity = new HttpEntity<>(headers);
    }

    public byte[] getWaveBuoys(String from, String to){
        String waveBuoysUrlTemplate = UriComponentsBuilder.fromUriString(dasConfig.host + "/api/v1/das/data/feature-collection/wave-buoy")
                .queryParam("start_date","{start_date}")
                .queryParam("end_date","{end_date}")
                .encode()
                .toUriString();
        Map<String,String> params = new HashMap<>();
        params.put("start_date", from);
        params.put("end_date",to);

        return httpClient.exchange(waveBuoysUrlTemplate, HttpMethod.GET,httpEntity,byte[].class,params).getBody();
    }

    public byte[] getWaveBuoysLatestDate(){
        String waveBuoysUrlTemplate = UriComponentsBuilder.fromUriString(dasConfig.host + "/api/v1/das/data/feature-collection/wave-buoy/latest")
                .encode()
                .toUriString();

        return httpClient.exchange(waveBuoysUrlTemplate, HttpMethod.GET,httpEntity,byte[].class).getBody();
    }

    public byte[] getWaveBuoyData(String from, String to, String buoy){
        String encodedBuoy = URLEncoder.encode(buoy, java.nio.charset.StandardCharsets.UTF_8);

        String waveBuoyDataUrlTemplate = UriComponentsBuilder.fromUriString(dasConfig.host + "/api/v1/das/data/feature-collection/wave-buoy/" + encodedBuoy)
                .queryParam("start_date","{start_date}")
                .queryParam("end_date","{end_date}")
                .encode()
                .toUriString();
        Map<String,String> params = new HashMap<>();
        params.put("start_date", from);
        params.put("end_date",to);

        return httpClient.exchange(waveBuoyDataUrlTemplate, HttpMethod.GET,httpEntity,byte[].class,params).getBody();
    }

    public byte[] getLatestWaveBuoySites(){
        String waveBuoysUrlTemplate = UriComponentsBuilder.fromUriString(dasConfig.host + "/api/v1/das/data/feature-collection/wave-buoy/all")
                .encode()
                .toUriString();

        return httpClient.exchange(waveBuoysUrlTemplate, HttpMethod.GET,httpEntity,byte[].class).getBody();
    }

    /**
     * Call the data-access-service cloud-optimised size estimate endpoint.
     *
     * POST /api/v1/das/data/{uuid}/estimate_size with a JSON body matching
     * EstimateSizeRequest. The endpoint is multi-key and aggregates server-side:
     * a null/"*" keys list means "all keys of the uuid" (same as the batch
     * download). Returns the raw JSON response body so the SSE layer can forward
     * it to the frontend unchanged.
     */
    public String estimateCloudOptimisedDownloadSize(
            String uuid,
            List<String> keys,
            String startDate,
            String endDate,
            Object multiPolygon,
            List<String> columns,
            String outputFormat) {

        String url = UriComponentsBuilder.fromUriString(dasConfig.host + "/api/v1/das/data/{uuid}/estimate_size")
                .encode()
                .toUriString();

        // Body mirrors EstimateSizeRequest. Send the raw frontend date strings
        // (or "non-specified" when null) so data-access-service applies the same
        // resolve/supply/trim chain the batch download uses.
        Map<String, Object> body = new HashMap<>();
        body.put("keys", keys); // null => all keys of the uuid
        body.put("start_date", startDate != null ? startDate : "non-specified");
        body.put("end_date", endDate != null ? endDate : "non-specified");
        body.put("output_format", outputFormat);
        // multi_polygon is accepted as a GeoJSON object or string; forward as-is.
        if (multiPolygon != null) {
            body.put("multi_polygon", multiPolygon);
        }
        // columns is not sent today (frontend doesn't subset columns yet, and the
        // batch download grabs all variables), keeping the estimate aligned.
        if (columns != null) {
            body.put("columns", columns);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.set("X-API-KEY", dasConfig.secret);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Map<String, String> uriVars = new HashMap<>();
        uriVars.put("uuid", uuid);

        return httpClient.exchange(url, HttpMethod.POST, entity, String.class, uriVars).getBody();
    }

    public boolean isCollectionSupported(String collectionId){
        final String waveBuoyRealtimeCollectionID = "b299cdcd-3dee-48aa-abdd-e0fcdbb9cadc";
        return waveBuoyRealtimeCollectionID.contentEquals(collectionId);
    }


}
