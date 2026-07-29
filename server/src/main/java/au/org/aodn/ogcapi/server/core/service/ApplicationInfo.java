package au.org.aodn.ogcapi.server.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

public interface ApplicationInfo {
    @Slf4j
    class LogHolder {
        // Lombok injects the logger here inside the static inner class
    }
    /**
     * Query this is repeat code to query the info path
     * @param restTemplate - The template of the service
     * @param host - Host
     * @param path - query path
     * @return - Map of value
     */
    default Map<String, Map<?,?>> queryInfo(RestTemplate restTemplate, String host, String path) {
        if(path != null) {
            try {
                String das = String.format("%s/%s", host, path);

                LogHolder.log.info("Query service info for {}, {}", host, path);
                ResponseEntity<Map<String, Map<?, ?>>> response = restTemplate.exchange(
                        das,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {
                        }
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    return response.getBody();
                }
            } catch (Exception e) {
                // Do not throw exception that impacts start, UI will use health endpoint to decide level of
                // service, for example if geonetwork is not work, we can still provide service
            }
        }
        return Collections.emptyMap();
    }

    String getName();
    String getVersion();
    String getDescription();
}
