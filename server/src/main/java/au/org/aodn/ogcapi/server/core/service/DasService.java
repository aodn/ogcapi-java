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

    public boolean isCollectionSupported(String collectionId){
        final String waveBuoyRealtimeCollectionID = "b299cdcd-3dee-48aa-abdd-e0fcdbb9cadc";
        return waveBuoyRealtimeCollectionID.contentEquals(collectionId);
    }


}
