package au.org.aodn.ogcapi.server.common;

import au.org.aodn.ogcapi.features.model.FeatureCollectionGeoJSON;
import au.org.aodn.ogcapi.features.model.FeatureGeoJSON;
import au.org.aodn.ogcapi.server.core.configuration.DASConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

@Service("DataAccessService")
public class DasService {

    @Autowired
    protected DASConfig dasConfig;

    @Autowired
    protected RestTemplate httpClient;

    public FeatureCollectionGeoJSON getWaveBuoys(String from, String to){
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set("X-API-KEY", dasConfig.secret);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        String waveBuoysUrlTemplate = UriComponentsBuilder.fromUriString(dasConfig.host + "/api/v1/das/data/feature-collection/wave-buoy")
                .queryParam("start_date","{start_date}")
                .queryParam("end_date","{end_date}")
                .encode()
                .toUriString();
        Map<String,String> params = new HashMap<>();
        params.put("start_date", from);
        params.put("end_date",to);

        return httpClient.exchange(waveBuoysUrlTemplate, HttpMethod.GET,entity,FeatureCollectionGeoJSON.class,params).getBody();
    }

    public FeatureGeoJSON getWaveBuoyData(String from, String to, String buoy){
        String encodedBuoy = URLEncoder.encode(buoy, java.nio.charset.StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set("X-API-KEY", dasConfig.secret);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        String waveBuoyDataUrlTemplate = UriComponentsBuilder.fromUriString(dasConfig.host + "/api/v1/das/data/feature-collection/wave-buoy/" + encodedBuoy)
                .queryParam("start_date","{start_date}")
                .queryParam("end_date","{end_date}")
                .encode()
                .toUriString();
        Map<String,String> params = new HashMap<>();
        params.put("start_date", from);
        params.put("end_date",to);

        return httpClient.exchange(waveBuoyDataUrlTemplate, HttpMethod.GET,entity,FeatureGeoJSON.class,params).getBody();
    }

    public boolean isCollectionSupported(String collectionId){
        final String waveBuoyRealtimeCollectionID = "b299cdcd-3dee-48aa-abdd-e0fcdbb9cadc";
        return waveBuoyRealtimeCollectionID.contentEquals(collectionId);
    }


}
