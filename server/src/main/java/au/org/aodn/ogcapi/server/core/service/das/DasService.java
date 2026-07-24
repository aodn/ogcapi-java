package au.org.aodn.ogcapi.server.core.service.das;

import au.org.aodn.ogcapi.server.core.configuration.Config;
import au.org.aodn.ogcapi.server.core.configuration.DasProperties;
import au.org.aodn.ogcapi.server.core.model.DatasetMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("DataAccessService")
public class DasService {

    protected final DasProperties dasProperties;

    protected final RestTemplate httpClient;

    public DasService(
            DasProperties dasProperties,
            @Qualifier(Config.DAS_REST_TEMPLATE) RestTemplate httpClient) {
        this.dasProperties = dasProperties;
        this.httpClient = httpClient;
    }

    /**
     * GET a feature-collection from the DAS, optionally bounded by start/end date. Only the date
     * query params that are non-null are added, so a null value is never passed to URI template
     * expansion (which would throw). Any path variables in {@code path} are supplied via
     * {@code pathVariables}.
     */
    private byte[] getFeatureCollection(String path, String start, String end, Map<String, String> pathVariables) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(dasProperties.host() + path);
        Map<String, String> params = new HashMap<>(pathVariables);

        if (start != null) {
            builder.queryParam("start_date", "{start_date}");
            params.put("start_date", start);
        }
        if (end != null) {
            builder.queryParam("end_date", "{end_date}");
            params.put("end_date", end);
        }

        String url = builder.encode().toUriString();
        return httpClient.getForObject(url, byte[].class, params);
    }

    public byte[] getWaveBuoysBetweenDates(String start, String end) {
        return getFeatureCollection("/api/v1/das/data/feature-collection/wave-buoy", start, end, Map.of());
    }

    public byte[] getWaveBuoysLatestAvailableDate() {
        String waveBuoysUrlTemplate = UriComponentsBuilder.fromUriString(dasProperties.host() + "/api/v1/das/data/feature-collection/wave-buoy/latest")
                .encode()
                .toUriString();

        return httpClient.getForObject(waveBuoysUrlTemplate, byte[].class);
    }

    public byte[] getWaveBuoyDetailsBetweenDates(String startDateTime, String endDateTime, String buoy) {
        return getFeatureCollection("/api/v1/das/data/feature-collection/wave-buoy/{buoy}", startDateTime, endDateTime, Map.of("buoy", buoy));
    }

    public byte[] getMooringsBetweenDates(String start, String end) {
        return getFeatureCollection("/api/v1/das/data/feature-collection/mooring", start, end, Map.of());
    }

    public byte[] getMooringsLatestAvailableDate() {
        String mooringsUrlTemplate = UriComponentsBuilder.fromUriString(dasProperties.host() + "/api/v1/das/data/feature-collection/mooring/latest")
                .encode()
                .toUriString();

        return httpClient.getForObject(mooringsUrlTemplate, byte[].class);
    }

    public byte[] getMooringDetailsBetweenDates(String startDateTime, String endDateTime, String mooring) {
        return getFeatureCollection("/api/v1/das/data/feature-collection/mooring/{mooring}", startDateTime, endDateTime, Map.of("mooring", mooring));
    }

    /**
     * Call the data-access-service cloud-optimised size estimate endpoint.
     * The {@code parameters} map is the same batch-style subset request the
     * download job submits (see {@code SubsetParametersUtils}), so DAS interprets
     * the estimate and the download identically. Returns the raw JSON response
     * body so the SSE layer can forward it to the frontend unchanged.
     */
    public String estimateCloudOptimisedDownloadSize(String uuid, Map<String, String> parameters) {

        String url = UriComponentsBuilder.fromUriString(dasProperties.host() + "/api/v1/das/data/{uuid}/estimate_size")
                .encode()
                .toUriString();

        Map<String, String> uriVars = new HashMap<>();
        uriVars.put("uuid", uuid);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        return httpClient.postForObject(url, new HttpEntity<>(parameters, headers), String.class, uriVars);
    }

    public ResponseEntity<DatasetMetadata> getDatasetMetadata(String datasetId) {
        ResponseEntity<DatasetMetadata> response = httpClient.getForEntity(
                dasProperties.host() + "/api/v1/das/metadata/" + datasetId,
                DatasetMetadata.class
        );
        // We need to do this so that the response is closed
        return ResponseEntity
                .status(response.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());
    }
}
